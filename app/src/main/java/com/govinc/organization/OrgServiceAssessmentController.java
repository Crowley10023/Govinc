package com.govinc.organization;

import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.service.EmailService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/orgservice-assessment")
public class OrgServiceAssessmentController {
    private final OrgServiceAssessmentService assessmentService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private OrgServiceRepository orgServiceRepository;

    private final OrgServiceAssessmentPropagationService propagationService;

    @Autowired
    public OrgServiceAssessmentController(OrgServiceAssessmentService assessmentService,
                                          OrgServiceAssessmentPropagationService propagationService) {
        this.assessmentService = assessmentService;
        this.propagationService = propagationService;
    }

    /** Returns true if the current user is a responsible person on this org service. */
    private boolean isResponsiblePerson(Long orgServiceId) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) return false;
        return orgServiceRepository.findById(orgServiceId)
                .map(svc -> svc.getResponsiblePersons() != null &&
                        svc.getResponsiblePersons().stream().anyMatch(u -> u.getId().equals(currentUser.getId())))
                .orElse(false);
    }

    private boolean canAccessAssessmentFor(Long orgServiceId) {
        return authorizationService.canAccessOrganization() || isResponsiblePerson(orgServiceId);
    }

    @Autowired
    private OrgServiceAssessmentRepository orgServiceAssessmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OpenAIUtil openAIUtil;

    @GetMapping("/list")
    public String listAssessments(Model model) {
        if (!authorizationService.canAccessOrganization()) {
            throw new UnauthorizedException("You do not have permission to view org assessments.");
        }
        List<OrgServiceAssessment> assessments = orgServiceAssessmentRepository.findAll();
        model.addAttribute("assessments", assessments);

        // Compute completeness per assessment: % of applicable controls with a non-zero maturity answer
        Map<Long, Integer> completenessMap = new HashMap<>();
        for (OrgServiceAssessment osa : assessments) {
            long applicable = 0;
            long answered = 0;
            if (osa.getControls() != null) {
                for (OrgServiceAssessmentControl c : osa.getControls()) {
                    if (c.isApplicable()) {
                        applicable++;
                        if (c.getPercent() > 0) answered++;
                    }
                }
            }
            int pct = applicable > 0 ? (int) Math.round((answered * 100.0) / applicable) : 0;
            completenessMap.put(osa.getId(), pct);
        }
        model.addAttribute("completenessMap", completenessMap);

        return "orgservice-assessment-list";
    }

    @GetMapping("/edit/{orgServiceId}")
    public String editAssessment(@PathVariable Long orgServiceId,
                                 @RequestParam(required = false) Integer version, Model model) {
        if (!authorizationService.canAccessOrganization()) {
            throw new UnauthorizedException("You do not have permission to access org service assessments.");
        }
        OrgServiceAssessment assessment = assessmentService.findOrCreateAssessment(orgServiceId);
        int viewVersion = addVersionAttributes(model, assessment, version);
        List<OrgServiceAssessmentControl> controls = assessmentService.getControlsForVersion(assessment, viewVersion);
        long applicableCount = controls.stream().filter(OrgServiceAssessmentControl::isApplicable).count();
        model.addAttribute("assessment", assessment);
        model.addAttribute("controls", controls);
        model.addAttribute("applicableCount", applicableCount);
        return "orgservice-assessment";
    }

    /** Adds version selector data and the previous version's values; returns the version being viewed. */
    private int addVersionAttributes(Model model, OrgServiceAssessment assessment, Integer requested) {
        int current = assessment.getVersionNumber();
        int viewVersion = (requested == null || requested < 1 || requested > current) ? current : requested;
        model.addAttribute("currentVersion", current);
        model.addAttribute("viewVersion", viewVersion);
        model.addAttribute("readOnly", viewVersion != current);
        model.addAttribute("versions", assessmentService.listVersions(assessment));
        model.addAttribute("prevMap", assessmentService.getPreviousValues(assessment, viewVersion));
        model.addAttribute("prevVersion", viewVersion - 1);
        try {
            model.addAttribute("historyJson", new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(assessmentService.getHistory(assessment, viewVersion, 5)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            model.addAttribute("historyJson", "{}");
        }
        return viewVersion;
    }

    @GetMapping("/simple/{orgServiceId}")
    public String simpleView(@PathVariable Long orgServiceId,
                             @RequestParam(required = false) Integer version, Model model) {
        if (!canAccessAssessmentFor(orgServiceId)) {
            throw new UnauthorizedException("You do not have permission to access this org service assessment.");
        }
        OrgServiceAssessment assessment = assessmentService.findOrCreateAssessment(orgServiceId);
        int viewVersion = addVersionAttributes(model, assessment, version);
        List<OrgServiceAssessmentControl> applicableControls = assessmentService.getControlsForVersion(assessment, viewVersion).stream()
                .filter(OrgServiceAssessmentControl::isApplicable)
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("assessment", assessment);
        model.addAttribute("controls", applicableControls);
        return "orgservice-assessment-simple";
    }

    @PostMapping("/{orgServiceId}/new-version")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> makeNewVersion(@PathVariable Long orgServiceId) {
        Map<String, Object> response = new HashMap<>();
        if (!canAccessAssessmentFor(orgServiceId)) {
            response.put("success", false);
            response.put("message", "You do not have permission to modify org service assessments.");
            return ResponseEntity.status(403).body(response);
        }
        User user = authorizationService.getCurrentUser();
        int newVersion = assessmentService.makeNewVersion(orgServiceId, user != null ? user.getName() : null);
        response.put("success", true);
        response.put("version", newVersion);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/simple/{orgServiceId}/export")
    public ResponseEntity<byte[]> exportSimpleView(@PathVariable Long orgServiceId,
                                                   @RequestParam(required = false) Integer version) throws java.io.IOException {
        if (!canAccessAssessmentFor(orgServiceId)) {
            throw new UnauthorizedException("You do not have permission to access this org service assessment.");
        }
        OrgServiceAssessment assessment = assessmentService.findOrCreateAssessment(orgServiceId);
        int current = assessment.getVersionNumber();
        int viewVersion = (version == null || version < 1 || version > current) ? current : version;
        List<OrgServiceAssessmentControl> controls = assessmentService.getControlsForVersion(assessment, viewVersion).stream()
                .filter(OrgServiceAssessmentControl::isApplicable).collect(Collectors.toList());
        Map<Long, OrgServiceAssessmentVersionControl> prev = assessmentService.getPreviousValues(assessment, viewVersion);

        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Assessment v" + viewVersion);
            org.apache.poi.ss.usermodel.CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            org.apache.poi.ss.usermodel.CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

            String[] titles = {"Security Control", "Detail", "Reference", "Rating (%)", "Comment",
                    "Previous Rating (%) (v" + (viewVersion - 1) + ")", "Previous Comment (v" + (viewVersion - 1) + ")"};
            org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
            for (int i = 0; i < titles.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = head.createCell(i);
                cell.setCellValue(titles[i]);
                cell.setCellStyle(header);
            }
            int r = 1;
            for (OrgServiceAssessmentControl c : controls) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                OrgServiceAssessmentVersionControl p = prev.get(c.getSecurityControl().getId());
                row.createCell(0).setCellValue(nz(c.getSecurityControl().getName()));
                row.createCell(1).setCellValue(nz(c.getSecurityControl().getDetail()));
                row.createCell(2).setCellValue(nz(c.getSecurityControl().getReference()));
                row.createCell(3).setCellValue(c.getPercent());
                row.createCell(4).setCellValue(nz(c.getComment()));
                if (p != null) {
                    row.createCell(5).setCellValue(p.getPercent());
                    row.createCell(6).setCellValue(nz(p.getComment()));
                }
                for (int i = 0; i < titles.length; i++) {
                    if (row.getCell(i) != null) row.getCell(i).setCellStyle(wrap);
                }
            }
            int[] widths = {40, 60, 25, 12, 60, 18, 60};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(0, 1);
            wb.write(out);

            String name = assessment.getOrgService().getName() == null ? "service"
                    : assessment.getOrgService().getName().replaceAll("[^A-Za-z0-9._-]+", "_");
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"assessment_" + name + "_v" + viewVersion + ".xlsx\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @PostMapping("/save-control")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveControl(@RequestParam Long id,
                                                            @RequestParam Long orgServiceId,
                                                            @RequestParam String assessmentDate,
                                                            @RequestParam Long controlId,
                                                            @RequestParam Boolean applicable,
                                                            @RequestParam Integer percent) {
        if (!canAccessAssessmentFor(orgServiceId)) {
            Map<String, Object> forbidden = new HashMap<>();
            forbidden.put("success", false);
            forbidden.put("message", "You do not have permission to modify org service assessments.");
            return ResponseEntity.status(403).body(forbidden);
        }
        Map<String, Object> response = new HashMap<>();
        try {
            OrgServiceAssessment assessment = assessmentService.getAssessment(id)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));
            
            // Update assessment date if needed
            if (assessmentDate != null && !assessmentDate.isEmpty()) {
                assessment.setAssessmentDate(java.time.LocalDate.parse(assessmentDate));
            }
            
            // Find and update the specific control
            List<OrgServiceAssessmentControl> controls = assessment.getControls();
            OrgServiceAssessmentControl controlToUpdate = controls.stream()
                    .filter(c -> c.getSecurityControl().getId().equals(controlId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Control not found"));
            
            // Check if the control is locked (answered by another assessment)
            if (controlToUpdate.isAnsweredByAnotherAssessment()) {
                response.put("success", false);
                response.put("message", "This control is locked by another assessment");
                return ResponseEntity.status(400).body(response);
            }
            
            // Update control values
            controlToUpdate.setApplicable(applicable);
            controlToUpdate.setPercent(Math.min(100, Math.max(0, percent)));
            
            assessmentService.saveAssessment(assessment);
            try {
                // Propagation is best-effort and must never break the primary save flow.
                propagationService.propagateControlChange(orgServiceId, controlId);
            } catch (Exception propagationEx) {
                response.put("warning", "Control saved, but propagation failed: " + propagationEx.getMessage());
            }

            response.put("success", true);
            response.put("message", "Control saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            response.put("success", false);
            response.put("message", ex.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/save-control-comment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveControlComment(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long id = Long.parseLong(body.get("id").toString());
            Long controlId = Long.parseLong(body.get("controlId").toString());
            String comment = (String) body.get("comment");
            
            OrgServiceAssessment assessment = assessmentService.getAssessment(id)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));

            Long svcId = assessment.getOrgService().getId();
            if (!canAccessAssessmentFor(svcId)) {
                response.put("success", false);
                response.put("message", "You do not have permission to modify org service assessments.");
                return ResponseEntity.status(403).body(response);
            }
            
            // Find and update the specific control
            List<OrgServiceAssessmentControl> controls = assessment.getControls();
            OrgServiceAssessmentControl controlToUpdate = controls.stream()
                    .filter(c -> c.getSecurityControl().getId().equals(controlId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Control not found"));
            
            // Check if the control is locked (answered by another assessment)
            if (controlToUpdate.isAnsweredByAnotherAssessment()) {
                response.put("success", false);
                response.put("message", "This control is locked by another assessment");
                return ResponseEntity.status(400).body(response);
            }
            
            // Update comment
            controlToUpdate.setComment(comment != null ? comment : "");
            
            assessmentService.saveAssessment(assessment);
            try {
                // Propagation is best-effort and must never break the primary save flow.
                propagationService.propagateCommentChange(svcId, controlId);
            } catch (Exception propagationEx) {
                response.put("warning", "Comment saved, but propagation failed: " + propagationEx.getMessage());
            }

            response.put("success", true);
            response.put("message", "Comment saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            response.put("success", false);
            response.put("message", ex.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/{orgServiceId}/email/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateEmail(
            @PathVariable Long orgServiceId,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> response = new HashMap<>();

        if (!canAccessAssessmentFor(orgServiceId)) {
            response.put("error", "You do not have permission.");
            return ResponseEntity.status(403).body(response);
        }

        OrgService orgService = orgServiceRepository.findById(orgServiceId).orElse(null);
        if (orgService == null) {
            response.put("error", "Org service not found.");
            return ResponseEntity.status(404).body(response);
        }

        String baseUrl = payload.containsKey("baseUrl") ? String.valueOf(payload.get("baseUrl")) : "";
        if ("null".equals(baseUrl)) baseUrl = "";

        String simpleLink = baseUrl.isBlank()
                ? "/orgservice-assessment/simple/" + orgServiceId
                : baseUrl.stripTrailing() + "/orgservice-assessment/simple/" + orgServiceId;
        String htmlLink = "<a href=\"" + simpleLink + "\">Complete your part of the assessment</a>";

        User currentUser = authorizationService.getCurrentUser();
        String senderName = currentUser != null && currentUser.getName() != null ? currentUser.getName() : "";
        String orgServiceName = orgService.getName() != null ? orgService.getName() : "Org Service #" + orgServiceId;

        String prompt = "Write a professional and motivating e-mail inviting the recipient to complete a security assessment for the org service/application they are responsible for.\n" +
                (senderName.isBlank() ? "" : "Sender name: " + senderName + "\n") +
                "Org service name: " + orgServiceName + "\n\n" +
                "The body must be valid HTML (use <p>, <br>, etc.). Include the assessment link exactly as this HTML anchor: " + htmlLink + "\n" +
                "The e-mail should:\n" +
                "- Explain the purpose of the assessment briefly\n" +
                "- Ask the recipient to complete their part via the provided link\n" +
                "- Mention the importance of timely completion\n" +
                "- Be polite and professional\n\n" +
                "Return ONLY a JSON object with two keys: \"subject\" and \"body\". No markdown, no code fences.";

        String aiResponse = openAIUtil.askAI(prompt, false);

        try {
            String cleaned = aiResponse.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n') + 1;
                int end = cleaned.lastIndexOf("```");
                if (end > start) cleaned = cleaned.substring(start, end).trim();
            }
            org.json.JSONObject json = new org.json.JSONObject(cleaned);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("subject", json.optString("subject", "Security Assessment: Action Required"));
            result.put("body", json.optString("body", aiResponse));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("subject", "Action Required: " + orgServiceName);
            result.put("body", aiResponse);
            return ResponseEntity.ok(result);
        }
    }

    @PostMapping("/{orgServiceId}/email/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendEmail(
            @PathVariable Long orgServiceId,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> response = new HashMap<>();

        if (!canAccessAssessmentFor(orgServiceId)) {
            response.put("error", "You do not have permission.");
            return ResponseEntity.status(403).body(response);
        }

        OrgService orgService = orgServiceRepository.findById(orgServiceId).orElse(null);
        if (orgService == null) {
            response.put("error", "Org service not found.");
            return ResponseEntity.status(404).body(response);
        }

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null || currentUser.getEmail().isBlank()) {
            response.put("error", "Your account does not have a valid e-mail address configured as sender.");
            return ResponseEntity.badRequest().body(response);
        }
        String from = currentUser.getEmail();

        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) payload.get("recipientUserIds");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> corpDirRecipients =
                (List<Map<String, String>>) payload.getOrDefault("corpDirRecipients", java.util.Collections.emptyList());

        boolean hasUserIds = rawIds != null && !rawIds.isEmpty();
        boolean hasCorpDir = corpDirRecipients != null && !corpDirRecipients.isEmpty();

        if (!hasUserIds && !hasCorpDir) {
            response.put("error", "No recipients selected.");
            return ResponseEntity.badRequest().body(response);
        }

        String subject = String.valueOf(payload.getOrDefault("subject", "")).trim();
        String body = String.valueOf(payload.getOrDefault("body", "")).trim();

        if (subject.isBlank()) {
            response.put("error", "Subject must not be empty.");
            return ResponseEntity.badRequest().body(response);
        }
        if (body.isBlank()) {
            response.put("error", "E-mail body must not be empty.");
            return ResponseEntity.badRequest().body(response);
        }

        if (orgService.getResponsiblePersons() == null) {
            orgService.setResponsiblePersons(new java.util.HashSet<>());
        }

        java.util.List<String> recipientEmails = new java.util.ArrayList<>();
        boolean serviceChanged = false;

        if (hasUserIds) {
            List<Long> userIds = rawIds.stream()
                    .map(o -> o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o)))
                    .collect(Collectors.toList());
            for (Long uid : userIds) {
                Optional<User> userOpt = userRepository.findById(uid);
                if (userOpt.isEmpty()) continue;
                User u = userOpt.get();
                if (orgService.getResponsiblePersons().stream().noneMatch(rp -> rp.getId().equals(u.getId()))) {
                    orgService.getResponsiblePersons().add(u);
                    serviceChanged = true;
                }
                if (u.getEmail() != null && !u.getEmail().isBlank()
                        && !recipientEmails.contains(u.getEmail())) {
                    recipientEmails.add(u.getEmail());
                }
            }
        }

        if (hasCorpDir) {
            for (Map<String, String> cdUser : corpDirRecipients) {
                String emailRaw = cdUser.getOrDefault("mail", "").trim();
                if (emailRaw.isBlank()) emailRaw = cdUser.getOrDefault("userPrincipalName", "").trim();
                if (emailRaw.isBlank() || !emailRaw.contains("@")) continue;
                final String cdEmail = emailRaw.toLowerCase();

                String gn = cdUser.getOrDefault("givenName", "").trim();
                String sn = cdUser.getOrDefault("surname", "").trim();
                if (gn.isBlank() && sn.isBlank()) {
                    String display = cdUser.getOrDefault("displayName", "").trim();
                    int sp = display.lastIndexOf(' ');
                    if (sp > 0) { gn = display.substring(0, sp); sn = display.substring(sp + 1); }
                    else { gn = display; }
                }
                final String givenName = gn;
                final String surname = sn;

                User cdDbUser = userRepository.findByEmail(cdEmail).orElseGet(() -> {
                    User nu = new User(givenName, surname, cdEmail);
                    nu.setRole(com.govinc.user.Role.ASSESSOR);
                    return userRepository.save(nu);
                });

                if (orgService.getResponsiblePersons().stream().noneMatch(rp -> rp.getId().equals(cdDbUser.getId()))) {
                    orgService.getResponsiblePersons().add(cdDbUser);
                    serviceChanged = true;
                }
                if (cdDbUser.getEmail() != null && !cdDbUser.getEmail().isBlank()
                        && !recipientEmails.contains(cdDbUser.getEmail())) {
                    recipientEmails.add(cdDbUser.getEmail());
                }
            }
        }

        if (serviceChanged) {
            orgServiceRepository.save(orgService);
        }

        if (recipientEmails.isEmpty()) {
            response.put("error", "None of the selected users have a valid e-mail address.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            emailService.sendEmail(from, recipientEmails, subject, body);
            response.put("success", true);
            response.put("message", "E-mail sent to " + recipientEmails.size() + " recipient(s).");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to send e-mail: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
