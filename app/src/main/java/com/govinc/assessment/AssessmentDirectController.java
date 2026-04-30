package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Objects;
import java.time.LocalDate;
import java.util.stream.Collectors;

import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.assessment.Assessment;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlDomain;
import com.govinc.maturity.MaturityModel;

@Controller
public class AssessmentDirectController {
    @Autowired
    private AssessmentUrlsService assessmentUrlsService;
    @Autowired
    private AssessmentDetailsService detailsService;
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository; // Added for maturity answers
    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private AssessmentDetailsService assessmentDetailsService;
    @Autowired
    private AnsweringGuideService answeringGuideService;

    // Replaced Thymeleaf mapping with RESTful endpoints

    // New JSON endpoint: Get all assessment data needed for the direct page (formerly Thymeleaf model)
    @GetMapping("/assessment-direct/{obfuscatedId}/alldata")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> getAssessmentDirectAllData(@PathVariable String obfuscatedId) {
        Optional<AssessmentUrls> maybeUrl = assessmentUrlsService.findByObfuscated(obfuscatedId);
        if (maybeUrl.isPresent()) {
            AssessmentUrls urlEntity = maybeUrl.get();
            Assessment assessment = urlEntity.getAssessment();
            Map<String, Object> out = new HashMap<>();

            Map<String, Object> assessmentMap = new HashMap<>();
            assessmentMap.put("id", assessment.getId());
            assessmentMap.put("creationDate", assessment.getCreationDate());
            assessmentMap.put("closeDate", assessment.getCloseDate());
            assessmentMap.put("status", assessment.getStatus());
            assessmentMap.put("name", assessment.getName());
            assessmentMap.put("orgUnit", assessment.getOrgUnit() != null ? assessment.getOrgUnit().getName() : "-");
            assessmentMap.put("createdBy", assessment.getCreatedBy() != null ? (assessment.getCreatedBy().getName() + " (" + assessment.getCreatedBy().getEmail() + ")") : null);
            out.put("assessment", assessmentMap);
            out.put("guideVisibleInDirect", assessment.isGuideVisibleInDirect());
            out.put("securityCatalogId", assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getId() : null);
            out.put("catalogHeadline", assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getHeadline() : null);

            // Controls, sorted (uses frozen snapshot for closed assessments)
            List<SecurityControl> controls = new ArrayList<>(assessment.getEffectiveControls());
            controls.sort(Comparator.comparing(SecurityControl::getName, Comparator.nullsLast(String::compareTo)));
            List<Map<String, Object>> controlsList = controls.stream().map(ctrl -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", ctrl.getId());
                m.put("name", ctrl.getName());
                m.put("detail", ctrl.getDetail());
                m.put("domainId", ctrl.getSecurityControlDomain() != null ? ctrl.getSecurityControlDomain().getId() : null);
                return m;
            }).collect(Collectors.toList());
            out.put("controls", controlsList);

            // Control Domains, sorted
            List<SecurityControlDomain> securityControlDomains = controls.stream()
                .map(SecurityControl::getSecurityControlDomain)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(SecurityControlDomain::getName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
            List<Map<String, Object>> domainsList = securityControlDomains.stream().map(domain -> {
                Map<String, Object> dm = new HashMap<>();
                dm.put("id", domain.getId());
                dm.put("name", domain.getName());
                dm.put("description", domain.getDescription());
                return dm;
            }).collect(Collectors.toList());
            out.put("securityControlDomains", domainsList);

            // Pass sorted maturity answers from the associated maturity model only
            List<MaturityAnswer> maturityAnswers = new ArrayList<>();
            if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getMaturityModel() != null) {
                maturityAnswers.addAll(assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers());
                maturityAnswers.sort(Comparator.comparing(MaturityAnswer::getAnswer, Comparator.nullsLast(String::compareTo)));
            }
            List<Map<String, Object>> maturityList = maturityAnswers.stream().map(ans -> {
                Map<String, Object> am = new HashMap<>();
                am.put("id", ans.getId());
                am.put("answer", ans.getAnswer());
                return am;
            }).collect(Collectors.toList());
            out.put("maturityAnswers", maturityList);

            // Control Answers (ctrlId -> answer text if answered)
            Optional<AssessmentDetails> detailsOpt = detailsService.findById(assessment.getId());
            AssessmentDetails details = detailsOpt.orElse(null);
            Map<Long, String> controlAnswers = new HashMap<>();
            Map<Long, String> controlComments = new HashMap<>();
            if (details != null && details.getControlAnswers() != null) {
                for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                    if (aca.getSecurityControl() != null) {
                        if (aca.getMaturityAnswer() != null)
                            controlAnswers.put(aca.getSecurityControl().getId(), aca.getMaturityAnswer().getAnswer());
                        if (aca.getComment() != null)
                            controlComments.put(aca.getSecurityControl().getId(), aca.getComment());
                    }
                }
            }
            out.put("controlAnswers", controlAnswers);
            out.put("controlComments", controlComments);

            // answerSummary filtered to maturity answers of this assessment's catalog model
            Set<Long> catalogMaturityAnswerIds = maturityAnswers.stream()
                .map(MaturityAnswer::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Object summary = detailsService.computeAnswerSummary(details, catalogMaturityAnswerIds);
            out.put("answerSummary", summary);

            // Assessment is open only if status is OPEN
            out.put("isOpen", AssessmentStatus.OPEN.equals(assessment.getStatus()));

            return org.springframework.http.ResponseEntity.ok(out);
        } else {
            return org.springframework.http.ResponseEntity.status(404).body(Map.of("error", "Not found"));
        }
    }

    // Deleted (replaced) Thymeleaf endpoint, but keep as fallback for old routes:
    @Deprecated
    @GetMapping("/assessment-direct/{obfuscatedId}")
    public String showAssessmentDirect(@PathVariable String obfuscatedId, Model model) {
        return "assessment-direct"; // fallback, all data fetched via API from now
    }

    // Allow using /assessment-direct.html?id=...
    @GetMapping("/assessment-direct.html")
    public String showAssessmentDirectByParam(@RequestParam("id") String obfuscatedId, Model model) {
        return showAssessmentDirect(obfuscatedId, model);
    }

    // Save/update answer for a single control (AJAX POST from assessment-direct UI)
    @org.springframework.web.bind.annotation.PostMapping("/assessment-direct/{id}/answer")
    @org.springframework.web.bind.annotation.ResponseBody
    public String saveDirectAnswer(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestParam Long controlId, @org.springframework.web.bind.annotation.RequestParam Long answerId) {
        System.out.println("[DEBUG] Called /assessment-direct/" + id + "/answer with controlId=" + controlId + " answerId=" + answerId);
        Optional<AssessmentDetails> detailsOpt = detailsService.findById(id);
        AssessmentDetails details = null;
        if (!detailsOpt.isPresent()) {
            System.out.println("[DEBUG] AssessmentDetails not found for id=" + id);
            return "fail";
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = details.getControlAnswers();
        // Find or add
        AssessmentControlAnswer found = null;
        for (AssessmentControlAnswer aca : answers) {
            if (aca.getSecurityControl() != null && aca.getSecurityControl().getId().equals(controlId)) {
                found = aca;
                break;
            }
        }
        // For direct controller: might not have all beans, basic logic only
        SecurityControl control = null;
        com.govinc.catalog.SecurityControlRepository controlRepo = null;
        try {
            controlRepo = (com.govinc.catalog.SecurityControlRepository)org.springframework.web.context.support.WebApplicationContextUtils
                .getRequiredWebApplicationContext(((org.springframework.web.context.request.ServletRequestAttributes)org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest().getServletContext())
                .getBean(com.govinc.catalog.SecurityControlRepository.class);
            control = controlRepo.findById(controlId).orElse(null);
        } catch (Exception e) { System.out.println("[DEBUG] Exception initializing controlRepo: " + e); }
        MaturityAnswer maturityAnswer = maturityAnswerRepository.findById(answerId).orElse(null);
        if (control == null || maturityAnswer == null) {
            System.out.println("[DEBUG] control or maturityAnswer not found: control=" + control + " maturityAnswer=" + maturityAnswer);
            return "fail";
        }
        if (found == null) {
            found = new AssessmentControlAnswer(control, maturityAnswer);
            answers.add(found);
            System.out.println("[DEBUG] New AssessmentControlAnswer created for controlId=" + controlId + " with answer=" + maturityAnswer.getAnswer());
        } else {
            found.setMaturityAnswer(maturityAnswer);
            System.out.println("[DEBUG] Updated AssessmentControlAnswer for controlId=" + controlId + " to answer=" + maturityAnswer.getAnswer());
        }
        detailsService.save(details);
        System.out.println("[DEBUG] detailsService.save(details) called for assessmentId=" + id);
        return "ok";
    }

    // Save/update comment for a single control (AJAX PUT from direct UI)
    @org.springframework.web.bind.annotation.PutMapping("/assessment-direct/{id}/control/{controlId}/comment")
    @org.springframework.web.bind.annotation.ResponseBody
    public String saveDirectComment(@PathVariable Long id, @PathVariable Long controlId, @org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        String comment = body.get("comment");
        Optional<AssessmentDetails> detailsOpt = detailsService.findById(id);
        AssessmentDetails details = null;
        if (!detailsOpt.isPresent()) {
            return "fail";
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = details.getControlAnswers();
        AssessmentControlAnswer found = null;
        for (AssessmentControlAnswer aca : answers) {
            if (aca.getSecurityControl() != null && aca.getSecurityControl().getId().equals(controlId)) {
                found = aca;
                break;
            }
        }
        SecurityControl control = null;
        com.govinc.catalog.SecurityControlRepository controlRepo = null;
        try {
            controlRepo = (com.govinc.catalog.SecurityControlRepository)org.springframework.web.context.support.WebApplicationContextUtils
                .getRequiredWebApplicationContext(((org.springframework.web.context.request.ServletRequestAttributes)org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest().getServletContext())
                .getBean(com.govinc.catalog.SecurityControlRepository.class);
            control = controlRepo.findById(controlId).orElse(null);
        } catch (Exception e) { }
        if (control == null)
            return "fail";
        if (found == null) {
            // A comment with no answer yet
            found = new AssessmentControlAnswer(control, null, comment);
            answers.add(found);
        } else {
            found.setComment(comment);
        }
        detailsService.save(details);
        return "ok";
    }

    // Web page listing all Assessment URLs
    @GetMapping("/assessment-urls-list")
    public String showAllAssessmentUrls(Model model) {
        System.out.println("show assessment 3 ;-) : " + model);
        List<AssessmentUrls> allUrls = assessmentUrlsService.findAll();
        model.addAttribute("urls", allUrls);
        return "assessment-urls-list";
    }

    // Public summary JSON endpoint for assessment-direct
    @GetMapping("/assessment-direct/{obfuscatedId}/data")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> getAssessmentDirectSummary(@PathVariable String obfuscatedId) {
        Optional<AssessmentUrls> maybeUrl = assessmentUrlsService.findByObfuscated(obfuscatedId);
        if (maybeUrl.isPresent()) {
            Assessment assessment = maybeUrl.get().getAssessment();
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("id", assessment.getId());
            result.put("name", assessment.getName());
            result.put("creationDate", assessment.getCreationDate());
            result.put("closeDate", assessment.getCloseDate());
            result.put("status", assessment.getStatus());
            if (assessment.getOrgUnit() != null) {
                result.put("orgUnit", assessment.getOrgUnit().getName());
            } else {
                result.put("orgUnit", "-");
            }
            result.put("createdBy", assessment.getCreatedBy() != null ? assessment.getCreatedBy().getName() + " (" + assessment.getCreatedBy().getEmail() + ")" : null);
            return org.springframework.http.ResponseEntity.ok(result);
        } else {
            return org.springframework.http.ResponseEntity.status(404).body(java.util.Map.of("error", "Not found"));
        }
    }

    // Public AI Guide: generate guide questions (no authentication required)
    @PostMapping("/assessment-direct/guide/questions")
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, Object> generateDirectGuideQuestions(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {
        Object controlIdObj = request.get("controlId");
        Long controlId = controlIdObj instanceof Number
                ? ((Number) controlIdObj).longValue()
                : controlIdObj != null ? Long.parseLong(controlIdObj.toString()) : null;
        String controlName = (String) request.get("controlName");
        String controlDetail = (String) request.get("controlDetail");
        if (controlId == null || controlName == null || controlName.isBlank()) {
            return Map.of("success", false, "message", "controlId and controlName are required");
        }
        return answeringGuideService.getAnsweringGuide(controlId, controlName, controlDetail);
    }

    // Public AI Guide: propose maturity answer from guide answers (no authentication required)
    @PostMapping("/assessment-direct/guide/answer")
    @org.springframework.web.bind.annotation.ResponseBody
    @SuppressWarnings("unchecked")
    public Map<String, Object> proposeDirectGuideAnswer(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {
        Object controlIdObj = request.get("controlId");
        Long controlId = controlIdObj instanceof Number
                ? ((Number) controlIdObj).longValue()
                : controlIdObj != null ? Long.parseLong(controlIdObj.toString()) : null;
        Object catalogIdObj = request.get("securityCatalogId");
        Long securityCatalogId = catalogIdObj instanceof Number
                ? ((Number) catalogIdObj).longValue()
                : catalogIdObj != null ? Long.parseLong(catalogIdObj.toString()) : null;
        List<String> questions = (List<String>) request.get("questions");
        List<String> answers = (List<String>) request.get("answers");
        List<Map<String, Object>> maturityModelAnswers = (List<Map<String, Object>>) request.get("maturityModelAnswers");
        if (controlId == null) {
            return Map.of("success", false, "message", "controlId is required");
        }
        return answeringGuideService.proposeAnswerFromGuide(controlId, securityCatalogId, questions, answers, maturityModelAnswers);
    }

    // Public AI Guide: generate answer summary comment (no authentication required)
    @PostMapping("/assessment-direct/guide/summary")
    @org.springframework.web.bind.annotation.ResponseBody
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateDirectGuideSummary(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {
        String controlName = (String) request.get("controlName");
        List<String> questions = (List<String>) request.get("questions");
        List<String> answers = (List<String>) request.get("answers");
        String proposedAnswer = (String) request.get("proposedAnswer");
        if (controlName == null || controlName.isBlank()) {
            return Map.of("success", false, "message", "controlName is required");
        }
        return answeringGuideService.generateAnswerSummary(controlName, questions, answers, proposedAnswer);
    }

    // Finalize assessment via assessment-direct (POST by obfuscated ID)
    @PostMapping("/assessment-direct/{obfuscatedId}/finalize")
    public String finalizeAssessmentDirect(@PathVariable String obfuscatedId) {
        Optional<AssessmentUrls> maybeUrl = assessmentUrlsService.findByObfuscated(obfuscatedId);
        if (!maybeUrl.isPresent()) {
            return "redirect:/assessment-direct/" + obfuscatedId; // Invalid URL, stay on page
        }

        Assessment assessment = maybeUrl.get().getAssessment();

        // Move assessment to REVIEW state so the IS Manager can review before closing
        assessment.setStatus(AssessmentStatus.REVIEW);
        assessmentRepository.save(assessment);

        return "redirect:/assessment-direct/" + obfuscatedId;
    }
}
