package com.govinc.assessment;

import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.user.User;
import com.govinc.organization.OrgUnit;

import com.govinc.entity.OpenAIConfiguration;
import com.govinc.repository.OpenAIConfigurationRepository;
import com.govinc.util.OpenAIUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.apache.poi.xwpf.usermodel.*;
import java.io.File;
import java.io.FileInputStream;

@Component
public class AssessmentReporterWord {

    private final com.govinc.organization.OrgServiceAssessmentService orgServiceAssessmentService;
    private final OpenAIConfigurationRepository openAIConfigurationRepository;
    private final OpenAIUtil openAIUtil;

    // Progress tracking for word report generation
    private final java.util.Map<Long, ReportProgress> progressMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public AssessmentReporterWord(
            com.govinc.organization.OrgServiceAssessmentService orgServiceAssessmentService,
            OpenAIConfigurationRepository openAIConfigurationRepository,
            OpenAIUtil openAIUtil) {
        this.orgServiceAssessmentService = orgServiceAssessmentService;
        this.openAIConfigurationRepository = openAIConfigurationRepository;
        this.openAIUtil = openAIUtil;
    }

    public ReportProgress getProgress(Long assessmentId) {
        return progressMap.getOrDefault(assessmentId, new ReportProgress(0, "Not started"));
    }

    private void updateProgress(Long assessmentId, int percent, String status) {
        progressMap.put(assessmentId, new ReportProgress(percent, status));
    }

    private void clearProgress(Long assessmentId) {
        progressMap.remove(assessmentId);
    }

    /**
     * Creates a Word report (DOCX) using Apache POI with optional template.
     * Tracks progress during generation.
     */
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users,
            OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers, String templatePath) throws Exception {
        Long assessmentId = assessment.getId();
        updateProgress(assessmentId, 5, "Initializing...");

        XWPFDocument doc = null;
        boolean templateLoaded = false;

        try {
            // Try to load template if provided
            if (templatePath != null && !templatePath.isEmpty()) {
                updateProgress(assessmentId, 10, "Loading template...");
                File templateFile = new File(templatePath);
                if (templateFile.exists()) {
                    doc = new XWPFDocument(new FileInputStream(templateFile));
                    templateLoaded = true;
                    System.out.println("[AssessmentReporterWord] Loaded template from: " + templatePath);
                } else {
                    System.out.println("[AssessmentReporterWord] Template file not found at: " + templatePath);
                    doc = new XWPFDocument();
                }
            } else {
                updateProgress(assessmentId, 10, "Creating new document...");
                doc = new XWPFDocument();
            }

            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {

                // If template was successfully loaded, replace placeholders in template
                if (templateLoaded) {
                    updateProgress(assessmentId, 30, "Processing template placeholders...");
                    replacePlaceholdersInTemplate(doc, assessment, details, users, orgUnit, answers);
                } else {
                    // Generate content from scratch if no template
                    updateProgress(assessmentId, 30, "Generating report content...");
                    generateDefaultReport(doc, assessment, details, users, orgUnit, answers);
                }

                updateProgress(assessmentId, 85, "Formatting and finalizing...");
                // Serialize document
                doc.write(baos);
                updateProgress(assessmentId, 95, "Preparing download...");

                byte[] result = baos.toByteArray();
                updateProgress(assessmentId, 100, "Complete!");
                return result;
            }
        } finally {
            // Clear progress after generation completes or fails
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    clearProgress(assessmentId);
                }
            }, 3000); // Clear after 3 seconds to allow UI to display completion
        }
    }

    /**
     * Backwards compatibility overload without template path
     */
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users,
            OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        return createWordReport(assessment, details, users, orgUnit, answers, null);
    }

    /**
     * Replaces placeholders in a template Word document with actual assessment data.
     * Looks for patterns like {{PLACEHOLDER_NAME}} in paragraphs and table cells.
     * If placeholders are found, only replacements are done.
     * If no placeholders are found, the full report content is appended to the template.
     */
    private void replacePlaceholdersInTemplate(XWPFDocument doc, Assessment assessment, AssessmentDetails details,
            java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {

        System.out.println("[AssessmentReporterWord] Processing " + doc.getParagraphs().size() + " paragraphs");

        // Track if any placeholders were found and replaced
        boolean foundPlaceholders = false;

        // Replace placeholders in paragraphs
        for (XWPFParagraph para : doc.getParagraphs()) {
            if (replacePlaceholdersInParagraph(para, assessment, details, users, orgUnit)) {
                foundPlaceholders = true;
            }
        }

        // Replace placeholders in tables
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        if (replacePlaceholdersInParagraph(para, assessment, details, users, orgUnit)) {
                            foundPlaceholders = true;
                        }
                    }
                }
            }
        }

        System.out.println("[AssessmentReporterWord] Placeholders found: " + foundPlaceholders);

        // If no placeholders were found, append full report content to the template
        if (!foundPlaceholders) {
            System.out.println("[AssessmentReporterWord] No placeholders found. Appending full report content to template.");
            appendFullReportToTemplate(doc, assessment, details, users, orgUnit, answers);
        } else {
            System.out.println("[AssessmentReporterWord] Template placeholders replaced successfully");
        }
    }

    /**
     * Appends the full default report content to an existing template document.
     */
    private void appendFullReportToTemplate(XWPFDocument doc, Assessment assessment, AssessmentDetails details,
            java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {

        System.out.println("[AssessmentReporterWord] Appending full report to template");

        // Add separator
        XWPFParagraph separator = doc.createParagraph();
        separator.createRun().setText("--- Assessment Report Content ---");
        doc.createParagraph();

        // Generate and append the full report content
        generateDefaultReport(doc, assessment, details, users, orgUnit, answers);
    }

    /**
     * Replaces placeholders in a single paragraph, handling multi-run text properly.
     * Returns true if any placeholder was replaced, false otherwise.
     */
    private boolean replacePlaceholdersInParagraph(XWPFParagraph para, Assessment assessment, AssessmentDetails details,
            java.util.List<User> users, OrgUnit orgUnit) {

        // Collect all text from all runs
        StringBuilder fullText = new StringBuilder();
        java.util.List<XWPFRun> allRuns = new java.util.ArrayList<>(para.getRuns());
        for (int i = 0; i < allRuns.size(); i++) {
            XWPFRun run = allRuns.get(i);
            String runText = run.getText(0);
            if (runText != null) {
                fullText.append(runText);
            }
        }

        String text = fullText.toString();

        // Define replacements map
        java.util.Map<String, String> replacements = new java.util.HashMap<>();
        replacements.put("{{TITLE}}", "Assessment Report");
        replacements.put("{{ASSESSMENT_NAME}}", assessment.getName() != null ? assessment.getName() : "");
        replacements.put("{{ASSESSMENT_ID}}", String.valueOf(assessment.getId()));
        replacements.put("{{ASSESSMENT_DATE}}", assessment.getDate() != null ? assessment.getDate().toString() : "-");
        replacements.put("{{CATALOG_NAME}}", assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-");
        replacements.put("{{COMPLETED_DATE}}", details.getDate() != null ? details.getDate().toString() : "-");
        replacements.put("{{ORG_UNIT}}", orgUnit != null ? orgUnit.getName() : "-");
        replacements.put("{{GENERATED_DATE}}", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        replacements.put("{{USERS_COUNT}}", String.valueOf(users.size()));

        String modifiedText = text;
        boolean foundAny = false;
        for (java.util.Map.Entry<String, String> entry : replacements.entrySet()) {
            if (text.contains(entry.getKey())) {
                foundAny = true;
                modifiedText = modifiedText.replace(entry.getKey(), entry.getValue());
            }
        }

        if (foundAny) {
            System.out.println("[AssessmentReporterWord] Replacing: '" + text + "' -> '" + modifiedText + "'");

            // Preserve formatting from first run if available
            XWPFRun templateRun = (allRuns.size() > 0) ? allRuns.get(0) : null;

            // Remove all existing runs from back to front to preserve indices
            for (int i = allRuns.size() - 1; i >= 0; i--) {
                para.removeRun(i);
            }

            // Create new run with modified text
            XWPFRun newRun = para.createRun();
            newRun.setText(modifiedText);

            // Copy formatting from template run if available
            if (templateRun != null) {
                try {
                    if (templateRun.isBold()) newRun.setBold(true);
                    if (templateRun.isItalic()) newRun.setItalic(true);
                    if (templateRun.getUnderline() != null) newRun.setUnderline(templateRun.getUnderline());
                    if (templateRun.getFontSize() > 0) newRun.setFontSize(templateRun.getFontSize());
                    if (templateRun.getFontName() != null) newRun.setFontFamily(templateRun.getFontName());
                    String color = templateRun.getColor();
                    if (color != null && !color.isEmpty()) newRun.setColor(color);
                } catch (Exception e) {
                    System.out.println("[AssessmentReporterWord] Could not copy formatting: " + e.getMessage());
                }
            }
        }

        return foundAny;
    }

    /**
     * Generates a default Word report from scratch when no template is available.
     */
    private void generateDefaultReport(XWPFDocument doc, Assessment assessment, AssessmentDetails details,
            java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {

        // --------- Title Page ---------
        XWPFParagraph title = doc.createParagraph();
        XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        run.setColor("1F2E8B");
        run.addBreak();

        XWPFParagraph meta = doc.createParagraph();
        XWPFRun metarun = meta.createRun();
        metarun.setText("Generated on: " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metarun.setFontSize(12);
        metarun.addBreak();
        metarun.addBreak();

        doc.createParagraph(); // blank

        // --------- Table of Contents (manual entry) -----------
        XWPFParagraph tocTitle = doc.createParagraph();
        XWPFRun tocRun = tocTitle.createRun();
        tocRun.setText("Contents");
        tocRun.setBold(true);
        tocRun.setFontSize(16);
        tocRun.setColor("1F2E8B");
        tocRun.addBreak();
        String[] toc = new String[] { "1. General Information", "2. Users and Organization",
                "3. Assessment Summary", "4. Domain Overview Table", "5. Controls by Domain" };
        for (String item : toc) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(item);
            r.setFontSize(12);
        }
        doc.createParagraph();
        // -------------------------------------------------------

        // Gather all controls, answers, and scoring
        java.util.List<SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        a -> a.getSecurityControl().getId(),
                        a -> a,
                        (a1, a2) -> a1
                ));

        // --- 1. General Info ---
        XWPFParagraph genInfoHeader = doc.createParagraph();
        XWPFRun genInfoRun = genInfoHeader.createRun();
        genInfoRun.setText("1. General Information");
        genInfoRun.setBold(true);
        genInfoRun.setFontSize(16);
        genInfoRun.setColor("1F2E8B");

        addKeyValueFormatted(doc, "Assessment Name: ", assessment.getName());
        addKeyValueFormatted(doc, "Assessment ID: ", String.valueOf(assessment.getId()));
        addKeyValueFormatted(doc, "Date: ", assessment.getDate() != null ? assessment.getDate().toString() : "-");
        addKeyValueFormatted(doc, "Catalog: ",
                (assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        addKeyValueFormatted(doc, "Completed On: ", details.getDate() != null ? details.getDate().toString() : "-");
        doc.createParagraph();

        // --- 2. Users & Organization ---
        XWPFParagraph usersHeader = doc.createParagraph();
        XWPFRun usersRun = usersHeader.createRun();
        usersRun.setText("2. Users and Organization");
        usersRun.setBold(true);
        usersRun.setFontSize(16);
        usersRun.setColor("1F2E8B");

        if (orgUnit != null) {
            addKeyValueFormatted(doc, "Org Unit: ", orgUnit.getName());
        } else {
            addKeyValueFormatted(doc, "Org Unit: ", "-");
        }
        if (!users.isEmpty()) {
            XWPFParagraph up = doc.createParagraph();
            XWPFRun ur = up.createRun();
            ur.setBold(true);
            ur.setText("Users Participating:");
            for (User u : users) {
                XWPFParagraph userline = doc.createParagraph();
                XWPFRun userrun = userline.createRun();
                userrun.setText(u.getName() + " <" + u.getEmail() + ">");
            }
        } else {
            addKeyValueFormatted(doc, "Users: ", "-");
        }
        doc.createParagraph();

        // --- 3. Assessment Summary ---
        XWPFParagraph summaryHeader = doc.createParagraph();
        XWPFRun summaryRun = summaryHeader.createRun();
        summaryRun.setText("3. Assessment Summary");
        summaryRun.setBold(true);
        summaryRun.setFontSize(16);
        summaryRun.setColor("1F2E8B");
        doc.createParagraph();

        // --- AI-Generated Summary ---
        OpenAIConfiguration config = openAIConfigurationRepository.findAll().stream().findFirst().orElse(null);
        if (config != null && config.getSummaryPrompt() != null && !config.getSummaryPrompt().isBlank()) {
            java.util.List<String> answerTexts = answers.stream()
                    .map(a -> {
                        MaturityAnswer ma = a.getMaturityAnswer();
                        return ma != null ? ma.getAnswer() : null;
                    })
                    .filter(s -> s != null && !s.isBlank())
                    .collect(java.util.stream.Collectors.toList());
            String prompt = config.getSummaryPrompt() + "\n---\n" + String.join("\n", answerTexts);
            String summary;
            try {
                summary = openAIUtil.askAI(prompt);
                System.out.println("[OpenAI AssessmentReporter] API result: " + summary);
            } catch (Exception ex) {
                summary = "AI-generated summary: Not available (OpenAI API not reachable)";
                System.err.println("[OpenAI AssessmentReporter] OpenAI API call failed: " + ex.getMessage());
            }
            XWPFParagraph summaryAI = doc.createParagraph();
            XWPFRun aiRun = summaryAI.createRun();
            aiRun.setBold(true);
            aiRun.setItalic(true);
            aiRun.setFontSize(13);
            aiRun.setText("Assessment AI-generated summary:");

            XWPFParagraph summaryText = doc.createParagraph();
            XWPFRun sumRun = summaryText.createRun();
            sumRun.setText(summary);
        }

        if (assessment.getOrgServices() != null && !assessment.getOrgServices().isEmpty()) {
            XWPFParagraph orgSvcHead = doc.createParagraph();
            XWPFRun osvRun = orgSvcHead.createRun();
            osvRun.setText("3.1 Assigned Org Services");
            osvRun.setBold(true);
            osvRun.setFontSize(13);
            osvRun.setColor("434BA3");
            XWPFTable svcTable = doc.createTable();
            XWPFTableRow tRow = svcTable.getRow(0);
            setTableCellBackground(tRow.getCell(0), "434BA3");
            setTableCellText(tRow.getCell(0), "Org Service", true, "FFFFFF");
            tRow.addNewTableCell();
            setTableCellBackground(tRow.getCell(1), "434BA3");
            setTableCellText(tRow.getCell(1), "Description", true, "FFFFFF");
            for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                XWPFTableRow row = svcTable.createRow();
                row.getCell(0).setText(orgService.getName());
                row.getCell(1).setText(orgService.getDescription() != null ? orgService.getDescription() : "-");
            }
        }

        // Score summary
        int totalScore = 0;
        int numAnswered = 0;
        java.util.Map<Long, Integer> scoresByControl = new java.util.HashMap<>();
        for (SecurityControl ctrl : allControls) {
            if (answerMap.containsKey(ctrl.getId())) {
                AssessmentControlAnswer aca = answerMap.get(ctrl.getId());
                int score = aca.getScore();
                scoresByControl.put(ctrl.getId(), score);
                totalScore += score;
                numAnswered++;
            }
        }
        double avgScore = numAnswered > 0 ? (totalScore / (double) numAnswered) : 0.0;

        XWPFParagraph summaryTableIntro = doc.createParagraph();
        XWPFRun summaryTableIntroRun = summaryTableIntro.createRun();
        summaryTableIntroRun.setText("Assessment Summary Table:");
        summaryTableIntroRun.setBold(true);
        XWPFTable summaryTable = doc.createTable();
        XWPFTableRow stRow = summaryTable.getRow(0);
        setTableCellBackground(stRow.getCell(0), "434BA3");
        setTableCellText(stRow.getCell(0), "# Security Controls", true, "FFFFFF");
        stRow.addNewTableCell();
        setTableCellBackground(stRow.getCell(1), "434BA3");
        setTableCellText(stRow.getCell(1), "Average Score (%)", true, "FFFFFF");
        stRow.addNewTableCell();
        setTableCellBackground(stRow.getCell(2), "434BA3");
        setTableCellText(stRow.getCell(2), "Org Unit", true, "FFFFFF");
        XWPFTableRow stData = summaryTable.createRow();
        stData.getCell(0).setText(String.valueOf(allControls.size()));
        stData.getCell(1).setText(String.format("%.1f", avgScore));
        stData.getCell(2).setText(orgUnit != null ? orgUnit.getName() : "-");

        doc.createParagraph();

        // --- 4. Domain Overview Table ---
        XWPFParagraph domainOverviewHeader = doc.createParagraph();
        XWPFRun domainOverviewRun = domainOverviewHeader.createRun();
        domainOverviewRun.setText("4. Domain Overview Table");
        domainOverviewRun.setBold(true);
        domainOverviewRun.setFontSize(16);
        domainOverviewRun.setColor("1F2E8B");

        java.util.Map<String, java.util.List<SecurityControl>> controlsPerDomain = allControls.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ctrl -> ctrl.getSecurityControlDomain() != null ? ctrl.getSecurityControlDomain().getName()
                                : "Unknown"));
        XWPFTable overviewTable = doc.createTable();
        XWPFTableRow ovwHeader = overviewTable.getRow(0);
        setTableCellBackground(ovwHeader.getCell(0), "434BA3");
        setTableCellText(ovwHeader.getCell(0), "Security Control Domain", true, "FFFFFF");
        ovwHeader.addNewTableCell();
        setTableCellBackground(ovwHeader.getCell(1), "434BA3");
        setTableCellText(ovwHeader.getCell(1), "Score (%)", true, "FFFFFF");
        for (String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc = 0, n = 0;
            for (SecurityControl ctrl : domainCtrls) {
                if (scoresByControl.containsKey(ctrl.getId())) {
                    sc += scoresByControl.get(ctrl.getId());
                    n++;
                }
            }
            double perc = n > 0 ? (sc / (double) n) : 0.0;
            XWPFTableRow rw = overviewTable.createRow();
            rw.getCell(0).setText(domain);
            rw.getCell(1).setText(String.format("%.1f", perc));
        }

        doc.createParagraph();

        // --- 5. Controls by Domain (detailed) ---
        XWPFParagraph controlsDomainHeader = doc.createParagraph();
        XWPFRun controlsDomainRun = controlsDomainHeader.createRun();
        controlsDomainRun.setText("5. Controls by Domain");
        controlsDomainRun.setBold(true);
        controlsDomainRun.setFontSize(16);
        controlsDomainRun.setColor("1F2E8B");
        doc.createParagraph();

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;

        for (String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            XWPFParagraph domP = doc.createParagraph();
            XWPFRun domRun = domP.createRun();
            domRun.setText("5." + domainNum + " " + domain);
            domRun.setBold(true);
            domRun.setFontSize(13);
            domRun.setColor("434BA3");

            XWPFTable t = doc.createTable();
            XWPFTableRow h = t.getRow(0);
            String[] headers = {"Title", "Description", "Reference", "Answer", "Answer Source"};
            for (int i = 0; i < headers.length; i++) {
                if (i == 0) {
                    setTableCellBackground(h.getCell(i), "434BA3");
                    setTableCellText(h.getCell(i), headers[i], true, "FFFFFF");
                } else {
                    h.addNewTableCell();
                    setTableCellBackground(h.getCell(i), "434BA3");
                    setTableCellText(h.getCell(i), headers[i], true, "FFFFFF");
                }
            }
            for (SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName() != null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail() != null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference() != null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService
                                .findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null
                                        && osac.getSecurityControl().getId().equals(ctrl.getId())
                                        && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment
                                                .getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream()
                                                .min(java.util.Comparator
                                                        .comparingInt(ma -> Math.abs(ma.getScore() - osPercent)))
                                                .orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer)
                            break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if (ma != null) {
                        answ = ma.getAnswer();
                        src = "Assessment";
                    }
                }
                XWPFTableRow row = t.createRow();
                row.getCell(0).setText(tt);
                row.getCell(1).setText(desc);
                row.getCell(2).setText(ref);
                row.getCell(3).setText(answ);
                row.getCell(4).setText(src);
            }
            domainNum++;
        }

        // Footer paragraph
        XWPFParagraph footer = doc.createParagraph();
        XWPFRun footerRun = footer.createRun();
        footerRun.setItalic(true);
        footerRun.setFontSize(10);
        footerRun.setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    // Helper: Add formatted key-value pair to document with improved styling
    private void addKeyValueFormatted(XWPFDocument doc, String key, String value) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun k = p.createRun();
        k.setBold(true);
        k.setText(key);
        k.setColor("434BA3");
        XWPFRun v = p.createRun();
        v.setText(value);
        v.setColor("2C3E50");
    }

    // Helper: Set table cell background color
    private void setTableCellBackground(XWPFTableCell cell, String color) {
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = cell.getCTTc().addNewTcPr();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd ctShd = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd.Factory.newInstance();
            ctShd.setFill(color);
            tcPr.setShd(ctShd);
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] Error setting table cell background: " + e.getMessage());
        }
    }

    // Helper: Set table cell text with formatting
    private void setTableCellText(XWPFTableCell cell, String text, boolean bold, String color) {
        cell.setText("");
        XWPFParagraph p = cell.getParagraphs().get(0);
        XWPFRun r = p.createRun();
        r.setText(text);
        if (bold) r.setBold(true);
        r.setColor(color);
    }
}
