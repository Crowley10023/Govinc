package com.govinc.assessment;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogService;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/assessment")
public class AssessmentController {
    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private SecurityCatalogService securityCatalogService;
    @Autowired
    private AssessmentDetailsService assessmentDetailsService;
    @Autowired
    private SecurityControlRepository securityControlRepository;
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;
    
    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

    // --- Inject UserRepository ---
    @Autowired
    private UserRepository userRepository;

    // --- Inject OrgUnitService ---
    @Autowired
    private OrgUnitService orgUnitService;

    @GetMapping("/create")
    public String showCreateAssessmentForm(Model model) {
        List<SecurityCatalog> catalogs = securityCatalogService.findAll();
        model.addAttribute("catalogs", catalogs);
        // --- Add users list to model ---
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        // --- Add org units to model ---
        List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
        model.addAttribute("orgUnits", orgUnits);
        return "create-assessment";
    }

    // POST handler for create-assessment
    @PostMapping("/create")
    public String createAssessment(
            @RequestParam("name") String name,
            @RequestParam("catalogId") Long catalogId,
            @RequestParam(value = "orgUnitId", required = false) Long orgUnitId,
            @RequestParam(value = "userIds", required = false) List<Long> userIds
    ) {
        SecurityCatalog catalog = securityCatalogService.findById(catalogId).orElse(null);
        if (catalog == null) {
            // handle error, redirect back or show error (for now, redirect to list)
            return "redirect:/assessment/list";
        }
        Assessment assessment = new Assessment();
        assessment.setName(name);
        assessment.setSecurityCatalog(catalog);
        assessment.setDate(LocalDate.now());
        // Persist org unit if set
        if (orgUnitId != null) {
            OrgUnit orgUnit = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
            if (orgUnit != null) {
                assessment.setOrgUnit(orgUnit);
            }
        }
        // Persist selected users
        if (userIds != null && !userIds.isEmpty()) {
            Set<User> users = userIds.stream()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            assessment.setUsers(users);
        }
        assessment = assessmentRepository.save(assessment);
        return "redirect:/assessment/" + assessment.getId() + "/controls";
    }

    @GetMapping("/list")
    public String showAssessments(Model model) {
        model.addAttribute("assessments", assessmentRepository.findAll());
        return "assessment-list";
    }

    // Add this mapping to serve assessment-step-controls.html as per your flow
    @GetMapping("/{id}/controls")
    public String assessmentStepControls(@PathVariable Long id, Model model) {
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isEmpty()) {
            return "assessment-not-found";
        }
        Assessment assessment = assessmentOpt.get();
        model.addAttribute("assessment", assessment);
        // Sorted controls by name
        List<SecurityControl> controls = new ArrayList<>();
        if (assessment.getSecurityCatalog() != null) {
            controls.addAll(assessment.getSecurityCatalog().getSecurityControls());
            controls.sort(Comparator.comparing(SecurityControl::getName, Comparator.nullsLast(String::compareTo)));
        }
        model.addAttribute("controls", controls);
        // Sorted answers
        List<MaturityAnswer> answers = new ArrayList<>();
        if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getMaturityModel() != null) {
            answers.addAll(assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers());
            answers.sort(Comparator.comparing(MaturityAnswer::getAnswer, Comparator.nullsLast(String::compareTo)));
        }
        model.addAttribute("answers", answers);

        // Add selected answers for each control
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        Map<Long, Long> controlAnswers = new HashMap<>();
        if (detailsOpt.isPresent()) {
            for (AssessmentControlAnswer aca : detailsOpt.get().getControlAnswers()) {
                if (aca.getSecurityControl() != null && aca.getMaturityAnswer() != null) {
                    controlAnswers.put(aca.getSecurityControl().getId(), aca.getMaturityAnswer().getId());
                }
            }
        }
        model.addAttribute("controlAnswers", controlAnswers);
        return "assessment-step-controls";
    }

    // POST handler for controls - saves answers and redirects to details page
    @PostMapping("/{id}/controls")
    public String handleAssessmentControls(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params) {
        // Find details or create new
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        AssessmentDetails details;
        if (!detailsOpt.isPresent()) {
            Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
            if (!assessmentOpt.isPresent()) return "redirect:/assessment/list";
            details = new AssessmentDetails();
            Set<Assessment> assessmentSet = new HashSet<>();
            assessmentSet.add(assessmentOpt.get());
            details.setAssessments(assessmentSet);
            details.setDate(LocalDate.now());
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = new HashSet<>();
        // Remove all previous answers for clean update
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("control_")) {
                try {
                    Long controlId = Long.parseLong(key.substring("control_".length()));
                    String answerIdStr = entry.getValue().get(0);
                    if (answerIdStr != null && !answerIdStr.isEmpty()) {
                        Long answerId = Long.parseLong(answerIdStr);
                        SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
                        MaturityAnswer maturityAnswer = maturityAnswerRepository.findById(answerId).orElse(null);
                        if (control != null && maturityAnswer != null) {
                            AssessmentControlAnswer aca = new AssessmentControlAnswer(control, maturityAnswer);
                            aca = assessmentControlAnswerRepository.save(aca);
                            answers.add(aca);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore parse errors
                }
            }
        }
        details.setControlAnswers(answers);
        assessmentDetailsService.save(details);
        return "redirect:/assessment/" + id;
    }

    @GetMapping("/{id}")
    public String getAssessmentById(@PathVariable Long id, Model model) {
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isPresent()) {
            Assessment assessment = assessmentOpt.get();
            model.addAttribute("assessment", assessment);
            
            // Control answers are always retrieved from AssessmentDetails
            Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
            AssessmentDetails details = detailsOpt.orElse(null);
            List<AssessmentControlAnswer> answers = new ArrayList<>();
            Map<Long, String> controlAnswers = new HashMap<>();
            if(details != null && details.getControlAnswers()!=null) {
                answers.addAll(details.getControlAnswers());
                for(AssessmentControlAnswer aca: details.getControlAnswers()){
                    if(aca.getSecurityControl()!=null && aca.getMaturityAnswer()!=null)
                        controlAnswers.put(aca.getSecurityControl().getId(), aca.getMaturityAnswer().getAnswer());
                }
            }
            model.addAttribute("answers", answers);
            model.addAttribute("controlAnswers", controlAnswers);

            // Summary table by answer type
            model.addAttribute("answerSummary", assessmentDetailsService.computeAnswerSummary(details));

            // Use only controls from the catalog assigned to this assessment
            // Sorted controls by name
            List<SecurityControl> controls = new ArrayList<>();
            if (assessment.getSecurityCatalog() != null) {
                controls.addAll(assessment.getSecurityCatalog().getSecurityControls());
                controls.sort(Comparator.comparing(SecurityControl::getName, Comparator.nullsLast(String::compareTo)));
            }
            model.addAttribute("controls", controls);

            // Pass the correct answers from the associated maturity model only
            // Sorted maturity answers
            List<MaturityAnswer> maturityAnswers = new ArrayList<>();
            if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getMaturityModel() != null) {
                maturityAnswers.addAll(assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers());
                maturityAnswers.sort(Comparator.comparing(MaturityAnswer::getAnswer, Comparator.nullsLast(String::compareTo)));
            }
            model.addAttribute("maturityAnswers", maturityAnswers);
            return "assessment-details";
        } else {
            return "assessment-not-found";
        }
    }

    // Save/update answer for a single control (AJAX POST from UI)
    @PostMapping("/{id}/answer")
    @ResponseBody
    public String saveAnswer(@PathVariable Long id, @RequestParam Long controlId, @RequestParam Long answerId) {
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        AssessmentDetails details = null;
        if (!detailsOpt.isPresent()) {
            // Try to find the assessment:
            Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
            if (!assessmentOpt.isPresent()) return "fail";
            details = new AssessmentDetails();
            // Link this details entity to the assessment
            Set<Assessment> assessmentSet = new HashSet<>();
            assessmentSet.add(assessmentOpt.get());
            details.setAssessments(assessmentSet);
            details.setDate(LocalDate.now());
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = details.getControlAnswers();
        // Find or add
        AssessmentControlAnswer found = null;
        for(AssessmentControlAnswer aca : answers) {
            if(aca.getSecurityControl()!=null && aca.getSecurityControl().getId().equals(controlId)) {
                found = aca; break;
            }
        }
        SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
        MaturityAnswer maturityAnswer = maturityAnswerRepository.findById(answerId).orElse(null);
        if (control == null || maturityAnswer == null) return "fail";

        if(found == null) {
            found = new AssessmentControlAnswer(control, maturityAnswer);
            found = assessmentControlAnswerRepository.save(found);
            answers.add(found);
        } else {
            found.setMaturityAnswer(maturityAnswer);
            found = assessmentControlAnswerRepository.save(found);
        }
        // Only update the modified/new answer, do NOT replace the set with only one answer
        assessmentDetailsService.save(details);
        return "ok";
    }

    // Finalize assessment (POST)
    @PostMapping("/{id}/finalize")
    public String finalizeAssessment(@PathVariable Long id) {
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        if (detailsOpt.isPresent()) {
            AssessmentDetails details = detailsOpt.get();
            // Mark as finalized (add a field for this in AssessmentDetails if you want persistently lock it)
            // Here we just simulate finalization
            // details.setFinalized(true);
            assessmentDetailsService.save(details);
        }
        return "redirect:/assessment/" + id;
    }

    // Delete assessment (POST)
    @PostMapping("/{id}/delete")
    public String deleteAssessment(@PathVariable Long id) {
        // Remove assessment reference from all AssessmentDetails entities before deleting
        Assessment assessment = assessmentRepository.findById(id).orElse(null);
        if (assessment != null) {
            List<AssessmentDetails> detailsList = assessmentDetailsService.findAll();
            for (AssessmentDetails details : detailsList) {
                if (details.getAssessments().contains(assessment)) {
                    details.getAssessments().remove(assessment);
                    assessmentDetailsService.save(details);
                }
            }
            assessmentRepository.delete(assessment);
        }
        return "redirect:/assessment/list";
    }

    // Download PDF (stub - returns text as PDF)
    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        String report = "Assessment Report for ID="+id+"\n";
        // Populate report text (stub)
        if(detailsOpt.isPresent()) {
            AssessmentDetails details = detailsOpt.get();
            report += "Completed on: " + details.getDate() + "\n";
        }
        byte[] pdfBytes = report.getBytes(StandardCharsets.UTF_8); // Should convert to actual PDF bytes
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment_"+id+".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // Download Excel (stub - returns text as Excel file)
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id) throws IOException {
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        StringBuilder builder = new StringBuilder();
        builder.append("Control,Answer\n");
        if(detailsOpt.isPresent()) {
            AssessmentDetails details = detailsOpt.get();
            for(AssessmentControlAnswer aca : details.getControlAnswers()) {
                builder.append(aca.getSecurityControl().getName()).append(",")
                        .append(aca.getMaturityAnswer().getAnswer()).append("\n");
            }
        }
        byte[] excelBytes = builder.toString().getBytes(StandardCharsets.UTF_8); // Should convert to real Excel if needed
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment_"+id+".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(excelBytes);
    }

    // --- Create direct URL for assessment ---
    @PostMapping("/{id}/create-url")
    @ResponseBody
    public Map<String, String> createUrl(@PathVariable Long id) {
        AssessmentUrls url = assessmentUrlsService.createOrReplaceUrl(id);
        String fullUrl = "/assessment-direct/" + url.getUrl();
        return Map.of("directUrl", fullUrl);
    }

    // --- Set OrgUnit for Assessment ---
    @PostMapping("/{id}/set-orgunit")
    public String setOrgUnitForAssessment(@PathVariable Long id, 
                                          @RequestParam(value = "orgUnitId", required = false) Long orgUnitId) {
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isPresent() && orgUnitId != null) {
            OrgUnit orgUnit = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
            if (orgUnit != null) {
                Assessment assessment = assessmentOpt.get();
                assessment.setOrgUnit(orgUnit);
                assessmentRepository.save(assessment);
            }
        }
        return "redirect:/assessment/" + id;
    }
}
