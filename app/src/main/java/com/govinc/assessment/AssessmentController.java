package com.govinc.assessment;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogService;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import com.govinc.catalog.SecurityControlDomain;

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    // --- Inject OrgServiceService ---
    @Autowired
    private OrgServiceService orgServiceService;

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
        // --- Add org services to model ---
        List<OrgService> orgServices = orgServiceService.getAllOrgServices();
        model.addAttribute("orgServices", orgServices);
        return "create-assessment";
    }

    // POST handler for create-assessment
    @PostMapping("/create")
    public String createAssessment(
            @RequestParam("name") String name,
            @RequestParam("catalogId") Long catalogId,
            @RequestParam(value = "orgUnitId", required = false) Long orgUnitId,
            @RequestParam(value = "userIds", required = false) List<Long> userIds,
            @RequestParam(value = "orgServiceIds", required = false) List<Long> orgServiceIds) {
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
        // Persist selected org services
        if (orgServiceIds != null && !orgServiceIds.isEmpty()) {
            Set<OrgService> orgServices = orgServiceIds.stream()
                    .map(id -> orgServiceService.getOrgService(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            assessment.setOrgServices(orgServices);
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
            if (!assessmentOpt.isPresent())
                return "redirect:/assessment/list";
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
            if (details != null && details.getControlAnswers() != null) {
                answers.addAll(details.getControlAnswers());
                for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                    if (aca.getSecurityControl() != null && aca.getMaturityAnswer() != null)
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
                maturityAnswers
                        .sort(Comparator.comparing(MaturityAnswer::getAnswer, Comparator.nullsLast(String::compareTo)));
            }
            model.addAttribute("maturityAnswers", maturityAnswers);

            // --- Pass securityControlDomains: all unique domains of controls in this
            // catalog ---
            List<SecurityControlDomain> securityControlDomains = controls.stream()
                    .map(SecurityControl::getSecurityControlDomain)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            model.addAttribute("securityControlDomains", securityControlDomains);

            // Also pass orgServices for details view
            model.addAttribute("orgServices", assessment.getOrgServices());

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
            if (!assessmentOpt.isPresent())
                return "fail";
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
        for (AssessmentControlAnswer aca : answers) {
            if (aca.getSecurityControl() != null && aca.getSecurityControl().getId().equals(controlId)) {
                found = aca;
                break;
            }
        }
        SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
        MaturityAnswer maturityAnswer = maturityAnswerRepository.findById(answerId).orElse(null);
        if (control == null || maturityAnswer == null)
            return "fail";

        if (found == null) {
            found = new AssessmentControlAnswer(control, maturityAnswer);
            found = assessmentControlAnswerRepository.save(found);
            answers.add(found);
        } else {
            found.setMaturityAnswer(maturityAnswer);
            found = assessmentControlAnswerRepository.save(found);
        }
        // Only update the modified/new answer, do NOT replace the set with only one
        // answer
        assessmentDetailsService.save(details);
        return "ok";
    }

    // Finalize assessment (POST)
    @PostMapping("/{id}/finalize")
    public String finalizeAssessment(@PathVariable Long id) {
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        if (detailsOpt.isPresent()) {
            AssessmentDetails details = detailsOpt.get();
            // Mark as finalized (add a field for this in AssessmentDetails if you want
            // persistently lock it)
            // Here we just simulate finalization
            // details.setFinalized(true);
            assessmentDetailsService.save(details);
        }
        return "redirect:/assessment/" + id;
    }

    // Delete assessment (POST)
    @PostMapping("/{id}/delete")
    public String deleteAssessment(@PathVariable Long id) {
        // Remove assessment reference from all AssessmentDetails entities before
        // deleting
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

    // Download PDF using iText (structured, all info, answers as table, improved
    // layout)
    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        if (assessmentOpt.isEmpty() || detailsOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Assessment assessment = assessmentOpt.get();
        AssessmentDetails details = detailsOpt.get();
        List<User> users = assessment.getUsers() != null ? new ArrayList<>(assessment.getUsers()) : new ArrayList<>();
        OrgUnit orgUnit = assessment.getOrgUnit();
        List<AssessmentControlAnswer> answers = (details.getControlAnswers() != null)
                ? new ArrayList<>(details.getControlAnswers())
                : new ArrayList<>();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            com.itextpdf.text.Document doc = new com.itextpdf.text.Document();
            com.itextpdf.text.pdf.PdfWriter writer = com.itextpdf.text.pdf.PdfWriter.getInstance(doc, baos);
            doc.open();

            // Add metadata
            doc.addTitle("Assessment Report");
            doc.addAuthor("GovInc Assessment System");
            doc.addSubject("Security Assessment Report");
            doc.addCreationDate();

            // Fonts and styles
            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA,
                    22, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA,
                    16, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font subHeaderFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 13, com.itextpdf.text.Font.BOLDITALIC);
            com.itextpdf.text.Font regularFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA,
                    12);
            com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA,
                    12, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font tableHeaderFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 11, com.itextpdf.text.Font.BOLD,
                    new com.itextpdf.text.BaseColor(255, 255, 255));
            com.itextpdf.text.Font tableCellFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.Font.FontFamily.HELVETICA, 10);

            // Title/cover section
            com.itextpdf.text.Paragraph title = new com.itextpdf.text.Paragraph("Assessment Report", titleFont);
            title.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
            doc.add(title);

            doc.add(com.itextpdf.text.Chunk.NEWLINE);
            com.itextpdf.text.Paragraph meta = new com.itextpdf.text.Paragraph();
            meta.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
            meta.add(new com.itextpdf.text.Chunk("Generated on: ", boldFont));
            meta.add(new com.itextpdf.text.Chunk(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), regularFont));
            doc.add(meta);
            doc.add(com.itextpdf.text.Chunk.NEWLINE);
            doc.add(new com.itextpdf.text.pdf.draw.LineSeparator(1, 100, com.itextpdf.text.BaseColor.LIGHT_GRAY,
                    com.itextpdf.text.Element.ALIGN_CENTER, 1));
            doc.add(com.itextpdf.text.Chunk.NEWLINE);

            // --- Chapter 1: General Info ---
            com.itextpdf.text.Paragraph chapter1 = new com.itextpdf.text.Paragraph("1. General Information",
                    headerFont);
            doc.add(chapter1);
            // Fix attribute lines: build Paragraph, add Chunks, then doc.add()
            com.itextpdf.text.Paragraph p1;
            p1 = new com.itextpdf.text.Paragraph();
            p1.add(new com.itextpdf.text.Chunk("Assessment Name: ", boldFont));
            p1.add(new com.itextpdf.text.Chunk(assessment.getName(), regularFont));
            doc.add(p1);
            p1 = new com.itextpdf.text.Paragraph();
            p1.add(new com.itextpdf.text.Chunk("Assessment ID: ", boldFont));
            p1.add(new com.itextpdf.text.Chunk(String.valueOf(assessment.getId()), regularFont));
            doc.add(p1);
            p1 = new com.itextpdf.text.Paragraph();
            p1.add(new com.itextpdf.text.Chunk("Date: ", boldFont));
            p1.add(new com.itextpdf.text.Chunk(assessment.getDate() != null ? assessment.getDate().toString() : "-",
                    regularFont));
            doc.add(p1);
            p1 = new com.itextpdf.text.Paragraph();
            p1.add(new com.itextpdf.text.Chunk("Catalog: ", boldFont));
            p1.add(new com.itextpdf.text.Chunk(
                    (assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"),
                    regularFont));
            doc.add(p1);
            p1 = new com.itextpdf.text.Paragraph();
            p1.add(new com.itextpdf.text.Chunk("Completed On: ", boldFont));
            p1.add(new com.itextpdf.text.Chunk(details.getDate() != null ? details.getDate().toString() : "-",
                    regularFont));
            doc.add(p1);
            doc.add(com.itextpdf.text.Chunk.NEWLINE);
            doc.add(new com.itextpdf.text.pdf.draw.LineSeparator(0.5f, 100, com.itextpdf.text.BaseColor.LIGHT_GRAY,
                    com.itextpdf.text.Element.ALIGN_CENTER, -4));
            doc.add(com.itextpdf.text.Chunk.NEWLINE);

            // --- Chapter 2: Users & Organization ---
            com.itextpdf.text.Paragraph chapter2 = new com.itextpdf.text.Paragraph("2. Users and Organization",
                    headerFont);
            doc.add(chapter2);
            if (orgUnit != null) {
                p1 = new com.itextpdf.text.Paragraph();
                p1.add(new com.itextpdf.text.Chunk("Org Unit: ", boldFont));
                p1.add(new com.itextpdf.text.Chunk(orgUnit.getName(), regularFont));
                doc.add(p1);
            } else {
                doc.add(new com.itextpdf.text.Paragraph("Org Unit: -", boldFont));
            }
            if (!users.isEmpty()) {
                doc.add(new com.itextpdf.text.Paragraph("Users Participating:", boldFont));
                com.itextpdf.text.List userList = new com.itextpdf.text.List(com.itextpdf.text.List.UNORDERED);
                for (User u : users) {
                    userList.add(new com.itextpdf.text.ListItem(u.getName() + " <" + u.getEmail() + ">", regularFont));
                }
                doc.add(userList);
            } else {
                doc.add(new com.itextpdf.text.Paragraph("Users: -", regularFont));
            }
            doc.add(com.itextpdf.text.Chunk.NEWLINE);
            doc.add(new com.itextpdf.text.pdf.draw.LineSeparator(0.5f, 100, com.itextpdf.text.BaseColor.LIGHT_GRAY,
                    com.itextpdf.text.Element.ALIGN_CENTER, 0));
            doc.add(com.itextpdf.text.Chunk.NEWLINE);

            // Chapter 3 – Assessment Answers Table
            com.itextpdf.text.Paragraph chapter3 = new com.itextpdf.text.Paragraph("3. Control Answers", headerFont);
            doc.add(chapter3);
            doc.add(com.itextpdf.text.Chunk.NEWLINE);
            com.itextpdf.text.pdf.PdfPTable table = new com.itextpdf.text.pdf.PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 2f, 2.5f, 3f, 3f, 1.5f });
            // Table header with color
            com.itextpdf.text.BaseColor thBg = new com.itextpdf.text.BaseColor(67, 100, 163);
            String[] ths = { "Domain", "Security Control", "Description", "Reference", "Answer" };
            for (String h : ths) {
                com.itextpdf.text.pdf.PdfPCell cell = new com.itextpdf.text.pdf.PdfPCell(
                        new com.itextpdf.text.Phrase(h, tableHeaderFont));
                cell.setBackgroundColor(thBg);
                cell.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (AssessmentControlAnswer aca : answers) {
                SecurityControl ctrl = aca.getSecurityControl();
                MaturityAnswer ans = aca.getMaturityAnswer();
                String domainName = ctrl != null && ctrl.getSecurityControlDomain() != null
                        ? ctrl.getSecurityControlDomain().getName()
                        : "-";
                String controlName = ctrl != null ? ctrl.getName() : "-";
                String controlDesc = ctrl != null ? (ctrl.getDetail() != null ? ctrl.getDetail() : "-") : "-";
                String question = ctrl != null ? (ctrl.getReference() != null ? ctrl.getReference() : "-") : "-";
                String answerText = ans != null ? ans.getAnswer() : "-";
                com.itextpdf.text.pdf.PdfPCell c1 = new com.itextpdf.text.pdf.PdfPCell(
                        new com.itextpdf.text.Phrase(domainName, tableCellFont));
                com.itextpdf.text.pdf.PdfPCell c2 = new com.itextpdf.text.pdf.PdfPCell(
                        new com.itextpdf.text.Phrase(controlName, tableCellFont));
                com.itextpdf.text.pdf.PdfPCell c3 = new com.itextpdf.text.pdf.PdfPCell(
                        new com.itextpdf.text.Phrase(controlDesc, tableCellFont));
                com.itextpdf.text.pdf.PdfPCell c4 = new com.itextpdf.text.pdf.PdfPCell(
                        new com.itextpdf.text.Phrase(question, tableCellFont));
                com.itextpdf.text.pdf.PdfPCell c5 = new com.itextpdf.text.pdf.PdfPCell(
                        new com.itextpdf.text.Phrase(answerText, boldFont));
                // Basic cell config
                c1.setPadding(4);
                c2.setPadding(4);
                c3.setPadding(4);
                c4.setPadding(4);
                c5.setPadding(4);
                c1.setVerticalAlignment(com.itextpdf.text.Element.ALIGN_TOP);
                c2.setVerticalAlignment(com.itextpdf.text.Element.ALIGN_TOP);
                c3.setVerticalAlignment(com.itextpdf.text.Element.ALIGN_TOP);
                c4.setVerticalAlignment(com.itextpdf.text.Element.ALIGN_TOP);
                c5.setVerticalAlignment(com.itextpdf.text.Element.ALIGN_TOP);
                table.addCell(c1);
                table.addCell(c2);
                table.addCell(c3);
                table.addCell(c4);
                table.addCell(c5);
            }
            doc.add(table);
            doc.add(com.itextpdf.text.Chunk.NEWLINE);
            doc.add(new com.itextpdf.text.pdf.draw.LineSeparator(0.5f, 100, com.itextpdf.text.BaseColor.LIGHT_GRAY,
                    com.itextpdf.text.Element.ALIGN_CENTER, 0));
            doc.add(com.itextpdf.text.Chunk.NEWLINE);

            // --- Chapter 4 – Summary ---
            com.itextpdf.text.Paragraph chapter4 = new com.itextpdf.text.Paragraph("4. Assessment Summary", headerFont);
            doc.add(chapter4);
            doc.add(com.itextpdf.text.Chunk.NEWLINE);
            com.itextpdf.text.Paragraph pSum = new com.itextpdf.text.Paragraph();
            pSum.add(new com.itextpdf.text.Chunk("Total Controls Answered: ", boldFont));
            pSum.add(new com.itextpdf.text.Chunk(String.valueOf(answers.size()), regularFont));
            doc.add(pSum);
            // Optionally, add statistics by answer type
            java.util.Map<String, Long> answerStats = answers.stream()
                    .filter(aca -> aca.getMaturityAnswer() != null)
                    .collect(Collectors.groupingBy(aca -> aca.getMaturityAnswer().getAnswer(), Collectors.counting()));
            if (!answerStats.isEmpty()) {
                com.itextpdf.text.Paragraph breakdown = new com.itextpdf.text.Paragraph("Breakdown by Answer Type: ",
                        boldFont);
                breakdown.setSpacingBefore(10);
                doc.add(breakdown);
                for (Map.Entry<String, Long> entry : answerStats.entrySet()) {
                    doc.add(new com.itextpdf.text.Paragraph("  • " + entry.getKey() + ": " + entry.getValue(),
                            regularFont));
                }
            }

            // Footer on every page: generated by + date
            String footerTxt = "Generated by GovInc Assessment System on: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            com.itextpdf.text.Phrase footerPhrase = new com.itextpdf.text.Phrase(footerTxt,
                    new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9,
                            com.itextpdf.text.Font.ITALIC, com.itextpdf.text.BaseColor.GRAY));
            int totalPages = writer.getPageNumber();
            for (int i = 1; i <= totalPages; i++) {
                com.itextpdf.text.pdf.PdfContentByte cb = writer.getDirectContentUnder();
                com.itextpdf.text.Rectangle rect = doc.getPageSize();
                com.itextpdf.text.pdf.ColumnText.showTextAligned(cb, com.itextpdf.text.Element.ALIGN_RIGHT,
                        footerPhrase, rect.getRight(30), rect.getBottom(22), 0);
            }

            doc.close();
            byte[] pdfBytes = baos.toByteArray();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment_" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            byte[] failBytes = ("Error creating PDF: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(failBytes);
        }
    }

    // Download Excel (stub - returns text as Excel file)
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id) throws IOException {
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        StringBuilder builder = new StringBuilder();
        builder.append("Control,Answer\n");
        if (detailsOpt.isPresent()) {
            AssessmentDetails details = detailsOpt.get();
            for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                builder.append(aca.getSecurityControl().getName()).append(",")
                        .append(aca.getMaturityAnswer().getAnswer()).append("\n");
            }
        }
        byte[] excelBytes = builder.toString().getBytes(StandardCharsets.UTF_8); // Should convert to real Excel if
                                                                                 // needed
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment_" + id + ".csv")
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
