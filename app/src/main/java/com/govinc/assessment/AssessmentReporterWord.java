package com.govinc.assessment;

import com.govinc.catalog.SecurityControl;
import com.govinc.compliance.ComplianceCheck;
import com.govinc.compliance.ComplianceThreshold;
import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.user.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.docx4j.wml.PPr;
import org.docx4j.wml.RPr;
import org.docx4j.wml.PPrBase.PStyle;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblPr;
import org.docx4j.wml.Tr;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Style;
import org.docx4j.wml.Color;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.Drawing;
import org.docx4j.XmlUtils;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartUtils;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.nio.file.Files;
import com.govinc.util.OpenAIUtil;

/**
 * Generates Word assessment reports programmatically using docx4j,
 * mirroring the structure of the assessment-details page.
 */
@Component
public class AssessmentReporterWord {

    private final OrganisationDetailsRepository organisationDetailsRepository;

    // Progress tracking
    private final Map<Long, ReportProgress> progressMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired(required = false)
    private OpenAIUtil openAIUtil;

    // Known template marker patterns (order matters: most specific first)
    private static final List<String> KNOWN_MARKERS = Arrays.asList(
            "{{TITLE}}", "{{REPORT_TITLE}}",
            "{{AUTHOR}}", "{{CREATED_BY}}",
            "{{DATE}}", "{{CREATED_DATE}}",
            "{{ORG}}", "{{ORG_UNIT}}", "{{ORGANISATION}}",
            "{{REPORT_CONTENT}}");

    // CSS theme colors matching web app defaults from theme-css.html fallback values
    private static final java.awt.Color CHART_PRIMARY   = new java.awt.Color(0x22, 0x74, 0xA5); // --primary-blue
    private static final java.awt.Color CHART_DARK      = new java.awt.Color(0x16, 0x46, 0x66); // --primary-blue-dark
    private static final java.awt.Color CHART_ORANGE    = new java.awt.Color(0xFF, 0x95, 0x05); // --accent-orange
    private static final java.awt.Color CHART_SUCCESS   = new java.awt.Color(0x33, 0xAA, 0x33); // --success-green
    private static final java.awt.Color CHART_ERROR     = new java.awt.Color(0xCC, 0x22, 0x22); // --error-red
    private static final java.awt.Color CHART_TEXT      = new java.awt.Color(0x22, 0x2E, 0x3A); // --text-main
    private static final java.awt.Color[] CHART_PALETTE = {
            CHART_PRIMARY,
            CHART_ORANGE,
            CHART_SUCCESS,
            CHART_ERROR,
            CHART_DARK,
            new java.awt.Color(0x95, 0x96, 0xAE), // --secondary-color
            new java.awt.Color(0xDA, 0xF0, 0xFF), // --table-bg3
            new java.awt.Color(0xFF, 0xD8, 0x67), // --yellow-highlight
    };
    private static final java.awt.Font CHART_FONT_TITLE = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13);
    private static final java.awt.Font CHART_FONT_LABEL = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11);
    private static final java.awt.Font CHART_FONT_TICK  = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10);

    @Autowired
    public AssessmentReporterWord(OrganisationDetailsRepository organisationDetailsRepository) {
        this.organisationDetailsRepository = organisationDetailsRepository;
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
     * Main entrypoint: Creates a Word report by analyzing the template FIRST, then generating
     * AI content that respects the template's formatting and structure.
     */
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users,
                                   OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers, String templatePath) throws Exception {
        Long assessmentId = assessment.getId();
        updateProgress(assessmentId, 5, "Initializing report generation...");

        // Step 1: Load template
        updateProgress(assessmentId, 10, "Loading template...");
        String tplPath = templatePath;
        WordStyleMapping styleMapping = new WordStyleMapping();
        OrganisationDetails orgDetails = null;
        try {
            orgDetails = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
            if (orgDetails != null) {
                if ((tplPath == null || tplPath.isBlank()) && orgDetails.getWordTemplatePath() != null && !orgDetails.getWordTemplatePath().isBlank()) {
                    tplPath = orgDetails.getWordTemplatePath();
                }
                if (orgDetails.getWordTemplateStyleMappingJson() != null && !orgDetails.getWordTemplateStyleMappingJson().isBlank()) {
                    try {
                        styleMapping = new ObjectMapper().readValue(orgDetails.getWordTemplateStyleMappingJson(), WordStyleMapping.class);
                    } catch (Exception e2) {
                        System.err.println("[AssessmentReporterWord] Could not parse style mapping: " + e2.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] Could not read organisation details: " + e.getMessage());
        }

        WordprocessingMLPackage wordMLPackage = null;
        TemplateAnalysis templateAnalysis = new TemplateAnalysis();
        WordPlaceholderAttributeMapping phMapping = new WordPlaceholderAttributeMapping();
        // Load persisted placeholder-attribute mapping
        if (orgDetails != null && orgDetails.getWordTemplatePlaceholderMappingJson() != null
                && !orgDetails.getWordTemplatePlaceholderMappingJson().isBlank()) {
            try {
                phMapping = new ObjectMapper().readValue(orgDetails.getWordTemplatePlaceholderMappingJson(), WordPlaceholderAttributeMapping.class);
            } catch (Exception e2) {
                System.err.println("[AssessmentReporterWord] Could not parse placeholder mapping: " + e2.getMessage());
            }
        }

        if (tplPath != null && !tplPath.isBlank()) {
            File tplFile = new File(tplPath);
            if (tplFile.exists()) {
                try {
                    wordMLPackage = WordprocessingMLPackage.load(tplFile);

                    // Step 2: Analyze template BEFORE building prompt
                    updateProgress(assessmentId, 15, "Analyzing template structure...");
                    TemplateAnalyzer analyzer = new TemplateAnalyzer(wordMLPackage);
                    templateAnalysis = analyzer.analyze();
                    templateAnalysis.applyStyleMapping(styleMapping);
                    // Apply AI-detected placeholder hints for roles not yet found by marker/fuzzy scan
                    if (orgDetails != null && orgDetails.getWordTemplateAnalysisJson() != null) {
                        try {
                            WordTemplateMetadata metaHints = new ObjectMapper().readValue(
                                    orgDetails.getWordTemplateAnalysisJson(), WordTemplateMetadata.class);
                            Map<String, Integer> aiHints = metaHints.getAiBodyPlaceholderHints();
                            if (aiHints != null && !aiHints.isEmpty()) {
                                applyAiBodyHints(aiHints, templateAnalysis, wordMLPackage);
                            }
                        } catch (Exception eAiHints) {
                            System.err.println("[AssessmentReporterWord] Failed to apply AI hints: " + eAiHints.getMessage());
                        }
                    }
                    // Always apply user's explicit candidate selections — uses paragraphRef for direct access.
                    // This runs regardless of whether the analysis JSON is present.
                    applyUserCandidateSelections(phMapping, templateAnalysis, wordMLPackage);
                    System.out.println("[AssessmentReporterWord] phMapping roleToAttribute=" + phMapping.getRoleToAttribute() + " roleToSelectedSectionIndex=" + phMapping.getRoleToSelectedSectionIndex());
                    System.out.println("[AssessmentReporterWord] namedHFPH after applyUserCandidateSelections: " + templateAnalysis.getNamedHeaderFooterPlaceholders().keySet());

                    System.out.println("[AssessmentReporterWord] Template analysis complete:");
                    System.out.println("  - Available styles: " + templateAnalysis.getAvailableStyles());
                    System.out.println("  - Template structure sections: " + templateAnalysis.getTemplateStructure().size());
                    System.out.println("  - Placeholder paragraphs: " + templateAnalysis.getPlaceholders().size());
                } catch (Exception e) {
                    System.err.println("[AssessmentReporterWord] Failed to load template: " + e.getMessage());
                    wordMLPackage = WordprocessingMLPackage.createPackage();
                }
            } else {
                System.out.println("[AssessmentReporterWord] Template file not found at: " + tplPath);
                wordMLPackage = WordprocessingMLPackage.createPackage();
            }
        } else {
            updateProgress(assessmentId, 10, "No template configured, creating empty document");
            wordMLPackage = WordprocessingMLPackage.createPackage();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            updateProgress(assessmentId, 30, "Building report content...");
            ObjectFactory factory = new ObjectFactory();
            final TemplateAnalysis ta = templateAnalysis;

            // Replace named marker paragraphs in the template (body + headers/footers)
            final Map<String, P> namedPH = ta.getNamedPlaceholders();
            final Map<String, P> namedHFPH = ta.getNamedHeaderFooterPlaceholders();
            // Compute replacement values once (used for both body and header/footer markers)
            String titleVal = assessment.getName() != null ? assessment.getName() : "Assessment Report";
            String authorVal = "";
            if (assessment.getCreatedBy() != null && assessment.getCreatedBy().getName() != null) {
                authorVal = assessment.getCreatedBy().getName();
            } else if (users != null && !users.isEmpty()) {
                authorVal = users.stream().map(User::getName).filter(Objects::nonNull).findFirst().orElse("");
            }
            String dateVal = assessment.getCloseDate() != null
                    ? assessment.getCloseDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    : java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            String orgVal = orgUnit != null ? orgUnit.getName()
                    : (assessment.getOrgUnit() != null ? assessment.getOrgUnit().getName() : "");
            final OrganisationDetails finalOrgDetails = orgDetails;
            final WordPlaceholderAttributeMapping finalPhMapping = phMapping;
            for (Map.Entry<String, P> phEntry : namedPH.entrySet()) {
                // Use full role name (no suffix stripping) so custom roles like MY_CUSTOM_ROLE are looked up correctly.
                // resolveMarkerValue handles standard variant markers ({{REPORT_TITLE}}, {{CREATED_DATE}}, etc.) via fallback.
                String role = phEntry.getKey().replaceAll("[{}]", "");
                String attrPath = finalPhMapping.getRoleToAttribute().get(role);
                if (attrPath == null) {
                    // Legacy fallback: try base name (strip underscore suffix) for compatibility
                    String roleBase = role.replaceAll("_.*$", "");
                    attrPath = finalPhMapping.getRoleToAttribute().get(roleBase);
                }
                if ("SKIP".equals(attrPath)) continue;
                String replVal = resolveAttributeValue(attrPath, assessment, finalOrgDetails);
                if (replVal == null) replVal = resolveMarkerValue(phEntry.getKey(), titleVal, authorVal, dateVal, orgVal);
                if (replVal != null) {
                    // targetText: the original textbox text detected during analysis — used to find the right textbox
                    String targetText = ta.getPlaceholderTexts().get(phEntry.getKey());
                    replaceMarkerInParagraphOrTextBox(factory, phEntry.getValue(), phEntry.getKey(), replVal, targetText);
                    System.out.println("[AssessmentReporterWord] Replaced body marker " + phEntry.getKey() + " \u2192 " + replVal + (targetText != null ? " (target='" + targetText.substring(0, Math.min(30, targetText.length())) + "...')" : ""));
                }
            }
            System.out.println("[AssessmentReporterWord] namedHFPH at replacement time: " + namedHFPH.size() + " entries: " + namedHFPH.keySet());
            for (Map.Entry<String, P> phEntry : namedHFPH.entrySet()) {
                String role = phEntry.getKey().replaceAll("[{}]", "");
                String attrPathHF = finalPhMapping.getRoleToAttribute().get(role);
                if (attrPathHF == null) {
                    String roleBaseHF = role.replaceAll("_.*$", "");
                    attrPathHF = finalPhMapping.getRoleToAttribute().get(roleBaseHF);
                }
                if ("SKIP".equals(attrPathHF)) continue;
                String replVal = resolveAttributeValue(attrPathHF, assessment, finalOrgDetails);
                if (replVal == null) replVal = resolveMarkerValue(phEntry.getKey(), titleVal, authorVal, dateVal, orgVal);
                if (replVal != null) {
                    String targetText = ta.getPlaceholderTexts().get(phEntry.getKey());
                    replaceMarkerInParagraphOrTextBox(factory, phEntry.getValue(), phEntry.getKey(), replVal, targetText);
                    System.out.println("[AssessmentReporterWord] Replaced header/footer marker " + phEntry.getKey() + " \u2192 " + replVal);
                }
            }

            // --- Direct H/F replacement (belt-and-suspenders) ---
            // Handles cases where namedHFPH was not populated or the P-ref approach failed.
            // Uses the paragraphRef stored in TemplateSection directly — bypasses namedHFPH entirely.
            {
                Map<String, Integer> hfSelections = finalPhMapping.getRoleToSelectedSectionIndex();
                List<TemplateSection> hfStructure = ta.getTemplateStructure();
                System.out.println("[AssessmentReporterWord] DirectHF check: hfSelections=" + hfSelections);
                if (hfSelections != null && !hfSelections.isEmpty() && hfStructure != null) {
                    Map<Integer, TemplateSection> hfSecMap = new LinkedHashMap<>();
                    for (TemplateSection ts : hfStructure) hfSecMap.put(ts.getSectionIndex(), ts);
                    for (Map.Entry<String, Integer> hfEntry : hfSelections.entrySet()) {
                        String hfRole = hfEntry.getKey();
                        int hfSecIdx = hfEntry.getValue();
                        if (hfSecIdx < 0) continue;
                        TemplateSection hfSec = hfSecMap.get(hfSecIdx);
                        if (hfSec == null) {
                            System.out.println("[AssessmentReporterWord] DirectHF: no section found for idx=" + hfSecIdx);
                            continue;
                        }
                        String hfStyle = hfSec.getStyle() != null ? hfSec.getStyle() : "";
                        boolean isHFSection = hfStyle.startsWith("Header") || hfStyle.startsWith("Footer")
                                || hfSec.getContentIndex() < 0;
                        if (!isHFSection) continue; // body section — already handled by namedPH loop above
                        String hfMarker = "{{" + hfRole + "}}";
                        if ("SKIP".equals(finalPhMapping.getRoleToAttribute().get(hfRole))) continue;
                        String hfReplVal = resolveAttributeValue(finalPhMapping.getRoleToAttribute().get(hfRole), assessment, finalOrgDetails);
                        if (hfReplVal == null) hfReplVal = resolveMarkerValue(hfMarker, titleVal, authorVal, dateVal, orgVal);
                        if (hfReplVal == null) {
                            System.out.println("[AssessmentReporterWord] DirectHF: no replVal for role=" + hfRole
                                    + " attr=" + finalPhMapping.getRoleToAttribute().get(hfRole));
                            continue;
                        }
                        String hfTargetText = hfSec.getText();
                        P hfP = hfSec.getParagraphRef();
                        if (hfP == null) {
                            hfP = findParagraphInHFParts(wordMLPackage, hfTargetText);
                            System.out.println("[AssessmentReporterWord] DirectHF: paragraphRef=null, findByText="
                                    + (hfP != null ? "found" : "NOT FOUND") + " for target='" + hfTargetText + "'");
                        }
                        if (hfP != null) {
                            replaceMarkerInParagraphOrTextBox(factory, hfP, hfMarker, hfReplVal, hfTargetText);
                            System.out.println("[AssessmentReporterWord] DirectHF replaced " + hfMarker + " \u2192 '" + hfReplVal
                                    + "' (sectionIdx=" + hfSecIdx + ", style=" + hfStyle + ", target='"
                                    + (hfTargetText != null ? hfTargetText.substring(0, Math.min(30, hfTargetText.length())) : "null") + "')");
                        } else {
                            System.out.println("[AssessmentReporterWord] DirectHF: no paragraph found for " + hfMarker
                                    + " (target='" + hfTargetText + "')");
                        }
                        // Sweep ALL header/footer parts in case the template has multiple variants
                        // (default, first-page, even-odd). Replace every paragraph whose text matches.
                        if (hfTargetText != null && !hfTargetText.isBlank()) {
                            boolean wantHeader = hfStyle.startsWith("Header");
                            String snippetLC = (hfTargetText.length() > 40
                                    ? hfTargetText.substring(0, 40) : hfTargetText).toLowerCase().trim();
                            final P hfPSnapshot = hfP; // capture final ref for identity check
                            java.util.Set<P> alreadyReplaced = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                            if (hfPSnapshot != null) alreadyReplaced.add(hfPSnapshot);
                            try {
                                for (java.util.Map.Entry<?, ?> pe : wordMLPackage.getParts().getParts().entrySet()) {
                                    org.docx4j.openpackaging.parts.Part pt =
                                            (org.docx4j.openpackaging.parts.Part) pe.getValue();
                                    boolean isFtrPt = pt instanceof org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
                                    boolean isHdrPt = pt instanceof org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
                                    if (wantHeader ? !isHdrPt : !isFtrPt) continue;
                                    List<Object> sweepContent = null;
                                    try {
                                        if (isFtrPt) {
                                            org.docx4j.wml.Ftr f = ((org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) pt).getJaxbElement();
                                            if (f != null) sweepContent = f.getContent();
                                        } else {
                                            org.docx4j.wml.Hdr h = ((org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) pt).getJaxbElement();
                                            if (h != null) sweepContent = h.getContent();
                                        }
                                    } catch (Exception ignored) {}
                                    if (sweepContent == null) continue;
                                    for (P scanP : collectAllParagraphs(sweepContent)) {
                                        if (alreadyReplaced.contains(scanP)) continue;
                                        String scanTxt = extractRunTextFromP(scanP).toLowerCase().trim();
                                        if (scanTxt.isEmpty()) {
                                            // also check textbox text via XML
                                            try {
                                                String scanXml = XmlUtils.marshaltoString(scanP, true);
                                                if (scanXml != null) scanTxt = scanXml.replaceAll("<[^>]+>", " ").toLowerCase().trim();
                                            } catch (Exception ignored) {}
                                        }
                                        if (scanTxt.contains(snippetLC)) {
                                            replaceMarkerInParagraphOrTextBox(factory, scanP, hfMarker, hfReplVal, hfTargetText);
                                            alreadyReplaced.add(scanP);
                                            System.out.println("[AssessmentReporterWord] DirectHF sweep replaced " + hfMarker
                                                    + " in additional " + (wantHeader ? "header" : "footer") + " part");
                                        }
                                    }
                                }
                            } catch (Exception sweepEx) {
                                System.err.println("[AssessmentReporterWord] DirectHF sweep error: " + sweepEx.getMessage());
                            }
                        }
                    }
                }
            }

            // Build answer map indexed by control id
            Map<Long, AssessmentControlAnswer> answerMap = new LinkedHashMap<>();
            for (AssessmentControlAnswer a : answers) {
                if (a.getSecurityControl() != null && !Boolean.TRUE.equals(a.getIsNotApplicable())) {
                    answerMap.put(a.getSecurityControl().getId(), a);
                }
            }
            Set<Long> excludedControls = answers.stream()
                    .filter(a -> a != null && a.getSecurityControl() != null && Boolean.TRUE.equals(a.getIsNotApplicable()))
                    .map(a -> a.getSecurityControl().getId())
                    .collect(Collectors.toSet());
            List<SecurityControl> allControls = assessment.getEffectiveControls().stream()
                    .filter(sc -> sc != null && sc.getId() != null && !excludedControls.contains(sc.getId()))
                    .collect(Collectors.toList());
            List<Object> elements = new ArrayList<>();

            // Title — only add programmatically if the template has no {{TITLE}} placeholder
            boolean hasTemplateTitlePH = namedPH.containsKey("{{TITLE}}") || namedPH.containsKey("{{REPORT_TITLE}}")
                    || namedHFPH.containsKey("{{TITLE}}") || namedHFPH.containsKey("{{REPORT_TITLE}}");
            if (!hasTemplateTitlePH) {
                elements.add(createParagraphWithStyle(factory, assessment.getName() != null ? assessment.getName() : "Assessment Report", "Title", ta));
            }

            final String tblStyle = styleMapping.getTableStyle() != null ? styleMapping.getTableStyle() : "";

            // --- Introduction ---
            elements.add(createParagraphWithStyle(factory, "Introduction", "Heading1", ta));
            elements.add(setFullWidth(factory, buildIntroductionTable(factory, assessment, details, users), tblStyle));

            // --- Assessment Details ---
            elements.add(createPageBreak(factory));
            elements.add(createParagraphWithStyle(factory, "Assessment Details", "Heading1", ta));
            elements.add(setFullWidth(factory, buildDetailsTable(factory, assessment, details, users), tblStyle));

            // --- Summary of Answers ---
            elements.add(createPageBreak(factory));
            elements.add(createParagraphWithStyle(factory, "Summary of Answers", "Heading1", ta));
            Map<String, Integer> maturityCounts = new LinkedHashMap<>();
            int totalAnswered = 0;
            for (AssessmentControlAnswer aca : answerMap.values()) {
                if (aca.getMaturityAnswer() != null && aca.getMaturityAnswer().getAnswer() != null) {
                    maturityCounts.merge(aca.getMaturityAnswer().getAnswer(), 1, Integer::sum);
                    totalAnswered++;
                }
            }
            if (!maturityCounts.isEmpty()) {
                try {
                    String[] pieLabels = maturityCounts.keySet().toArray(new String[0]);
                    double[] pieValues = maturityCounts.values().stream().mapToDouble(Integer::doubleValue).toArray();
                    P pieChart = createPieChart(factory, wordMLPackage, "Summary of Answers", pieLabels, pieValues);
                    if (pieChart != null) elements.add(pieChart);
                } catch (Exception e) {
                    System.err.println("[AssessmentReporterWord] Could not create pie chart: " + e.getMessage());
                }
            }
            elements.add(setFullWidth(factory, buildMaturityCountsTable(factory, maturityCounts, totalAnswered), tblStyle));

            // --- Average Maturity Rating by Domain ---
            elements.add(createPageBreak(factory));
            elements.add(createParagraphWithStyle(factory, "Average Maturity Rating by Domain", "Heading1", ta));
            Map<String, double[]> domainStats = new LinkedHashMap<>();
            for (SecurityControl sc : allControls) {
                String dn = sc.getSecurityControlDomain() != null ? sc.getSecurityControlDomain().getName() : "Uncategorized";
                AssessmentControlAnswer aca = answerMap.get(sc.getId());
                int score = aca != null ? aca.getScore() : 0;
                double[] stats = domainStats.computeIfAbsent(dn, k -> new double[]{0.0, 0.0});
                stats[0] += score;
                stats[1]++;
            }
            if (!domainStats.isEmpty()) {
                try {
                    String[] domainNames = domainStats.keySet().toArray(new String[0]);
                    double[] domainAvgs = Arrays.stream(domainNames)
                            .mapToDouble(n -> { double[] s = domainStats.get(n); return s[1] > 0 ? s[0] / s[1] : 0; })
                            .toArray();
                    P barChart = createBarChart(factory, wordMLPackage, "Average Maturity Rating by Domain", domainNames, domainAvgs, true);
                    if (barChart != null) elements.add(barChart);
                } catch (Exception e) {
                    System.err.println("[AssessmentReporterWord] Could not create bar chart: " + e.getMessage());
                }
            }
            elements.add(setFullWidth(factory, buildDomainAveragesTable(factory, domainStats), tblStyle));

            // --- Compliance Check ---
            if (assessment.getComplianceCheck() != null && details != null) {
                ComplianceCheck cc = assessment.getComplianceCheck();
                elements.add(createPageBreak(factory));
                elements.add(createParagraphWithStyle(factory, "Compliance Check: " + cc.getName(), "Heading1", ta));
                elements.add(setFullWidth(factory, buildComplianceTable(factory, assessment, details, answerMap), tblStyle));
            }

            // --- Management Summary ---
            elements.add(createPageBreak(factory));
            elements.add(createParagraphWithStyle(factory, "Management Summary", "Heading1", ta));
            String mgmtSummary = assessment.getManagementSummary();
            if (mgmtSummary != null && !mgmtSummary.isBlank()) {
                for (P htmlPara : createHtmlParagraphs(factory, mgmtSummary, "Normal", ta)) {
                    elements.add(htmlPara);
                }
            } else {
                elements.add(createParagraphWithStyle(factory, "(No management summary available)", "Normal", ta));
            }

            // --- Per-domain control sections (extra chapter) ---
            Map<String, List<SecurityControl>> controlsByDomain = new LinkedHashMap<>();
            for (SecurityControl sc : allControls) {
                String dn = sc.getSecurityControlDomain() != null ? sc.getSecurityControlDomain().getName() : "Uncategorized";
                controlsByDomain.computeIfAbsent(dn, k -> new ArrayList<>()).add(sc);
            }
            if (!controlsByDomain.isEmpty()) {
                elements.add(createPageBreak(factory));
                elements.add(createParagraphWithStyle(factory, "Security Domain Details", "Heading1", ta));
                for (Map.Entry<String, List<SecurityControl>> entry : controlsByDomain.entrySet()) {
                    elements.add(createParagraphWithStyle(factory, entry.getKey(), "Heading2", ta));
                    elements.add(setFullWidth(factory, buildDomainControlsTable(factory, entry.getValue(), answerMap), tblStyle));
                }
            }

            updateProgress(assessmentId, 60, "Inserting content into template...");
            // Insert at {{REPORT_CONTENT}} placeholder, or append
            MainDocumentPart mdp = wordMLPackage.getMainDocumentPart();
            List<P> placeholders = templateAnalysis.getPlaceholders();
            if (!placeholders.isEmpty()) {
                List<Object> content = mdp.getContent();
                List<Integer> indexes = new ArrayList<>();
                for (P p : placeholders) {
                    int idx = content.indexOf(p);
                    if (idx >= 0) indexes.add(idx);
                }
                Collections.sort(indexes, Collections.reverseOrder());
                if (!indexes.isEmpty()) {
                    int insertAt = indexes.get(indexes.size() - 1);
                    for (int idx : indexes) {
                        if (idx >= 0 && idx < content.size()) content.remove(idx);
                    }
                    content.addAll(insertAt, elements);
                }
            } else {
                mdp.getContent().addAll(elements);
            }

            updateProgress(assessmentId, 90, "Saving document...");
            wordMLPackage.save(baos);
            updateProgress(assessmentId, 100, "Report generation complete");

            return baos.toByteArray();
        } finally {
            // Clear progress shortly after completion
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    clearProgress(assessmentId);
                }
            }, 3000);
        }
    }

    /**
     * Backwards compatible overload
     */
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users,
                                   OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        return createWordReport(assessment, details, users, orgUnit, answers, null);
    }


    // ======================================================
    // Table building helpers
    // ======================================================

    private Tbl buildIntroductionTable(ObjectFactory factory, Assessment assessment, AssessmentDetails details, List<User> users) {
        Tbl tbl = factory.createTbl();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");

        // What was done
        addDetailRow(tbl, factory, "Assessment", assessment.getName() != null ? assessment.getName() : "-");
        if (assessment.getSecurityCatalog() != null) {
            addDetailRow(tbl, factory, "Security Catalog", assessment.getSecurityCatalog().getName());
        }
        addDetailRow(tbl, factory, "Organization Unit", assessment.getOrgUnit() != null ? assessment.getOrgUnit().getName() : "-");
        addDetailRow(tbl, factory, "Status", assessment.getStatus() != null ? assessment.getStatus().toString() : "-");

        // When
        addDetailRow(tbl, factory, "Started", assessment.getCreationDate() != null ? assessment.getCreationDate().format(fmt) : "-");
        if (details != null && details.getCompletedDate() != null) {
            addDetailRow(tbl, factory, "Completed", details.getCompletedDate().format(fmt));
        } else if (assessment.getCloseDate() != null) {
            addDetailRow(tbl, factory, "Closed", assessment.getCloseDate().format(fmt));
        }

        // IS Managers
        String ismStr = "-";
        if (users != null && !users.isEmpty()) {
            ismStr = users.stream().map(User::getName).filter(Objects::nonNull).collect(Collectors.joining(", "));
        } else if (assessment.getUsers() != null && !assessment.getUsers().isEmpty()) {
            ismStr = assessment.getUsers().stream().map(User::getName).filter(Objects::nonNull).collect(Collectors.joining(", "));
        }
        addDetailRow(tbl, factory, "IS Manager(s)", ismStr);

        // Interviewees
        if (assessment.getInterviewees() != null && !assessment.getInterviewees().isEmpty()) {
            String iStr = assessment.getInterviewees().stream()
                    .map(User::getName).filter(Objects::nonNull).collect(Collectors.joining(", "));
            addDetailRow(tbl, factory, "Interviewees", iStr);
        } else {
            addDetailRow(tbl, factory, "Interviewees", "-");
        }

        // Prepared by
        addDetailRow(tbl, factory, "Prepared By", assessment.getCreatedBy() != null ? assessment.getCreatedBy().getName() : "-");

        return tbl;
    }

    private Tbl buildDetailsTable(ObjectFactory factory, Assessment assessment, AssessmentDetails details, List<User> users) {
        Tbl tbl = factory.createTbl();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        addDetailRow(tbl, factory, "Assessment Name", assessment.getName());
        addDetailRow(tbl, factory, "Creation Date", assessment.getCreationDate() != null ? assessment.getCreationDate().format(fmt) : "-");
        addDetailRow(tbl, factory, "Created By", assessment.getCreatedBy() != null ? assessment.getCreatedBy().getName() : "-");
        if (assessment.getCloseDate() != null) {
            addDetailRow(tbl, factory, "Close Date", assessment.getCloseDate().format(fmt));
        }
        addDetailRow(tbl, factory, "Organization Unit", assessment.getOrgUnit() != null ? assessment.getOrgUnit().getName() : "-");
        if (assessment.getSecurityCatalog() != null) {
            addDetailRow(tbl, factory, "Security Catalog", assessment.getSecurityCatalog().getName());
        }
        if (assessment.getComplianceCheck() != null) {
            addDetailRow(tbl, factory, "Compliance Check", assessment.getComplianceCheck().getName());
        }
        addDetailRow(tbl, factory, "Status", assessment.getStatus() != null ? assessment.getStatus().toString() : "-");
        if (details != null && details.getCompletedDate() != null) {
            addDetailRow(tbl, factory, "Completed Date", details.getCompletedDate().format(fmt));
        }
        String usersStr = "-";
        if (users != null && !users.isEmpty()) {
            usersStr = users.stream().map(User::getName).filter(Objects::nonNull).collect(Collectors.joining(", "));
        } else if (assessment.getUsers() != null && !assessment.getUsers().isEmpty()) {
            usersStr = assessment.getUsers().stream().map(User::getName).filter(Objects::nonNull).collect(Collectors.joining(", "));
        }
        addDetailRow(tbl, factory, "Assessors", usersStr);
        if (assessment.getInterviewees() != null && !assessment.getInterviewees().isEmpty()) {
            String iStr = assessment.getInterviewees().stream()
                    .map(User::getName).filter(Objects::nonNull).collect(Collectors.joining(", "));
            addDetailRow(tbl, factory, "Interviewees", iStr);
        }
        return tbl;
    }

    private void addDetailRow(Tbl tbl, ObjectFactory factory, String label, String value) {
        Tr row = factory.createTr();
        row.getContent().add(createTableCell(factory, label, null, true));
        row.getContent().add(createTableCell(factory, value != null ? value : "-", null, false));
        tbl.getContent().add(row);
    }

    private Tbl buildMaturityCountsTable(ObjectFactory factory, Map<String, Integer> maturityCounts, int total) {
        Tbl tbl = factory.createTbl();
        Tr header = factory.createTr();
        header.getContent().add(createTableCell(factory, "Maturity Level", null, true));
        header.getContent().add(createTableCell(factory, "Count", null, true));
        header.getContent().add(createTableCell(factory, "Percentage", null, true));
        tbl.getContent().add(header);
        for (Map.Entry<String, Integer> e : maturityCounts.entrySet()) {
            double pct = total > 0 ? (e.getValue() * 100.0 / total) : 0.0;
            Tr row = factory.createTr();
            row.getContent().add(createTableCell(factory, e.getKey(), null, false));
            row.getContent().add(createTableCell(factory, String.valueOf(e.getValue()), null, false));
            row.getContent().add(createTableCell(factory, String.format("%.1f%%", pct), null, false));
            tbl.getContent().add(row);
        }
        return tbl;
    }

    private Tbl buildDomainAveragesTable(ObjectFactory factory, Map<String, double[]> domainStats) {
        Tbl tbl = factory.createTbl();
        Tr header = factory.createTr();
        header.getContent().add(createTableCell(factory, "Domain", null, true));
        header.getContent().add(createTableCell(factory, "Average Score", null, true));
        header.getContent().add(createTableCell(factory, "Controls", null, true));
        tbl.getContent().add(header);
        for (Map.Entry<String, double[]> e : domainStats.entrySet()) {
            double avg = e.getValue()[1] > 0 ? e.getValue()[0] / e.getValue()[1] : 0.0;
            int count = (int) e.getValue()[1];
            Tr row = factory.createTr();
            row.getContent().add(createTableCell(factory, e.getKey(), null, false));
            row.getContent().add(createTableCell(factory, String.format("%.1f", avg), null, false));
            row.getContent().add(createTableCell(factory, String.valueOf(count), null, false));
            tbl.getContent().add(row);
        }
        return tbl;
    }

    private Tbl buildComplianceTable(ObjectFactory factory, Assessment assessment, AssessmentDetails details,
                                     Map<Long, AssessmentControlAnswer> answerMap) {
        ComplianceCheck cc = assessment.getComplianceCheck();
        Set<Long> catalogControlIds = new LinkedHashSet<>();
        for (SecurityControl sc : assessment.getEffectiveControls()) {
            catalogControlIds.add(sc.getId());
        }
        Set<Long> excludedControlIds = new LinkedHashSet<>();
        if (details != null && details.getControlAnswers() != null) {
            for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                if (aca.getSecurityControl() != null && Boolean.TRUE.equals(aca.getIsNotApplicable())) {
                    excludedControlIds.add(aca.getSecurityControl().getId());
                }
            }
        }
        List<AssessmentControlAnswer> answerList = new ArrayList<>();
        int answered = 0;
        double scoreSum = 0;
        int scoreCount = 0;
        if (details != null && details.getControlAnswers() != null) {
            for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                if (aca.getSecurityControl() != null
                        && catalogControlIds.contains(aca.getSecurityControl().getId())
                        && !Boolean.TRUE.equals(aca.getIsNotApplicable())) {
                    answerList.add(aca);
                    answered++;
                    if (aca.getMaturityAnswer() != null) {
                        scoreSum += aca.getMaturityAnswer().getRating();
                        scoreCount++;
                    }
                }
            }
        }
        double avgScore = scoreCount > 0 ? scoreSum / scoreCount : 0.0;
        int applicableControls = Math.max(0, catalogControlIds.size() - excludedControlIds.size());
        double coverage = applicableControls == 0 ? 0.0 : (answered * 100.0 / applicableControls);
        boolean compliant = !answerList.isEmpty();
        List<String[]> thresholdRows = new ArrayList<>();
        if (cc.getThresholds() != null) {
            for (ComplianceThreshold t : cc.getThresholds()) {
                boolean passed = false;
                if (!answerList.isEmpty()) {
                    if ("ALL_ABOVE".equals(t.getType())) {
                        passed = answerList.stream().allMatch(a -> a.getMaturityAnswer() != null
                                && a.getMaturityAnswer().getRating() >= t.getValue());
                    } else if ("AVERAGE_ABOVE".equals(t.getType())) {
                        passed = avgScore >= t.getValue();
                    }
                }
                if (!passed) compliant = false;
                thresholdRows.add(new String[]{
                        t.getRuleDescription() != null ? t.getRuleDescription() : "-",
                        t.getType() != null ? t.getType() : "-",
                        String.valueOf(t.getValue()),
                        passed ? "Yes" : "No"
                });
            }
        }
        Tbl tbl = factory.createTbl();
        addDetailRow(tbl, factory, "Controls Answered", answered + " / " + applicableControls);
        addDetailRow(tbl, factory, "Coverage", String.format("%.1f%%", coverage));
        addDetailRow(tbl, factory, "Average Score", String.format("%.1f", Math.round(avgScore * 10.0) / 10.0));
        addDetailRow(tbl, factory, "Result", compliant ? "Compliant" : "Not Compliant");
        if (!thresholdRows.isEmpty()) {
            Tr thHdr = factory.createTr();
            thHdr.getContent().add(createTableCell(factory, "Rule", null, true));
            thHdr.getContent().add(createTableCell(factory, "Type / Target / Passed", null, true));
            tbl.getContent().add(thHdr);
            for (String[] tr : thresholdRows) {
                Tr row = factory.createTr();
                row.getContent().add(createTableCell(factory, tr[0], null, false));
                row.getContent().add(createTableCell(factory, tr[1] + " \u2265 " + tr[2] + "  \u2192  " + tr[3], null, false));
                tbl.getContent().add(row);
            }
        }
        return tbl;
    }

    private Tbl buildDomainControlsTable(ObjectFactory factory, List<SecurityControl> controls,
                                          Map<Long, AssessmentControlAnswer> answerMap) {
        Tbl tbl = factory.createTbl();
        Tr header = factory.createTr();
        header.getContent().add(createTableCell(factory, "Control", null, true));
        header.getContent().add(createTableCell(factory, "Reference", null, true));
        header.getContent().add(createTableCell(factory, "Answer", null, true));
        header.getContent().add(createTableCell(factory, "Comment", null, true));
        tbl.getContent().add(header);
        for (SecurityControl sc : controls) {
            AssessmentControlAnswer aca = answerMap.get(sc.getId());
            String answer = aca != null && aca.getMaturityAnswer() != null ? aca.getMaturityAnswer().getAnswer() : "-";
            String comment = aca != null && aca.getComment() != null && !aca.getComment().isBlank() ? aca.getComment() : "-";
            Tr row = factory.createTr();
            row.getContent().add(createTableCell(factory, sc.getName() != null ? sc.getName() : "-", null, false));
            row.getContent().add(createTableCell(factory, sc.getReference() != null ? sc.getReference() : "-", null, false));
            row.getContent().add(createTableCell(factory, answer, null, false));
            row.getContent().add(createTableCell(factory, comment, null, false));
            tbl.getContent().add(row);
        }
        return tbl;
    }

    // ======================================================
    // Chart helpers
    // ======================================================

    @SuppressWarnings("unchecked")
    private P createPieChart(ObjectFactory factory, WordprocessingMLPackage wordMLPackage,
                              String title, String[] labels, double[] values) throws Exception {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        for (int i = 0; i < Math.min(labels.length, values.length); i++) {
            dataset.setValue(labels[i], values[i]);
        }
        JFreeChart chart = ChartFactory.createPieChart(title, dataset, true, true, false);
        chart.setBackgroundPaint(java.awt.Color.WHITE);
        chart.setBorderVisible(false);
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(CHART_FONT_TITLE);
            chart.getTitle().setPaint(CHART_TEXT);
        }
        PiePlot<String> plot = (PiePlot<String>) chart.getPlot();
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelFont(CHART_FONT_LABEL);
        plot.setLabelPaint(CHART_TEXT);
        for (int i = 0; i < labels.length; i++) {
            plot.setSectionPaint(labels[i], CHART_PALETTE[i % CHART_PALETTE.length]);
        }
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(CHART_FONT_LABEL);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ChartUtils.writeChartAsPNG(bos, chart, 500, 350);
        return embedImage(factory, wordMLPackage, bos.toByteArray(), "pie-chart.png", title);
    }

    private P createBarChart(ObjectFactory factory, WordprocessingMLPackage wordMLPackage,
                              String title, String[] categories, double[] values, boolean horizontal) throws Exception {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int i = 0; i < Math.min(categories.length, values.length); i++) {
            dataset.addValue(values[i], "Score", categories[i]);
        }
        PlotOrientation orientation = horizontal ? PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL;
        JFreeChart chart = ChartFactory.createBarChart(title, "", "Score", dataset, orientation, false, true, false);
        chart.setBackgroundPaint(java.awt.Color.WHITE);
        chart.setBorderVisible(false);
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(CHART_FONT_TITLE);
            chart.getTitle().setPaint(CHART_TEXT);
        }
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(new java.awt.Color(0xDA, 0xE1, 0xE7)); // --border
        plot.getDomainAxis().setTickLabelFont(CHART_FONT_TICK);
        plot.getDomainAxis().setLabelFont(CHART_FONT_LABEL);
        plot.getRangeAxis().setTickLabelFont(CHART_FONT_TICK);
        plot.getRangeAxis().setLabelFont(CHART_FONT_LABEL);
        plot.getDomainAxis().setTickLabelPaint(CHART_TEXT);
        plot.getRangeAxis().setTickLabelPaint(CHART_TEXT);
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, CHART_PRIMARY);
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ChartUtils.writeChartAsPNG(bos, chart, 550, Math.max(300, categories.length * 30));
        return embedImage(factory, wordMLPackage, bos.toByteArray(), "bar-chart.png", title);
    }

    private P embedImage(ObjectFactory factory, WordprocessingMLPackage wordMLPackage,
                         byte[] imgBytes, String filename, String title) throws Exception {
        if (imgBytes == null || imgBytes.length == 0) return null;
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(wordMLPackage, imgBytes);
        Inline inline = imagePart.createImageInline(filename, title != null ? title : "", 0, 1, false);
        Drawing drawing = factory.createDrawing();
        drawing.getAnchorOrInline().add(inline);
        R run = factory.createR();
        run.getContent().add(drawing);
        P p = factory.createP();
        p.getContent().add(run);
        return p;
    }

    /**
     * Map a template marker to its replacement value.
     */
    private String resolveMarkerValue(String marker, String title, String author, String date, String org) {
        if ("{{TITLE}}".equals(marker) || "{{REPORT_TITLE}}".equals(marker))     return title;
        if ("{{AUTHOR}}".equals(marker) || "{{CREATED_BY}}".equals(marker))     return author;
        if ("{{DATE}}".equals(marker) || "{{CREATED_DATE}}".equals(marker))     return date;
        if ("{{ORG}}".equals(marker) || "{{ORG_UNIT}}".equals(marker) || "{{ORGANISATION}}".equals(marker)) return org;
        return null;
    }

    /**
     * Resolve an assessment attribute path to its string value.
     * Used when a WordPlaceholderAttributeMapping is configured.
     */
    private String resolveAttributeValue(String attrPath, Assessment assessment, OrganisationDetails org) {
        if (attrPath == null || attrPath.isBlank()) return null;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        switch (attrPath) {
            case "assessment.name": return assessment.getName();
            case "assessment.creationDate": return assessment.getCreationDate() != null ? assessment.getCreationDate().format(fmt) : "";
            case "assessment.closeDate": return assessment.getCloseDate() != null ? assessment.getCloseDate().format(fmt) : "";
            case "assessment.createdBy.name": return assessment.getCreatedBy() != null ? assessment.getCreatedBy().getName() : "";
            case "assessment.orgUnit.name": return assessment.getOrgUnit() != null ? assessment.getOrgUnit().getName() : "";
            case "assessment.status": return assessment.getStatus() != null ? assessment.getStatus().toString() : "";
            case "assessment.managementSummary": return assessment.getManagementSummary();
            case "assessment.securityCatalog.name": return assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "";
            case "org.organisationName": return org != null ? org.getOrganisationName() : "";
            default: return null;
        }
    }

    /**
     * Apply 100% width (and optional table style) to a docx4j Tbl.
     */
    private Tbl setFullWidth(ObjectFactory factory, Tbl tbl, String tableStyleId) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<w:tblPr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">");
            if (tableStyleId != null && !tableStyleId.isBlank()) {
                String safeId = tableStyleId.replaceAll("[<>&\"'\\\\]", "");
                sb.append("<w:tblStyle w:val=\"").append(safeId).append("\"/>");
            }
            sb.append("<w:tblW w:w=\"5000\" w:type=\"pct\"/>");
            sb.append("</w:tblPr>");
            TblPr tblPr = (TblPr) XmlUtils.unmarshalString(sb.toString());
            tbl.setTblPr(tblPr);
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] setFullWidth failed: " + e.getMessage());
        }
        return tbl;
    }

    /**
     * Recursively walk a content list replacing the first paragraph whose text
     * starts with {@code targetText} (first 20 chars). If {@code targetText} is
     * null, falls back to the very first paragraph with any text (original behaviour).
     * Handles: Drawing → anchor/inline → graphicData → wsp → txbxContent → P.
     * Returns true once a replacement has been made.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean replaceFirstTextInContent(List<?> items, String replacement, ObjectFactory factory, String targetText) {
        for (Object item : items) {
            Object uw = XmlUtils.unwrap(item);
            if (uw == null) continue;
            if (uw instanceof P) {
                P p = (P) uw;
                boolean hasText = false;
                outer:
                for (Object pc : p.getContent()) {
                    Object puw = XmlUtils.unwrap(pc);
                    if (puw instanceof R) {
                        for (Object rc : ((R) puw).getContent()) {
                            if (XmlUtils.unwrap(rc) instanceof Text) { hasText = true; break outer; }
                        }
                    }
                }
                if (hasText) {
                    // If a target text is provided, verify this paragraph matches before replacing
                    if (targetText != null && !targetText.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Object pc : p.getContent()) {
                            Object puw = XmlUtils.unwrap(pc);
                            if (puw instanceof R) {
                                for (Object rc : ((R) puw).getContent()) {
                                    Object ruw = XmlUtils.unwrap(rc);
                                    if (ruw instanceof Text) sb.append(((Text) ruw).getValue());
                                }
                            }
                        }
                        String pText = sb.toString().trim();
                        String snippet = targetText.length() > 20 ? targetText.substring(0, 20) : targetText;
                        if (!pText.toLowerCase().contains(snippet.toLowerCase())) {
                            continue; // wrong textbox — keep searching
                        }
                    }
                    p.getContent().removeIf(c -> XmlUtils.unwrap(c) instanceof R);
                    R r = factory.createR();
                    Text t = factory.createText();
                    t.setValue(replacement);
                    t.setSpace("preserve");
                    r.getContent().add(t);
                    p.getContent().add(r);
                    return true;
                }
                continue;
            }
            if (uw instanceof org.docx4j.wml.Drawing) {
                if (replaceFirstTextInContent(((org.docx4j.wml.Drawing) uw).getAnchorOrInline(), replacement, factory, targetText))
                    return true;
                continue;
            }
            // wp:anchor / wp:inline → graphic → graphicData → any (mirrors extractTextBoxTextFromObject)
            if (uw instanceof org.docx4j.dml.wordprocessingDrawing.Anchor
                    || uw instanceof org.docx4j.dml.wordprocessingDrawing.Inline) {
                try {
                    java.lang.reflect.Method getGraphic = uw.getClass().getMethod("getGraphic");
                    Object graphic = getGraphic.invoke(uw);
                    if (graphic != null) {
                        java.lang.reflect.Method getGraphicData = graphic.getClass().getMethod("getGraphicData");
                        Object graphicData = getGraphicData.invoke(graphic);
                        if (graphicData != null) {
                            java.lang.reflect.Method mGetAny = graphicData.getClass().getMethod("getAny");
                            List<?> anyList = (List<?>) mGetAny.invoke(graphicData);
                            if (anyList != null && replaceFirstTextInContent(anyList, replacement, factory, targetText))
                                return true;
                        }
                    }
                } catch (Exception ignored) {}
                continue; // fully handled — don't fall into generic reflection
            }
            // mc:AlternateContent → only process mc:Choice, skip mc:Fallback to avoid duplicates
            {
                java.lang.reflect.Method getChoiceM = null;
                try { getChoiceM = uw.getClass().getMethod("getChoice"); } catch (NoSuchMethodException e) { /* not AlternateContent */ }
                if (getChoiceM != null) {
                    try {
                        List<?> choices = (List<?>) getChoiceM.invoke(uw);
                        if (choices != null) {
                            for (Object ch : choices) {
                                Object chUw = XmlUtils.unwrap(ch);
                                if (chUw == null) continue;
                                try {
                                    java.lang.reflect.Method mGetAny = chUw.getClass().getMethod("getAny");
                                    List<?> any = (List<?>) mGetAny.invoke(chUw);
                                    if (any != null && replaceFirstTextInContent(any, replacement, factory, targetText))
                                        return true;
                                } catch (Exception ignored2) {}
                            }
                        }
                    } catch (Exception ignored) {}
                    continue; // skip fallback — already handled via Choice above
                }
            }
            // wps:wsp (CTWordprocessingShape) → getTxbx() → getTxbxContent() → getContent()
            {
                java.lang.reflect.Method getTxbxM = null;
                try { getTxbxM = uw.getClass().getMethod("getTxbx"); } catch (NoSuchMethodException e) { /* not wsp */ }
                if (getTxbxM != null) {
                    try {
                        Object txbx = getTxbxM.invoke(uw);
                        if (txbx != null) {
                            java.lang.reflect.Method getTxbxContent = txbx.getClass().getMethod("getTxbxContent");
                            Object txbxContent = getTxbxContent.invoke(txbx);
                            if (txbxContent != null) {
                                java.lang.reflect.Method getContent = txbxContent.getClass().getMethod("getContent");
                                List<?> content = (List<?>) getContent.invoke(txbxContent);
                                if (content != null && replaceFirstTextInContent(content, replacement, factory, targetText))
                                    return true;
                            }
                        }
                    } catch (Exception ignored) {}
                    continue; // handled wsp shape
                }
            }
            // Generic fallbacks for any other container types
            try {
                java.lang.reflect.Method m = uw.getClass().getMethod("getContent");
                Object sub = m.invoke(uw);
                if (sub instanceof List && !((List) sub).isEmpty()) {
                    if (replaceFirstTextInContent((List<?>) sub, replacement, factory, targetText)) return true;
                }
            } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException | IllegalAccessException ignored) {}
            try {
                java.lang.reflect.Method m = uw.getClass().getMethod("getAnchorOrInline");
                Object sub = m.invoke(uw);
                if (sub instanceof List && !((List) sub).isEmpty()) {
                    if (replaceFirstTextInContent((List<?>) sub, replacement, factory, targetText)) return true;
                }
            } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException | IllegalAccessException ignored) {}
        }
        return false;
    }

    /** Backward-compat overload — replaces first textbox found (no target matching). */
    private boolean replaceFirstTextInContent(List<?> items, String replacement, ObjectFactory factory) {
        return replaceFirstTextInContent(items, replacement, factory, null);
    }

    /**
     * Smart replacement: if the paragraph contains an embedded text box, replace the
     * textbox whose text starts with {@code targetText} (first 20 chars).
     * If no targetText is given or no match is found, falls back to the first textbox.
     * If no textbox is present, falls back to standard full-paragraph replacement.
     */
    private void replaceMarkerInParagraphOrTextBox(ObjectFactory factory, P paragraph, String marker, String replacement, String targetText) {
        try {
            String xml = XmlUtils.marshaltoString(paragraph, true);
            if (xml != null && (xml.contains("txbxContent") || xml.contains("v:textbox"))) {
                // Try targeted replacement first
                boolean replaced = replaceFirstTextInContent(paragraph.getContent(), replacement, factory, targetText);
                if (!replaced && targetText != null) {
                    // fallback: replace first textbox if target not found
                    replaced = replaceFirstTextInContent(paragraph.getContent(), replacement, factory, null);
                }
                if (replaced) {
                    System.out.println("[AssessmentReporterWord] Replaced text inside text box for " + marker + " \u2192 " + replacement);
                    return;
                }
                // IMPORTANT: do NOT fall through to replaceMarkerInParagraph here.
                // The Drawing is stored inside a w:r child of this paragraph — calling
                // replaceMarkerInParagraph would strip that run (and thus the whole cover).
                System.out.println("[AssessmentReporterWord] WARNING: textbox replacement found no match for " + marker + ", preserving paragraph structure unchanged.");
                return;
            }
        } catch (Exception ignored) {}
        replaceMarkerInParagraph(factory, paragraph, marker, replacement);
    }

    /** Backward-compat overload — uses first textbox found (no target matching). */
    private void replaceMarkerInParagraphOrTextBox(ObjectFactory factory, P paragraph, String marker, String replacement) {
        replaceMarkerInParagraphOrTextBox(factory, paragraph, marker, replacement, null);
    }

    /**
     * Replace the text marker in a paragraph's runs with the given replacement value.
     * Preserves paragraph formatting (style) but replaces the run content.
     */
    private void replaceMarkerInParagraph(ObjectFactory factory, P paragraph, String marker, String replacement) {
        try {
            paragraph.getContent().removeIf(o -> {
                Object unwrapped = XmlUtils.unwrap(o);
                return unwrapped instanceof R;
            });
            R run = factory.createR();
            Text t = factory.createText();
            t.setValue(replacement != null ? replacement : "");
            t.setSpace("preserve");
            run.getContent().add(t);
            paragraph.getContent().add(run);
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] replaceMarkerInParagraph failed: " + e.getMessage());
        }
    }

    /**
     * Analyze a Word template file and persist the analysis results to the database.
     * Called whenever a template is uploaded or changed.
     */
    public void analyzeAndPersistTemplate(String templatePath) {
        if (templatePath == null || templatePath.isBlank()) return;
        try {
            File tplFile = new File(templatePath);
            if (!tplFile.exists()) {
                System.err.println("[AssessmentReporterWord] Template file not found for analysis: " + templatePath);
                return;
            }
            // Compute SHA-256 checksum
            byte[] fileBytes = Files.readAllBytes(tplFile.toPath());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(fileBytes);
            StringBuilder hexStr = new StringBuilder();
            for (byte b : digest) hexStr.append(String.format("%02x", b));
            String checksum = hexStr.toString();

            // Load and analyze template
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(tplFile);
            TemplateAnalyzer analyzer = new TemplateAnalyzer(pkg);
            TemplateAnalysis analysis = analyzer.analyze();

            // Build serializable metadata
            WordTemplateMetadata meta = new WordTemplateMetadata();
            meta.setChecksum(checksum);
            meta.setAnalysisTimestamp(java.time.LocalDateTime.now().toString());
            meta.setAvailableStyles(new ArrayList<>(analysis.getAvailableStyles()));
            meta.setTableStyles(new ArrayList<>(analysis.getTableStyles()));
            Set<String> allMarkers = new LinkedHashSet<>(analysis.getNamedPlaceholders().keySet());
            allMarkers.addAll(analysis.getNamedHeaderFooterPlaceholders().keySet());
            meta.setFoundMarkers(new ArrayList<>(allMarkers));
            meta.setHasHeader(analysis.isHasHeader());
            meta.setHeaderText(analysis.getHeaderText());
            meta.setHasFooter(analysis.isHasFooter());
            meta.setFooterText(analysis.getFooterText());

            List<WordTemplateMetadata.TemplateSectionMeta> structureMeta = new ArrayList<>();
            for (TemplateSection ts : analysis.getTemplateStructure()) {
                WordTemplateMetadata.TemplateSectionMeta sm = new WordTemplateMetadata.TemplateSectionMeta();
                sm.setStyle(ts.getStyle());
                sm.setSectionIndex(ts.getSectionIndex());
                String preview = ts.getText().length() > 100 ? ts.getText().substring(0, 100) + "..." : ts.getText();
                sm.setTextPreview(preview);
                for (String marker : KNOWN_MARKERS) {
                    if (ts.getText().contains(marker)) { sm.setMarker(marker); break; }
                }
                structureMeta.add(sm);
            }
            meta.setStructure(structureMeta);

            // AI-based placeholder identification (single call, persisted for report generation)
            Map<String, Integer> aiBodyHints = identifyPlaceholdersWithAI(analysis);
            if (aiBodyHints != null && !aiBodyHints.isEmpty()) {
                meta.setAiBodyPlaceholderHints(aiBodyHints);
            }
            // Persist AI multi-candidates
            if (!analysis.getAiCandidateDetails().isEmpty()) {
                meta.setAiCandidates(analysis.getAiCandidateDetails());
            }

            // Populate tableStyleNames (id → displayName) from the analyzer
            meta.setTableStyleNames(analysis.getTableStyleNameMap());

            // Build combined detected placeholder texts: fuzzy/exact detections + AI hint texts
            Map<String, String> allPlaceholderTexts = new java.util.LinkedHashMap<>(analysis.getPlaceholderTexts());
            if (aiBodyHints != null) {
                for (Map.Entry<String, Integer> hint : aiBodyHints.entrySet()) {
                    String marker = "{{" + hint.getKey() + "}}";
                    int idx = hint.getValue();
                    if (idx >= 0 && !allPlaceholderTexts.containsKey(marker)) {
                        analysis.getTemplateStructure().stream()
                            .filter(s -> s.getContentIndex() == idx).findFirst()
                            .ifPresent(s -> allPlaceholderTexts.put(marker, s.getText()));
                    }
                }
            }
            meta.setDetectedPlaceholderTexts(allPlaceholderTexts);

            // Rich analysis: placeholders, character styles, heading hierarchy, TOC, H/F
            // Merge named placeholder map into richPlaceholders list
            List<WordTemplateMetadata.PlaceholderInfo> richPHs = new ArrayList<>(analysis.getRichPlaceholders());
            // Add EXPLICIT_MARKER entries from namedPlaceholders (not already present as FIELD/SDT)
            for (Map.Entry<String, P> entry : analysis.getNamedPlaceholders().entrySet()) {
                String marker = entry.getKey(); // e.g. {{TITLE}}
                String role = marker.replaceAll("[{}]", "").replaceAll("_.*", ""); // TITLE, AUTHOR, DATE, ORG
                boolean alreadyPresent = richPHs.stream().anyMatch(p -> role.equalsIgnoreCase(p.getRole()) && !"EXPLICIT_MARKER".equals(p.getDetectionType()));
                if (!alreadyPresent) {
                    WordTemplateMetadata.PlaceholderInfo pi = new WordTemplateMetadata.PlaceholderInfo();
                    pi.setRole(role);
                    pi.setDetectionType("EXPLICIT_MARKER");
                    pi.setLocation("BODY");
                    pi.setOriginalText(analysis.getPlaceholderTexts().getOrDefault(marker, marker));
                    richPHs.add(pi);
                }
            }
            for (Map.Entry<String, P> entry : analysis.getNamedHeaderFooterPlaceholders().entrySet()) {
                String marker = entry.getKey();
                String role = marker.replaceAll("[{}]", "").replaceAll("_.*", "");
                WordTemplateMetadata.PlaceholderInfo pi = new WordTemplateMetadata.PlaceholderInfo();
                pi.setRole(role);
                pi.setDetectionType("EXPLICIT_MARKER");
                pi.setLocation("HEADER/FOOTER");
                pi.setOriginalText(marker);
                richPHs.add(pi);
            }
            // Add fuzzy detections from placeholderTexts not yet in richPHs
            for (Map.Entry<String, String> pt : analysis.getPlaceholderTexts().entrySet()) {
                String marker = pt.getKey();
                if (KNOWN_MARKERS.contains(marker)) continue; // already handled above as EXPLICIT_MARKER
                String role = marker.replaceAll("[{}]", "").replaceAll("_.*", "");
                WordTemplateMetadata.PlaceholderInfo pi = new WordTemplateMetadata.PlaceholderInfo();
                pi.setRole(role);
                pi.setDetectionType("FUZZY");
                pi.setLocation("BODY");
                pi.setOriginalText(pt.getValue());
                richPHs.add(pi);
            }
            // Add AI hints as AI type entries
            if (aiBodyHints != null) {
                for (Map.Entry<String, Integer> hint : aiBodyHints.entrySet()) {
                    String role = hint.getKey();
                    boolean alreadyPresent = richPHs.stream().anyMatch(p -> role.equalsIgnoreCase(p.getRole()));
                    if (!alreadyPresent) {
                        WordTemplateMetadata.PlaceholderInfo pi = new WordTemplateMetadata.PlaceholderInfo();
                        pi.setRole(role);
                        pi.setDetectionType("AI");
                        pi.setLocation("BODY");
                        pi.setParagraphIndex(hint.getValue());
                        String aiText = allPlaceholderTexts.getOrDefault("{{" + role + "}}", "");
                        pi.setOriginalText(aiText);
                        richPHs.add(pi);
                    }
                }
            }
            meta.setDetectedPlaceholders(richPHs);
            meta.setCharacterStyles(analysis.getCharacterStyleList());
            meta.setHeadingHierarchy(analysis.getHeadingHierarchyList());
            if (analysis.getTocInfo() != null) meta.setToc(analysis.getTocInfo());
            if (analysis.getRichHeaderInfo() != null) meta.setHeaderInfo(analysis.getRichHeaderInfo());
            if (analysis.getRichFooterInfo() != null) meta.setFooterInfo(analysis.getRichFooterInfo());

            String json = new ObjectMapper().writeValueAsString(meta);

            OrganisationDetails orgDetails = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
            if (orgDetails != null) {
                orgDetails.setWordTemplateAnalysisJson(json);
                orgDetails.setWordTemplateChecksum(checksum);
                organisationDetailsRepository.save(orgDetails);
                System.out.println("[AssessmentReporterWord] Template analysis persisted. Markers: "
                        + analysis.getNamedPlaceholders().keySet()
                        + ", Styles: " + analysis.getAvailableStyles().size()
                        + ", HasHeader: " + analysis.isHasHeader()
                        + ", HasFooter: " + analysis.isHasFooter());
            }
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] Failed to analyze/persist template: " + e.getMessage());
        }
    }

    /**
     * Uses AI to identify which body paragraphs serve as metadata placeholders (TITLE, DATE, AUTHOR, ORG).
     * Makes a single AI call and returns a map of role → contentIndex in mdp.getContent(), or null if unavailable.
     */
    private Map<String, Integer> identifyPlaceholdersWithAI(TemplateAnalysis analysis) {
        if (openAIUtil == null) return null;
        try {
            List<TemplateSection> structure = analysis.getTemplateStructure();
            if (structure == null || structure.isEmpty()) return null;
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are analyzing a Word document template to find where report metadata is placed.\n");
            prompt.append("Below are the non-empty body paragraphs (including text box content), listed as (contentIndex: [style] text).\n");
            prompt.append("Paragraphs labelled [TextBox] or [TextBox@header]/[TextBox@footer] are text boxes — these are VERY COMMON locations for title/date/author.\n");
            prompt.append("Your task: for each role, list ALL contentIndex values that could plausibly be that placeholder, ordered by confidence (best first).\n");
            prompt.append("A placeholder can appear in many forms:\n");
            prompt.append("  - Explicit marker: '{{TITLE}}', '{{DATE}}', '{{AUTHOR}}'\n");
            prompt.append("  - Short label (standalone word): 'Title', 'Date', 'Time', 'Author', 'Issuer'\n");
            prompt.append("  - Descriptive text: 'Report Title', 'Title of this report', 'Title of this document', 'Enter title here', 'Prepared by:'\n");
            prompt.append("  - Date format placeholder: 'xx/xx/2024', 'xx/xx/xx', '00/00/0000', 'dd.mm.yyyy', '[Date]'\n");
            prompt.append("  - German equivalents: 'Titel', 'Datum', 'Uhrzeit', 'Autor', 'Erstellt von', 'Organisation'\n");
            prompt.append("  - A paragraph with 'Title' style and non-content text is likely the title placeholder.\n");
            prompt.append("  - Text boxes in headers/covers VERY OFTEN contain metadata.\n");
            prompt.append("Use an empty array [] if no paragraph matches a role.\n");
            prompt.append("Return ONLY valid JSON, no explanation or markdown:\n");
            prompt.append("{\"TITLE\":[<n>,...],\"DATE\":[<n>,...],\"AUTHOR\":[<n>,...],\"ORG\":[<n>,...]}\n\n");
            prompt.append("BODY PARAGRAPHS:\n");
            List<String> textBoxLines = new ArrayList<>();
            for (TemplateSection section : structure) {
                String text = section.getText().length() > 100 ? section.getText().substring(0, 100) : section.getText();
                String style = section.getStyle();
                prompt.append(section.getSectionIndex()).append(": [").append(style).append("] ").append(text).append("\n");
                if (style != null && style.contains("TextBox")) textBoxLines.add(section.getSectionIndex() + ": [" + style + "] " + text);
            }
            if (!textBoxLines.isEmpty()) {
                prompt.append("\nTEXT BOX ELEMENTS (special attention — often contain metadata):\n");
                for (String tbl : textBoxLines) prompt.append("  ").append(tbl).append("\n");
            }
            String aiResponse = openAIUtil.askAI(prompt.toString(), true);
            if (aiResponse == null || aiResponse.isBlank()) return null;
            String json = aiResponse.trim();
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            json = json.substring(start, end + 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = new ObjectMapper().readValue(json, Map.class);
            // Parse candidates: value can be single int OR array of ints
            Map<String, List<Integer>> candidatesMap = new LinkedHashMap<>();
            Map<String, Integer> bestHints = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                String role = entry.getKey();
                List<Integer> candidates = new ArrayList<>();
                Object val = entry.getValue();
                if (val instanceof List) {
                    for (Object item : (List<?>) val) {
                        if (item instanceof Number && ((Number) item).intValue() >= 0) {
                            candidates.add(((Number) item).intValue());
                        }
                    }
                } else if (val instanceof Number && ((Number) val).intValue() >= 0) {
                    candidates.add(((Number) val).intValue());
                }
                candidatesMap.put(role, candidates);
                if (!candidates.isEmpty()) bestHints.put(role, candidates.get(0));
            }
            analysis.setAiCandidates(candidatesMap, structure);
            System.out.println("[AssessmentReporterWord] AI placeholder hints: " + bestHints);
            return bestHints;
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] AI placeholder identification failed: " + e.getMessage());
            return null;
        }
    }

    private void applyAiBodyHints(Map<String, Integer> aiHints, TemplateAnalysis templateAnalysis,
                                  WordprocessingMLPackage pkg) {
        try {
            Map<String, P> existingPH = templateAnalysis.getNamedPlaceholders();
            Set<String> foundRoles = new HashSet<>();
            if (existingPH.containsKey("{{TITLE}}") || existingPH.containsKey("{{REPORT_TITLE}}")) foundRoles.add("TITLE");
            if (existingPH.containsKey("{{DATE}}")  || existingPH.containsKey("{{CREATED_DATE}}"))  foundRoles.add("DATE");
            if (existingPH.containsKey("{{AUTHOR}}") || existingPH.containsKey("{{CREATED_BY}}"))   foundRoles.add("AUTHOR");
            if (existingPH.containsKey("{{ORG}}") || existingPH.containsKey("{{ORG_UNIT}}")
                    || existingPH.containsKey("{{ORGANISATION}}")) foundRoles.add("ORG");
            Map<String, String> roleToMarker = new LinkedHashMap<>();
            roleToMarker.put("TITLE",  "{{TITLE}}");
            roleToMarker.put("DATE",   "{{DATE}}");
            roleToMarker.put("AUTHOR", "{{AUTHOR}}");
            roleToMarker.put("ORG",    "{{ORG}}");
            List<Object> bodyContent = pkg.getMainDocumentPart().getContent();
            for (Map.Entry<String, Integer> hint : aiHints.entrySet()) {
                if (foundRoles.contains(hint.getKey())) continue;
                String marker = roleToMarker.get(hint.getKey());
                int idx = hint.getValue();
                if (marker == null || idx < 0 || idx >= bodyContent.size()) continue;
                Object obj = XmlUtils.unwrap(bodyContent.get(idx));
                if (obj instanceof P) {
                    existingPH.put(marker, (P) obj);
                    System.out.println("[AssessmentReporterWord] Applied AI hint: " + marker + " → body paragraph " + idx);
                }
            }
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] applyAiBodyHints failed: " + e.getMessage());
        }
    }

    /**
     * Applies user-saved candidate selections (roleToSelectedSectionIndex) to update
     * namedPlaceholders / namedHeaderFooterPlaceholders before replacement.
     * This overrides the initial fuzzy/AI detection with the user's explicit choice.
     * Handles both standard roles (TITLE, DATE, AUTHOR, ORG) and custom roles.
     */
    private void applyUserCandidateSelections(WordPlaceholderAttributeMapping phMapping,
                                              TemplateAnalysis templateAnalysis,
                                              WordprocessingMLPackage pkg) {
        Map<String, Integer> selectedIndices = phMapping.getRoleToSelectedSectionIndex();
        if (selectedIndices == null || selectedIndices.isEmpty()) return;
        List<TemplateSection> structure = templateAnalysis.getTemplateStructure();
        if (structure == null || structure.isEmpty()) return;
        Map<String, P> namedPH   = templateAnalysis.getNamedPlaceholders();
        Map<String, P> namedHFPH = templateAnalysis.getNamedHeaderFooterPlaceholders();
        // build sectionIndex → TemplateSection map for fast lookup
        Map<Integer, TemplateSection> secMap = new LinkedHashMap<>();
        for (TemplateSection ts : structure) secMap.put(ts.getSectionIndex(), ts);

        List<Object> bodyContent = pkg.getMainDocumentPart().getContent();

        for (Map.Entry<String, Integer> entry : selectedIndices.entrySet()) {
            String role = entry.getKey();
            int sectionIdx = entry.getValue();
            if (sectionIdx < 0) {
                // explicit SKIP: -1 means the user chose to not replace this role at all
                continue;
            }
            TemplateSection section = secMap.get(sectionIdx);
            if (section == null) continue;
            String marker = "{{" + role + "}}";
            String sectionText = section.getText() != null ? section.getText() : "";
            int contentIndex = section.getContentIndex();
            String sectionStyle = section.getStyle() != null ? section.getStyle() : "";

            boolean isHF = sectionStyle.startsWith("Header") || sectionStyle.startsWith("Footer")
                    || contentIndex < 0;

            if (!isHF) {
                // Body section — look up the paragraph at contentIndex
                if (contentIndex >= 0 && contentIndex < bodyContent.size()) {
                    Object obj = XmlUtils.unwrap(bodyContent.get(contentIndex));
                    if (obj instanceof P) {
                        namedPH.put(marker, (P) obj);
                        if (!sectionText.isEmpty()) templateAnalysis.getPlaceholderTexts().put(marker, sectionText);
                        namedHFPH.remove(marker);
                        System.out.println("[applyUserCandidateSelections] " + marker + " → body sectionIdx=" + sectionIdx + " contentIdx=" + contentIndex);
                    }
                }
            } else {
                // Header/Footer section — use the stored live JAXB reference (most reliable).
                // Fall back to text-based search only if the reference wasn't captured.
                P found = section.getParagraphRef();
                if (found == null) {
                    found = findParagraphInHFParts(pkg, sectionText);
                }
                if (found != null) {
                    namedHFPH.put(marker, found);
                    if (!sectionText.isEmpty()) templateAnalysis.getPlaceholderTexts().put(marker, sectionText);
                    namedPH.remove(marker);
                    System.out.println("[applyUserCandidateSelections] " + marker + " → H/F sectionIdx=" + sectionIdx
                            + " via " + (section.getParagraphRef() != null ? "direct-ref" : "text-match"));
                } else {
                    System.out.println("[applyUserCandidateSelections] " + marker + " → H/F sectionIdx=" + sectionIdx + " but no matching paragraph found");
                }
            }
        }
    }

    /** Extract plain-text from a P by concatenating all run Text values. */
    private static String extractRunTextFromP(P p) {
        StringBuilder sb = new StringBuilder();
        for (Object c : p.getContent()) {
            Object uw = XmlUtils.unwrap(c);
            if (uw instanceof R) {
                for (Object rc : ((R) uw).getContent()) {
                    Object ruw = XmlUtils.unwrap(rc);
                    if (ruw instanceof org.docx4j.wml.Text) sb.append(((org.docx4j.wml.Text) ruw).getValue());
                }
            }
        }
        return sb.toString();
    }

    /** Collect all P elements (including those inside Tbl cells) from a content list. */
    private static List<P> collectAllParagraphs(List<Object> content) {
        List<P> result = new ArrayList<>();
        for (Object c : content) {
            Object uw = XmlUtils.unwrap(c);
            if (uw instanceof P) {
                result.add((P) uw);
            } else if (uw instanceof org.docx4j.wml.Tbl) {
                for (Object row : ((org.docx4j.wml.Tbl) uw).getContent()) {
                    Object rowUw = XmlUtils.unwrap(row);
                    if (rowUw instanceof org.docx4j.wml.Tr) {
                        for (Object cell : ((org.docx4j.wml.Tr) rowUw).getContent()) {
                            Object cellUw = XmlUtils.unwrap(cell);
                            if (cellUw instanceof org.docx4j.wml.Tc) {
                                result.addAll(collectAllParagraphs(((org.docx4j.wml.Tc) cellUw).getContent()));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Find a paragraph in any header or footer part whose text (runs + textbox XML)
     * starts with the same characters as the given snippet.
     */
    private P findParagraphInHFParts(WordprocessingMLPackage pkg, String targetText) {
        if (targetText == null || targetText.isBlank()) return null;
        String snippet = targetText.length() > 40 ? targetText.substring(0, 40).toLowerCase() : targetText.toLowerCase();
        Set<String> seenUris = new java.util.LinkedHashSet<>();
        List<P> found = new ArrayList<>();
        try {
            // scan via parts registry
            for (java.util.Map.Entry<?, ?> e : pkg.getParts().getParts().entrySet()) {
                org.docx4j.openpackaging.parts.Part part =
                        (org.docx4j.openpackaging.parts.Part) e.getValue();
                String uri = part.getPartName() != null ? part.getPartName().toString() : "";
                if (!seenUris.add(uri)) continue;
                List<Object> hfContent = null;
                try {
                    if (part instanceof org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) {
                        org.docx4j.wml.Ftr ftr = ((org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) part).getJaxbElement();
                        if (ftr != null) hfContent = ftr.getContent();
                    } else if (part instanceof org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) {
                        org.docx4j.wml.Hdr hdr = ((org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) part).getJaxbElement();
                        if (hdr != null) hfContent = hdr.getContent();
                    }
                } catch (Exception ignored) {}
                if (hfContent == null) continue;
                for (P p : collectAllParagraphs(hfContent)) {
                    String txt = extractRunTextFromP(p).toLowerCase();
                    // also check textbox text via XML
                    try {
                        String xml = XmlUtils.marshaltoString(p, true);
                        if (xml != null) {
                            txt += " " + xml.replaceAll("<[^>]+>", " ").toLowerCase();
                        }
                    } catch (Exception ignored) {}
                    if (txt.contains(snippet)) { found.add(p); break; }
                }
                if (!found.isEmpty()) break;
            }
            // fallback: relationship-based scan
            if (found.isEmpty()) {
                org.docx4j.openpackaging.parts.relationships.RelationshipsPart relsPart =
                        pkg.getMainDocumentPart().getRelationshipsPart();
                if (relsPart != null) {
                    for (org.docx4j.relationships.Relationship rel :
                            relsPart.getRelationships().getRelationship()) {
                        String type = rel.getType();
                        if (!type.endsWith("/header") && !type.endsWith("/footer")) continue;
                        String target = rel.getTarget();
                        if (!target.startsWith("/")) target = "/word/" + target;
                        if (!seenUris.add(target)) continue;
                        try {
                            org.docx4j.openpackaging.parts.Part part2 =
                                    pkg.getParts().get(new org.docx4j.openpackaging.parts.PartName(target));
                            List<Object> hfContent2 = null;
                            if (part2 instanceof org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) {
                                org.docx4j.wml.Ftr ftr = ((org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) part2).getJaxbElement();
                                if (ftr != null) hfContent2 = ftr.getContent();
                            } else if (part2 instanceof org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) {
                                org.docx4j.wml.Hdr hdr = ((org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) part2).getJaxbElement();
                                if (hdr != null) hfContent2 = hdr.getContent();
                            }
                            if (hfContent2 == null) continue;
                            for (P p : collectAllParagraphs(hfContent2)) {
                                String txt = extractRunTextFromP(p).toLowerCase();
                                try {
                                    String xml = XmlUtils.marshaltoString(p, true);
                                    if (xml != null) txt += " " + xml.replaceAll("<[^>]+>", " ").toLowerCase();
                                } catch (Exception ignored) {}
                                if (txt.contains(snippet)) { found.add(p); break; }
                            }
                            if (!found.isEmpty()) break;
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return found.isEmpty() ? null : found.get(0);
    }

    private static final Pattern STYLE_TAG = Pattern.compile("(?is)<style\\s+([^>]*)>(.*?)</style>");

    /**
     * Parse text with optional inline <style ...>...</style> tags into runs.
     * Returns a list of RunData which include text and optional color/bold attributes.
     */
    private static class RunData {
        String text;
        String color; // #RRGGBB or null
        boolean bold;

        RunData(String text, String color, boolean bold) {
            this.text = text;
            this.color = color;
            this.bold = bold;
        }
    }

    private List<RunData> parseStyledRuns(String text) {
        List<RunData> runs = new ArrayList<>();
        if (text == null || text.isEmpty()) return runs;

        Matcher m = STYLE_TAG.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                String plain = text.substring(last, m.start());
                runs.add(new RunData(plain, null, false));
            }
            String attrs = m.group(1);
            String inner = m.group(2);
            String color = null;
            boolean bold = false;
            // parse attributes like color="#FF0000" bold="true"
            Matcher aM = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
            while (aM.find()) {
                String k = aM.group(1).toLowerCase(Locale.ROOT);
                String v = aM.group(2);
                if ("color".equals(k)) color = v;
                if ("bold".equals(k)) bold = "true".equalsIgnoreCase(v) || "1".equals(v);
            }
            runs.add(new RunData(inner, color, bold));
            last = m.end();
        }
        if (last < text.length()) {
            runs.add(new RunData(text.substring(last), null, false));
        }
        return runs;
    }

    /**
     * Splits an HTML management summary (using &lt;p&gt;, &lt;ul&gt;, &lt;li&gt;, &lt;strong&gt;) into Word paragraphs.
     * Each &lt;p&gt; becomes a paragraph; &lt;li&gt; items become bullet-style paragraphs.
     * &lt;strong&gt; content is rendered as bold runs.
     */
    private List<P> createHtmlParagraphs(ObjectFactory factory, String html, String styleName, TemplateAnalysis ta) {
        List<P> result = new ArrayList<>();
        if (html == null || html.isBlank()) return result;

        // Strip outer whitespace and normalise line endings
        String src = html.replace("\r\n", "\n").replace("\r", "\n").trim();

        // Extract <p>...</p> and <li>...</li> blocks
        java.util.regex.Pattern blockPattern = java.util.regex.Pattern.compile(
                "<(p|li)(?:\\s[^>]*)?>([\\s\\S]*?)</\\1>", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher bm = blockPattern.matcher(src);
        boolean found = false;
        while (bm.find()) {
            found = true;
            String tag = bm.group(1).toLowerCase(java.util.Locale.ROOT);
            String inner = bm.group(2).trim();
            // Convert <strong> to custom <style bold="true">
            inner = inner.replaceAll("(?i)<strong>([\\s\\S]*?)</strong>", "<style bold=\"true\">$1</style>");
            inner = inner.replaceAll("(?i)<em>([\\s\\S]*?)</em>", "<style bold=\"true\">$1</style>");
            // Strip any other remaining tags
            inner = inner.replaceAll("<(?!/?(style))[^>]+>", "");
            String prefix = "li".equals(tag) ? "• " : "";
            result.add(createParagraphWithStyle(factory, prefix + inner, styleName, ta));
        }
        if (!found) {
            // No block-level tags — treat whole string as single paragraph after stripping tags
            String plain = src.replaceAll("<[^>]+>", "");
            if (!plain.isBlank()) {
                result.add(createParagraphWithStyle(factory, plain.trim(), styleName, ta));
            }
        }
        return result;
    }

    /**
     * Create a paragraph and attempt to apply the template's style.
     * Use type-safe access to the StyleDefinitionsPart and apply paragraph and run properties.
     * Also processes inline styling tags parsed from AI.
     */
    private P createPageBreak(ObjectFactory factory) {
        P p = factory.createP();
        R r = factory.createR();
        org.docx4j.wml.Br br = factory.createBr();
        br.setType(org.docx4j.wml.STBrType.PAGE);
        r.getContent().add(br);
        p.getContent().add(r);
        return p;
    }

    private P createParagraphWithStyle(ObjectFactory factory, String text, String styleName, TemplateAnalysis ta) {
        P p = factory.createP();

        List<RunData> runs = parseStyledRuns(text);

        // determine style id to apply
        String styleId = ta != null ? ta.resolveStyle(styleName, styleName) : styleName;

        // gather style-level RPr if available
        RPr styleRPr = null;
        if (ta != null && ta.getStyleDefinitionsPart() != null) {
            StyleDefinitionsPart sdp = ta.getStyleDefinitionsPart();
            try {
                Style style = sdp.getStyleById(styleId);
                if (style != null && style.getRPr() != null) {
                    styleRPr = (RPr) XmlUtils.deepCopy(style.getRPr());
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // set paragraph style reference
        if (styleId != null && !styleId.isBlank()) {
            PPr pprRef = factory.createPPr();
            PStyle pstyle = factory.createPPrBasePStyle();
            pstyle.setVal(styleId);
            pprRef.setPStyle(pstyle);
            // If style has paragraph properties, prefer copying them
            if (ta != null && ta.getStyleIdToStyleMap().containsKey(styleId)) {
                Style s = ta.getStyleIdToStyleMap().get(styleId);
                if (s != null && s.getPPr() != null) {
                    try {
                        PPr copied = (PPr) XmlUtils.deepCopy(s.getPPr());
                        // ensure pStyle set
                        copied.setPStyle(pstyle);
                        p.setPPr(copied);
                    } catch (Exception e) {
                        p.setPPr(pprRef);
                    }
                } else {
                    p.setPPr(pprRef);
                }
            } else {
                p.setPPr(pprRef);
            }
        }

        // Create runs and apply run-level styling merged with styleRPr
        for (RunData rd : runs) {
            R run = factory.createR();
            Text t = factory.createText();
            t.setValue(rd.text);
            t.setSpace("preserve");
            run.getContent().add(t);

            RPr runRpr = null;
            if (styleRPr != null) {
                try {
                    runRpr = (RPr) XmlUtils.deepCopy(styleRPr);
                } catch (Exception e) {
                    runRpr = factory.createRPr();
                }
            } else {
                runRpr = factory.createRPr();
            }

            // apply inline attrs
            if (rd.bold) {
                runRpr.setB(new BooleanDefaultTrue());
            }
            if (rd.color != null && !rd.color.isBlank()) {
                try {
                    Color c = factory.createColor();
                    String val = rd.color.trim();
                    if (val.startsWith("#")) val = val.substring(1);
                    c.setVal(val);
                    runRpr.setColor(c);
                } catch (Exception e) {
                    // ignore
                }
            }

            // Only set RPr if it has properties (avoid null/empty)
            if (runRpr != null) run.setRPr(runRpr);

            p.getContent().add(run);
        }

        return p;
    }

    private Tc createTableCell(ObjectFactory factory, String text, String color, boolean bold) {
        Tc tc = factory.createTc();
        P p = factory.createP();
        // create runs with styling
        List<RunData> runs = parseStyledRuns(text);
        if (runs.isEmpty()) runs = Collections.singletonList(new RunData(text == null ? "" : text, color, bold));

        for (RunData rd : runs) {
            R r = factory.createR();
            Text t = factory.createText();
            t.setValue(rd.text == null ? "" : rd.text);
            t.setSpace("preserve");
            r.getContent().add(t);

            RPr rpr = factory.createRPr();
            if (rd.bold) rpr.setB(new BooleanDefaultTrue());
            String cval = rd.color != null ? rd.color : color;
            if (cval != null && !cval.isBlank()) {
                try {
                    Color c = factory.createColor();
                    String val = cval.trim();
                    if (val.startsWith("#")) val = val.substring(1);
                    c.setVal(val);
                    rpr.setColor(c);
                } catch (Exception e) {
                    // ignore
                }
            }
            if (rpr != null) r.setRPr(rpr);
            p.getContent().add(r);
        }

        tc.getContent().add(p);
        return tc;
    }

    /**
     * Analyzes Word template to extract styles, structure, and placeholders
     */
    private static class TemplateAnalyzer {
        private final WordprocessingMLPackage wordMLPackage;

        public TemplateAnalyzer(WordprocessingMLPackage wordMLPackage) {
            this.wordMLPackage = wordMLPackage;
        }

        public TemplateAnalysis analyze() {
            TemplateAnalysis analysis = new TemplateAnalysis();
            try {
                MainDocumentPart mdp = wordMLPackage.getMainDocumentPart();
                System.out.println("[TemplateAnalyzer] Starting template analysis...");

                // Extract all paragraph styles
                Set<String> styles = extractParagraphStyles(mdp, analysis);
                analysis.setAvailableStyles(styles);
                System.out.println("[TemplateAnalyzer] Found " + styles.size() + " paragraph styles: " + styles);

                // Extract template structure (headings, sections, content)
                List<TemplateSection> structure = extractTemplateStructure(mdp);
                analysis.setTemplateStructure(structure);
                System.out.println("[TemplateAnalyzer] Extracted " + structure.size() + " template sections");

                // Find all placeholder paragraphs (named markers + {{REPORT_CONTENT}})
                findAllPlaceholders(mdp, analysis);
                System.out.println("[TemplateAnalyzer] Named placeholders: " + analysis.getNamedPlaceholders().keySet()
                        + ", REPORT_CONTENT count: " + analysis.getPlaceholders().size());

                // Fuzzy detect placeholders for roles not found by exact markers
                detectFuzzyPlaceholders(mdp, analysis);
                System.out.println("[TemplateAnalyzer] After fuzzy detection: " + analysis.getNamedPlaceholders().keySet());

                // Scan table styles used in the document
                Set<String> tableStyles = scanTableStyles(mdp, analysis);
                analysis.setTableStyles(tableStyles);
                System.out.println("[TemplateAnalyzer] Table styles: " + tableStyles);

                // Scan headers and footers
                scanHeaderFooter(wordMLPackage, analysis);
                System.out.println("[TemplateAnalyzer] Header: " + analysis.isHasHeader()
                        + " (" + analysis.getHeaderText() + "), Footer: " + analysis.isHasFooter()
                        + " (" + analysis.getFooterText() + ")");

                // Rich scanning: SDTs, field instructions, character/heading styles, rich H/F
                scanSDTPlaceholders(mdp, analysis);
                scanFieldInstructions(mdp, analysis);
                extractCharacterAndHeadingStyles(analysis);
                enrichHeaderFooterInfo(wordMLPackage, analysis);

                System.out.println("[TemplateAnalyzer] Template analysis complete");

            } catch (Exception e) {
                System.err.println("[AssessmentReporterWord] Error analyzing template: " + e.getMessage());
            }
            return analysis;
        }

        private Set<String> extractParagraphStyles(MainDocumentPart mdp, TemplateAnalysis analysis) {
            Set<String> styles = new LinkedHashSet<>();
            try {
                System.out.println("[TemplateAnalyzer.extractParagraphStyles] Scanning document for paragraph styles...");

                // Map of styleId -> displayName (if available)
                Map<String, String> styleIdToName = new HashMap<>();
                Map<String, String> styleNameToId = new HashMap<>();
                Map<String, Style> styleIdToStyle = new HashMap<>();

                // Try to extract from StyleDefinitionsPart (type-safe)
                try {
                    StyleDefinitionsPart sdp = mdp.getStyleDefinitionsPart();
                    if (sdp != null) {
                        System.out.println("[TemplateAnalyzer.extractParagraphStyles] StyleDefinitionsPart found");
                        analysis.setStyleDefinitionsPart(sdp);
                        // sdp.getJaxbElement() is Styles; iterate type-safely
                        org.docx4j.wml.Styles stylesElement = sdp.getJaxbElement();
                        if (stylesElement != null && stylesElement.getStyle() != null) {
                            for (org.docx4j.wml.Style style : stylesElement.getStyle()) {
                                if (style.getStyleId() != null) {
                                    String styleId = style.getStyleId();
                                    styles.add(styleId);
                                    styleIdToStyle.put(styleId, style);
                                    String displayName = null;
                                    if (style.getName() != null && style.getName().getVal() != null) {
                                        displayName = style.getName().getVal();
                                        styles.add(displayName);
                                        styleIdToName.put(styleId, displayName);
                                        styleNameToId.put(displayName, styleId);
                                        // also add normalized keys for lookups
                                        styleNameToId.put(displayName.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""), styleId);
                                    }
                                }
                            }
                        }
                    }

                    // Store mappings in analysis so resolution can use them later
                    analysis.setStyleIdToNameMap(styleIdToName);
                    analysis.setStyleNameToIdMap(styleNameToId);
                    analysis.setStyleIdToStyleMap(styleIdToStyle);

                } catch (Exception e) {
                    System.out.println("[TemplateAnalyzer.extractParagraphStyles] Could not read StyleDefinitionsPart: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }

                // Also extract styles directly from paragraphs in the document
                List<Object> content = mdp.getContent();
                int paragraphCount = 0;
                for (Object c : content) {
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(c);
                    if (unwrapped instanceof P) {
                        P p = (P) unwrapped;
                        paragraphCount++;
                        String styleNameOrId = extractStyleFromParagraph(p);
                        if (styleNameOrId != null && !styleNameOrId.isBlank()) {
                            if (styles.add(styleNameOrId)) {
                                System.out.println("[TemplateAnalyzer.extractParagraphStyles]   Found new style from paragraph: '" + styleNameOrId + "'");
                            }
                        }
                    }
                }
                System.out.println("[TemplateAnalyzer.extractParagraphStyles] Processed " + paragraphCount + " paragraphs");

                // Add common default styles
                System.out.println("[TemplateAnalyzer.extractParagraphStyles] Adding default styles: Normal, Title, Heading1, Heading2, Heading3");
                styles.addAll(Arrays.asList("Normal", "Title", "Heading1", "Heading2", "Heading3"));
                System.out.println("[TemplateAnalyzer.extractParagraphStyles] Total unique styles available: " + styles.size());
            } catch (Exception e) {
                System.err.println("[AssessmentReporterWord] Error extracting styles: " + e.getMessage());
                e.printStackTrace();
            }
            return styles;
        }

        private List<TemplateSection> extractTemplateStructure(MainDocumentPart mdp) {
            List<TemplateSection> sections = new ArrayList<>();
            try {
                System.out.println("[TemplateAnalyzer.extractTemplateStructure] Extracting template structure and sections...");
                List<Object> content = mdp.getContent();
                int sectionCount = 0;
                int totalParagraphs = 0;
                int emptyParagraphs = 0;
                for (int contentIdx = 0; contentIdx < content.size(); contentIdx++) {
                    Object c = content.get(contentIdx);
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(c);
                    if (unwrapped instanceof P) {
                        P p = (P) unwrapped;
                        totalParagraphs++;
                        String style = extractStyleFromParagraph(p);
                        String finalStyle = style != null ? style : "Normal";
                        String text = extractTextFromParagraph(p);
                        boolean hadContent = false;
                        // Paragraph's own text — add as its own section
                        if (text != null && !text.isBlank()) {
                            sections.add(new TemplateSection(text, finalStyle, contentIdx));
                            sectionCount++;
                            String truncatedText = text.length() > 80 ? text.substring(0, 80) + "..." : text;
                            System.out.println("[TemplateAnalyzer.extractTemplateStructure]   Section " + sectionCount + " (style='" + finalStyle + "'): " + truncatedText);
                            hadContent = true;
                        }
                        // Each text box in this paragraph becomes its own separate section
                        List<String> tbTexts = extractTextBoxListFromParagraph(p);
                        for (String tbEntry : tbTexts) {
                            if (!tbEntry.isBlank()) {
                                String tbStyle = finalStyle + "/TextBox";
                                sections.add(new TemplateSection(tbEntry, tbStyle, contentIdx));
                                sectionCount++;
                                String trunc = tbEntry.length() > 80 ? tbEntry.substring(0, 80) + "..." : tbEntry;
                                System.out.println("[TemplateAnalyzer.extractTemplateStructure]   Section " + sectionCount + " TextBox (style='" + finalStyle + "'): " + trunc);
                                hadContent = true;
                            }
                        }
                        if (!hadContent) {
                            emptyParagraphs++;
                        }
                    } else {
                        // Handle standalone drawings / frames at body level (rare, but possible)
                        String tbText = extractTextBoxTextFromObject(unwrapped);
                        if (tbText != null && !tbText.isBlank()) {
                            sections.add(new TemplateSection(tbText, "TextBox", contentIdx));
                            sectionCount++;
                        }
                    }
                }
                System.out.println("[TemplateAnalyzer.extractTemplateStructure] Total sections extracted: " + sectionCount + " from " + totalParagraphs + " total paragraphs (" + emptyParagraphs + " were empty)");

                // Also scan headers and footers so their text appears as candidates in role dropdowns
                java.util.Set<String> seenHFUris = new java.util.HashSet<>();
                for (Object[] hfEntry : getHFContents()) {
                    @SuppressWarnings("unchecked")
                    List<Object> hfContent = (List<Object>) hfEntry[0];
                    boolean isHdr = (Boolean) hfEntry[1];
                    String hfUri   = (String) hfEntry[2];
                    if (!seenHFUris.add(hfUri)) continue; // deduplicate
                    String partType = isHdr ? "Header" : "Footer";
                    // Collect all paragraphs (including those inside table cells)
                    for (P hfp : AssessmentReporterWord.collectAllParagraphs(hfContent)) {
                        String pStyle = extractStyleFromParagraph(hfp);
                        String hfStyle = partType + (pStyle != null && !pStyle.equals("Normal") ? "/" + pStyle : "");
                        String ptxt = extractTextFromParagraph(hfp);
                        if (ptxt != null && !ptxt.isBlank()) {
                            TemplateSection ts = new TemplateSection(ptxt, hfStyle, -1);
                            ts.setParagraphRef(hfp); // store live JAXB reference for direct replacement
                            sections.add(ts);
                            sectionCount++;
                        }
                        for (String tbEntry : extractTextBoxListFromParagraph(hfp)) {
                            if (!tbEntry.isBlank()) {
                                // For text box sections the container P (hfp) is the replacement target
                                TemplateSection ts = new TemplateSection(tbEntry, hfStyle + "/TextBox", -1);
                                ts.setParagraphRef(hfp);
                                sections.add(ts);
                                sectionCount++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[AssessmentReporterWord] Error extracting template structure: " + e.getMessage());
                e.printStackTrace();
            }
            // Assign unique sequential sectionIndex to every section (body + H/F)
            for (int si = 0; si < sections.size(); si++) sections.get(si).setSectionIndex(si);
            return sections;
        }

        /**
         * Extract text from embedded text boxes (wps:txbx or v:textbox) inside a paragraph's drawing runs.
         */
        @SuppressWarnings("rawtypes")
        /**
         * Extract text that lives inside text boxes embedded in this paragraph.
         * Uses XML marshaling + scoped regex so we never mix in the paragraph's own run text.
         */
        private String extractTextBoxTextFromParagraph(P p) {
            try {
                String xml = org.docx4j.XmlUtils.marshaltoString(p, true);
                if (xml == null) return null;
                // Quick pre-check: only proceed if a text-box marker is present
                if (!xml.contains("txbxContent") && !xml.contains("v:textbox")) return null;
                String result = extractTextFromTxbxXml(xml);
                if (result != null && !result.isBlank()) {
                    System.out.println("[TemplateAnalyzer] TextBox text found: '" + result + "'");
                }
                return (result == null || result.isBlank()) ? null : result;
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] TextBox extraction failed: " + e.getMessage());
                return null;
            }
        }

        /**
         * Recursively extract text from a drawing/textbox/pict element.
         * Handles wps:txbx (modern drawing) and v:textbox (VML/legacy) elements.
         */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private String extractTextBoxTextFromObject(Object obj) {
            if (obj == null) return null;
            StringBuilder sb = new StringBuilder();
            try {
                // Modern text boxes: Drawing → wp:inline/anchor → mc:AlternateContent or wps:wsp → wps:txbx → w:txbxContent
                if (obj instanceof org.docx4j.wml.Drawing) {
                    org.docx4j.wml.Drawing drawing = (org.docx4j.wml.Drawing) obj;
                    for (Object item : drawing.getAnchorOrInline()) {
                        String txt = extractTextBoxTextFromObject(item);
                        if (txt != null) sb.append(txt);
                    }
                } else if (obj instanceof org.docx4j.dml.wordprocessingDrawing.Anchor
                        || obj instanceof org.docx4j.dml.wordprocessingDrawing.Inline) {
                    // Get graphic data via reflection to avoid tight coupling
                    try {
                        java.lang.reflect.Method getGraphic = obj.getClass().getMethod("getGraphic");
                        Object graphic = getGraphic.invoke(obj);
                        if (graphic != null) {
                            java.lang.reflect.Method getGraphicData = graphic.getClass().getMethod("getGraphicData");
                            Object graphicData = getGraphicData.invoke(graphic);
                            if (graphicData != null) {
                                java.lang.reflect.Method getAny = graphicData.getClass().getMethod("getAny");
                                List anyList = (List) getAny.invoke(graphicData);
                                for (Object any : anyList) {
                                    String txt = extractTextBoxTextFromObject(org.docx4j.XmlUtils.unwrap(any));
                                    if (txt != null) sb.append(txt);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    // Also try wsp directly via XML traversal
                    try {
                        String xml = org.docx4j.XmlUtils.marshaltoString(obj, true);
                        sb.append(extractTextFromTxbxXml(xml));
                    } catch (Exception ignored) {}
                } else {
                    // Fallback: marshal to XML string and extract txbxContent text
                    String className = obj.getClass().getName();
                    if (className.contains("wsp") || className.contains("txbx") || className.contains("TextBox")
                            || className.contains("pict") || className.contains("Pict")
                            || className.contains("Anchor") || className.contains("Inline")) {
                        try {
                            String xml = org.docx4j.XmlUtils.marshaltoString(obj, true);
                            sb.append(extractTextFromTxbxXml(xml));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            String result = sb.toString().trim();
            return result.isBlank() ? null : result;
        }

        /**
         * Extract plain text strictly from within text-box content sections of an XML string.
         * Scoped to w:txbxContent (modern drawing text boxes) and v:textbox (VML/legacy).
         * Does NOT extract regular paragraph run text to avoid double-counting.
         */
        private String extractTextFromTxbxXml(String xml) {
            if (xml == null || xml.isBlank()) return "";
            // Strip mc:Fallback sections FIRST — they are rendering fallbacks that
            // duplicate the same w:txbxContent already present in mc:Choice
            xml = xml.replaceAll("(?s)<mc:Fallback[^>]*>.*?</mc:Fallback\\s*>", "");
            StringBuilder sb = new StringBuilder();
            java.util.regex.Pattern WT = java.util.regex.Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>");
            // Modern text boxes: w:txbxContent
            java.util.regex.Matcher tbx = java.util.regex.Pattern.compile(
                    "<w:txbxContent[^>]*>(.*?)</w:txbxContent>",
                    java.util.regex.Pattern.DOTALL).matcher(xml);
            while (tbx.find()) {
                java.util.regex.Matcher wt = WT.matcher(tbx.group(1));
                while (wt.find()) {
                    String val = wt.group(1).trim();
                    if (!val.isBlank()) sb.append(val).append(" ");
                }
            }
            // VML legacy text boxes: v:textbox (only if no w:txbxContent was found inside them)
            java.util.regex.Matcher vbx = java.util.regex.Pattern.compile(
                    "<v:textbox[^>]*>(.*?)</v:textbox>",
                    java.util.regex.Pattern.DOTALL).matcher(xml);
            while (vbx.find()) {
                String vbxContent = vbx.group(1);
                // Skip if it already contains w:txbxContent (already captured above)
                if (vbxContent.contains("txbxContent")) continue;
                java.util.regex.Matcher wt = WT.matcher(vbxContent);
                while (wt.find()) {
                    String val = wt.group(1).trim();
                    if (!val.isBlank()) sb.append(val).append(" ");
                }
            }
            return sb.toString().trim();
        }

        /**
         * Like extractTextFromTxbxXml but returns one entry PER text box (not all concatenated).
         * Each w:txbxContent / v:textbox becomes a separate list entry.
         */
        private List<String> extractTextBoxListFromTxbxXml(String xml) {
            List<String> result = new ArrayList<>();
            if (xml == null || xml.isBlank()) return result;
            xml = xml.replaceAll("(?s)<mc:Fallback[^>]*>.*?</mc:Fallback\\s*>", "");
            java.util.regex.Pattern WT = java.util.regex.Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>");
            java.util.regex.Matcher tbx = java.util.regex.Pattern.compile(
                    "<w:txbxContent[^>]*>(.*?)</w:txbxContent>",
                    java.util.regex.Pattern.DOTALL).matcher(xml);
            while (tbx.find()) {
                StringBuilder sb = new StringBuilder();
                java.util.regex.Matcher wt = WT.matcher(tbx.group(1));
                while (wt.find()) {
                    String val = wt.group(1).trim();
                    if (!val.isBlank()) sb.append(val).append(" ");
                }
                String text = sb.toString().trim();
                if (!text.isBlank()) result.add(text);
            }
            java.util.regex.Matcher vbx = java.util.regex.Pattern.compile(
                    "<v:textbox[^>]*>(.*?)</v:textbox>",
                    java.util.regex.Pattern.DOTALL).matcher(xml);
            while (vbx.find()) {
                String vbxContent = vbx.group(1);
                if (vbxContent.contains("txbxContent")) continue;
                StringBuilder sb = new StringBuilder();
                java.util.regex.Matcher wt = WT.matcher(vbxContent);
                while (wt.find()) {
                    String val = wt.group(1).trim();
                    if (!val.isBlank()) sb.append(val).append(" ");
                }
                String text = sb.toString().trim();
                if (!text.isBlank()) result.add(text);
            }
            return result;
        }

        /**
         * Returns one text-box text entry per embedded text box in the paragraph (not concatenated).
         */
        private List<String> extractTextBoxListFromParagraph(P p) {
            try {
                String xml = org.docx4j.XmlUtils.marshaltoString(p, true);
                if (xml == null) return java.util.Collections.emptyList();
                if (!xml.contains("txbxContent") && !xml.contains("v:textbox")) return java.util.Collections.emptyList();
                return extractTextBoxListFromTxbxXml(xml);
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        }

        private String extractStyleFromParagraph(P p) {
            try {
                PPr ppr = p.getPPr();
                if (ppr != null) {
                    PStyle pstyle = ppr.getPStyle();
                    if (pstyle != null && pstyle.getVal() != null) {
                        return pstyle.getVal();
                    }
                }
            } catch (Exception e) {
                System.err.println("[TemplateReporterWord] Error extracting paragraph style: " + e.getMessage());
            }
            return null;
        }

        private List<P> findPlaceholderParagraphs(MainDocumentPart mdp) {
            List<P> result = new ArrayList<>();
            try {
                List<Object> content = mdp.getContent();
                for (Object c : content) {
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(c);
                    if (unwrapped instanceof P) {
                        P p = (P) unwrapped;
                        String txt = extractTextFromParagraph(p);
                        if (txt != null && txt.contains("{{REPORT_CONTENT}}")) {
                            result.add(p);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[AssessmentReporterWord] Error finding placeholder paragraphs: " + e.getMessage());
            }
            return result;
        }

        private String extractTextFromParagraph(P p) {
            StringBuilder sb = new StringBuilder();
            try {
                for (Object o : p.getContent()) {
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(o);
                    if (unwrapped instanceof R) {
                        R r = (R) unwrapped;
                        for (Object rc : r.getContent()) {
                            Object rcUnwrapped = org.docx4j.XmlUtils.unwrap(rc);
                            if (rcUnwrapped instanceof Text) {
                                Text t = (Text) rcUnwrapped;
                                if (t.getValue() != null) {
                                    sb.append(t.getValue());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[AssessmentReporterWord] Error extracting text from paragraph: " + e.getMessage());
            }
            return sb.toString();
        }

        private void findAllPlaceholders(MainDocumentPart mdp, TemplateAnalysis analysis) {
            List<P> reportContentPHs = new ArrayList<>();
            Map<String, P> namedPHs = new LinkedHashMap<>();
            Map<String, P> namedHFPHs = new LinkedHashMap<>();
            try {
                // Scan main body paragraphs
                for (Object c : mdp.getContent()) {
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(c);
                    if (unwrapped instanceof P) {
                        P p = (P) unwrapped;
                        String txt = extractTextFromParagraph(p);
                        String tbTxt = extractTextBoxTextFromParagraph(p);
                        if (tbTxt != null && !tbTxt.isBlank()) {
                            txt = (txt == null || txt.isBlank()) ? tbTxt : txt + " " + tbTxt;
                        }
                        if (txt == null || txt.isBlank()) continue;
                        for (String marker : KNOWN_MARKERS) {
                            if (txt.contains(marker)) {
                                if ("{{REPORT_CONTENT}}".equals(marker)) {
                                    reportContentPHs.add(p);
                                } else {
                                    namedPHs.put(marker, p);
                                    analysis.getPlaceholderTexts().put(marker, txt);
                                }
                                break;
                            }
                        }
                    }
                }
                // Scan header and footer parts for markers
                Set<String> seenHFUrisForPH = new java.util.HashSet<>();
                for (Object[] hfEntry : getHFContents()) {
                    @SuppressWarnings("unchecked")
                    List<Object> partContent = (List<Object>) hfEntry[0];
                    String hfUri = (String) hfEntry[2];
                    if (!seenHFUrisForPH.add(hfUri)) continue;
                    for (P p : AssessmentReporterWord.collectAllParagraphs(partContent)) {
                        String txt = extractTextFromParagraph(p);
                        String tbTxt = extractTextBoxTextFromParagraph(p);
                        if (tbTxt != null && !tbTxt.isBlank()) {
                            txt = (txt == null || txt.isBlank()) ? tbTxt : txt + " " + tbTxt;
                        }
                        if (txt == null || txt.isBlank()) continue;
                        for (String marker : KNOWN_MARKERS) {
                            if (txt.contains(marker) && !"{{REPORT_CONTENT}}".equals(marker)) {
                                namedHFPHs.put(marker, p);
                                analysis.getPlaceholderTexts().put(marker, txt);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] Error finding placeholders: " + e.getMessage());
            }
            analysis.setPlaceholders(reportContentPHs);
            analysis.setNamedPlaceholders(namedPHs);
            analysis.setNamedHeaderFooterPlaceholders(namedHFPHs);
        }

        private void detectFuzzyPlaceholders(MainDocumentPart mdp, TemplateAnalysis analysis) {
            Map<String, P> namedPH = analysis.getNamedPlaceholders();
            java.util.regex.Pattern DATE_FORMAT_RE = java.util.regex.Pattern.compile(
                    "\\b(\\d{2}[.\\-/]\\d{2}[.\\-/]\\d{2,4}" +        // 01/01/2024
                    "|[xX]{2}[.\\-/][xX]{2}[.\\-/](\\d{2,4}|[xX]{2,4})" + // xx/xx/2024 or xx/xx/xx
                    "|[0]{2}[.\\-/][0]{2}[.\\-/](\\d{2,4}|[0]{2,4})" +  // 00/00/2024 or 00/00/00
                    "|[xX]{2}[.\\-/][xX]{2}" +                             // xx/xx standalone
                    "|dd[.\\-/]mm[.\\-/](yy|yyyy)" +
                    "|\\[date\\]|\\[datum\\])\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
            List<String> titlePatterns  = Arrays.asList(
                    "title", "report title", "report name", "assessment title",
                    "title of this", "title of the", "document title",
                    "here goes the title", "your title", "enter title",
                    "titel", "berichtstitel", "bericht titel", "dokumenttitel");
            List<String> datePatterns   = Arrays.asList(
                    "date", "time", "report date", "created date", "creation date", "close date",
                    "here goes the date", "enter date",
                    "datum", "uhrzeit", "berichtsdatum", "erstellt am", "erstellungsdatum",
                    "berichtszeitraum", "zeitraum", "[date]", "[datum]");
            List<String> authorPatterns = Arrays.asList(
                    "author", "issuer", "reporter", "responsible", "prepared by", "created by",
                    "written by", "analyst", "enter author",
                    "autor", "erstellt von", "verfasser", "bearbeiter", "verantwortlich", "ersteller");
            List<String> orgPatterns    = Arrays.asList(
                    "organisation", "organization", "org unit", "department", "company", "client", "enter org",
                    "organisationseinheit", "abteilung", "firma", "unternehmen", "auftraggeber");
            boolean needTitle  = !namedPH.containsKey("{{TITLE}}")  && !namedPH.containsKey("{{REPORT_TITLE}}");
            boolean needDate   = !namedPH.containsKey("{{DATE}}")   && !namedPH.containsKey("{{CREATED_DATE}}");
            boolean needAuthor = !namedPH.containsKey("{{AUTHOR}}") && !namedPH.containsKey("{{CREATED_BY}}");
            boolean needOrg    = !namedPH.containsKey("{{ORG}}")    && !namedPH.containsKey("{{ORG_UNIT}}")
                               && !namedPH.containsKey("{{ORGANISATION}}");
            // Scan body paragraphs only when at least one role is still needed
            if (needTitle || needDate || needAuthor || needOrg) {
            try {
                List<Object> content = mdp.getContent();
                for (int i = 0; i < content.size(); i++) {
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(content.get(i));
                    if (!(unwrapped instanceof P)) continue;
                    P p = (P) unwrapped;
                    String paraText = extractTextFromParagraph(p);
                    String styleId = extractStyleFromParagraph(p);
                    // Build candidate list: paragraph's own text first, then each text box individually
                    List<String> candidates = new ArrayList<>();
                    if (paraText != null && !paraText.isBlank()) candidates.add(paraText);
                    candidates.addAll(extractTextBoxListFromParagraph(p));
                    if (candidates.isEmpty()) continue;
                    // Track which roles are still needed BEFORE processing this paragraph's candidates
                    boolean titleWas = needTitle, dateWas = needDate, authorWas = needAuthor, orgWas = needOrg;
                    for (String text : candidates) {
                        boolean hasMarker = false;
                        for (String m : KNOWN_MARKERS) { if (text.contains(m)) { hasMarker = true; break; } }
                        if (hasMarker) continue;
                        String lower = text.toLowerCase(java.util.Locale.ROOT).trim();
                        if (needTitle) {
                            boolean byStyle = styleId != null && styleId.toLowerCase(java.util.Locale.ROOT).contains("title");
                            if (byStyle || matchesAny(lower, titlePatterns)) {
                                namedPH.put("{{TITLE}}", p);
                                analysis.getPlaceholderTexts().put("{{TITLE}}", text);
                                needTitle = false;
                                System.out.println("[TemplateAnalyzer.detectFuzzy] TITLE at idx " + i + ": '" + text + "' style=" + styleId);
                                continue;
                            }
                        }
                        if (needDate && (matchesAny(lower, datePatterns) || DATE_FORMAT_RE.matcher(text).find())) {
                            namedPH.put("{{DATE}}", p);
                            analysis.getPlaceholderTexts().put("{{DATE}}", text);
                            needDate = false;
                            System.out.println("[TemplateAnalyzer.detectFuzzy] DATE at idx " + i + ": '" + text + "'");
                            continue;
                        }
                        if (needAuthor && matchesAny(lower, authorPatterns)) {
                            namedPH.put("{{AUTHOR}}", p);
                            analysis.getPlaceholderTexts().put("{{AUTHOR}}", text);
                            needAuthor = false;
                            System.out.println("[TemplateAnalyzer.detectFuzzy] AUTHOR at idx " + i + ": '" + text + "'");
                            continue;
                        }
                        if (needOrg && matchesAny(lower, orgPatterns)) {
                            namedPH.put("{{ORG}}", p);
                            analysis.getPlaceholderTexts().put("{{ORG}}", text);
                            needOrg = false;
                            System.out.println("[TemplateAnalyzer.detectFuzzy] ORG at idx " + i + ": '" + text + "'");
                            continue;
                        }
                    }
                    // When a role is matched, expose same-style TextBox + all Header/Footer sections
                    // from the template structure as chooser candidates in the UI dropdown.
                    if ((titleWas && !needTitle) || (dateWas && !needDate)
                            || (authorWas && !needAuthor) || (orgWas && !needOrg)) {
                        java.util.Set<Integer> poolSeen = new java.util.LinkedHashSet<>();
                        List<Map<String, Object>> pool = new ArrayList<>();
                        // 1. Same-style body TextBox sections (highest-relevance candidates)
                        if (styleId != null) {
                            for (TemplateSection ts : analysis.getTemplateStructure()) {
                                if (ts.getStyle() != null
                                        && ts.getStyle().startsWith(styleId)
                                        && ts.getStyle().contains("TextBox")
                                        && poolSeen.add(ts.getSectionIndex())) {
                                    Map<String, Object> cm = new java.util.LinkedHashMap<>();
                                    cm.put("index", ts.getSectionIndex());
                                    String txt = ts.getText();
                                    cm.put("text", txt != null && txt.length() > 100 ? txt.substring(0, 100) + "..." : txt != null ? txt : "");
                                    cm.put("style", ts.getStyle());
                                    pool.add(cm);
                                }
                            }
                        }
                        // 2. All Header/Footer sections (second group)
                        for (TemplateSection ts : analysis.getTemplateStructure()) {
                            if (ts.getStyle() != null
                                    && (ts.getStyle().startsWith("Header") || ts.getStyle().startsWith("Footer"))
                                    && poolSeen.add(ts.getSectionIndex())) {
                                Map<String, Object> cm = new java.util.LinkedHashMap<>();
                                cm.put("index", ts.getSectionIndex());
                                String txt = ts.getText();
                                cm.put("text", txt != null && txt.length() > 100 ? txt.substring(0, 100) + "..." : txt != null ? txt : "");
                                cm.put("style", ts.getStyle());
                                pool.add(cm);
                            }
                        }
                        // 3. Fallback: current paragraph's own text(s) if no pool built
                        if (pool.isEmpty()) {
                            for (String ct : candidates) {
                                if (ct == null || ct.isBlank()) continue;
                                Map<String, Object> cm = new java.util.LinkedHashMap<>();
                                cm.put("index", i);
                                cm.put("text", ct.length() > 100 ? ct.substring(0, 100) + "..." : ct);
                                cm.put("style", styleId != null ? styleId : "Normal");
                                pool.add(cm);
                            }
                        }
                        if (!pool.isEmpty()) {
                            if (titleWas && !needTitle && !analysis.getAiCandidateDetails().containsKey("TITLE"))
                                analysis.getAiCandidateDetails().put("TITLE", new ArrayList<>(pool));
                            if (dateWas && !needDate && !analysis.getAiCandidateDetails().containsKey("DATE"))
                                analysis.getAiCandidateDetails().put("DATE", new ArrayList<>(pool));
                            if (authorWas && !needAuthor && !analysis.getAiCandidateDetails().containsKey("AUTHOR"))
                                analysis.getAiCandidateDetails().put("AUTHOR", new ArrayList<>(pool));
                            if (orgWas && !needOrg && !analysis.getAiCandidateDetails().containsKey("ORG"))
                                analysis.getAiCandidateDetails().put("ORG", new ArrayList<>(pool));
                        }
                    }
                    if (!needTitle && !needDate && !needAuthor && !needOrg) break;
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] detectFuzzyPlaceholders body error: " + e.getMessage());
            }
            // Scan headers and footers for fuzzy matches (for roles still needed)
            // and also always build H/F candidate pool for all roles (so user can choose H/F override)
            try {
                Map<String, P> namedHFPH = analysis.getNamedHeaderFooterPlaceholders();
                Set<String> seenHFUrisFuzzy = new java.util.HashSet<>();
                for (Object[] hfEntry : getHFContents()) {
                    @SuppressWarnings("unchecked")
                    List<Object> partContent = (List<Object>) hfEntry[0];
                    String hfUri = (String) hfEntry[2];
                    if (!seenHFUrisFuzzy.add(hfUri)) continue;
                    for (P p : AssessmentReporterWord.collectAllParagraphs(partContent)) {
                        String hfParaText = extractTextFromParagraph(p);
                        List<String> hfCandidates = new ArrayList<>();
                        if (hfParaText != null && !hfParaText.isBlank()) hfCandidates.add(hfParaText);
                        hfCandidates.addAll(extractTextBoxListFromParagraph(p));
                        for (String text : hfCandidates) {
                            boolean hasMarker = false;
                            for (String m : KNOWN_MARKERS) { if (text.contains(m)) { hasMarker = true; break; } }
                            if (hasMarker) continue;
                            String lower = text.toLowerCase(java.util.Locale.ROOT).trim();
                            if (needDate && (matchesAny(lower, datePatterns) || DATE_FORMAT_RE.matcher(text).find())) {
                                namedHFPH.put("{{DATE}}", p);
                                analysis.getPlaceholderTexts().put("{{DATE}}", text);
                                needDate = false;
                                System.out.println("[TemplateAnalyzer.detectFuzzy] DATE in H/F: '" + text + "'");
                            } else if (needAuthor && matchesAny(lower, authorPatterns)) {
                                namedHFPH.put("{{AUTHOR}}", p);
                                analysis.getPlaceholderTexts().put("{{AUTHOR}}", text);
                                needAuthor = false;
                                System.out.println("[TemplateAnalyzer.detectFuzzy] AUTHOR in H/F: '" + text + "'");
                            } else if (needOrg && matchesAny(lower, orgPatterns)) {
                                namedHFPH.put("{{ORG}}", p);
                                analysis.getPlaceholderTexts().put("{{ORG}}", text);
                                needOrg = false;
                                System.out.println("[TemplateAnalyzer.detectFuzzy] ORG in H/F: '" + text + "'");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] detectFuzzyPlaceholders H/F error: " + e.getMessage());
            }
            } // end if (needTitle || needDate || needAuthor || needOrg)
            // Always add H/F sections to every role's candidate pool (allows user to override body detection with H/F)
            try {
                List<Map<String, Object>> allHFCandidates = new ArrayList<>();
                for (TemplateSection ts : analysis.getTemplateStructure()) {
                    if (ts.getStyle() == null) continue;
                    if (!ts.getStyle().startsWith("Header") && !ts.getStyle().startsWith("Footer")) continue;
                    if (ts.getText() == null || ts.getText().isBlank()) continue;
                    Map<String, Object> cm = new java.util.LinkedHashMap<>();
                    cm.put("index", ts.getSectionIndex());
                    String txt = ts.getText();
                    cm.put("text", txt.length() > 100 ? txt.substring(0, 100) + "..." : txt);
                    cm.put("style", ts.getStyle());
                    allHFCandidates.add(cm);
                }
                if (!allHFCandidates.isEmpty()) {
                    for (String role : Arrays.asList("TITLE", "DATE", "AUTHOR", "ORG")) {
                        List<Map<String, Object>> existing = analysis.getAiCandidateDetails().get(role);
                        if (existing == null) {
                            analysis.getAiCandidateDetails().put(role, new ArrayList<>(allHFCandidates));
                        } else {
                            Set<Integer> existingIdxs = new java.util.HashSet<>();
                            for (Map<String, Object> cm : existing) { if (cm.get("index") instanceof Integer) existingIdxs.add((Integer) cm.get("index")); }
                            for (Map<String, Object> hfc : allHFCandidates) {
                                if (existingIdxs.add((Integer) hfc.get("index"))) existing.add(hfc);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        private boolean matchesAny(String text, List<String> patterns) {
            for (String pat : patterns) { if (text.contains(pat)) return true; }
            return false;
        }

        /**
         * Returns all header/footer content lists with metadata: [{content, isHeader, uri}, ...].
         * Uses the parts registry first, then relationship-based fallback so no H/F part is missed.
         */
        private List<Object[]> getHFContents() {
            List<Object[]> result = new ArrayList<>();
            Set<String> seenUris = new java.util.HashSet<>();
            // Primary: flat parts registry
            for (java.util.Map.Entry<?, ?> e : wordMLPackage.getParts().getParts().entrySet()) {
                org.docx4j.openpackaging.parts.Part part = (org.docx4j.openpackaging.parts.Part) e.getValue();
                boolean isFtr = part instanceof org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
                boolean isHdr = part instanceof org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
                if (!isFtr && !isHdr) continue;
                String uri = part.getPartName() != null ? part.getPartName().toString() : "";
                if (!seenUris.add(uri)) continue;
                List<Object> content = null;
                try {
                    if (isFtr) {
                        org.docx4j.wml.Ftr ftr = ((org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) part).getJaxbElement();
                        if (ftr != null) content = ftr.getContent();
                    } else {
                        org.docx4j.wml.Hdr hdr = ((org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) part).getJaxbElement();
                        if (hdr != null) content = hdr.getContent();
                    }
                } catch (Exception ignored) {}
                if (content != null) result.add(new Object[]{content, isHdr, uri});
            }
            // Fallback: relationship-based (catches parts not yet in the flat registry)
            try {
                org.docx4j.openpackaging.parts.relationships.RelationshipsPart relsPart =
                        wordMLPackage.getMainDocumentPart().getRelationshipsPart();
                if (relsPart != null) {
                    for (org.docx4j.relationships.Relationship rel :
                            relsPart.getRelationships().getRelationship()) {
                        String type = rel.getType();
                        boolean isHdr2 = type.endsWith("/header");
                        boolean isFtr2 = type.endsWith("/footer");
                        if (!isHdr2 && !isFtr2) continue;
                        String target = rel.getTarget();
                        if (!target.startsWith("/")) target = "/word/" + target;
                        if (!seenUris.add(target)) continue;
                        try {
                            org.docx4j.openpackaging.parts.Part part2 =
                                    wordMLPackage.getParts().get(new org.docx4j.openpackaging.parts.PartName(target));
                            if (part2 == null) continue;
                            List<Object> content2 = null;
                            if (part2 instanceof org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) {
                                org.docx4j.wml.Ftr ftr = ((org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) part2).getJaxbElement();
                                if (ftr != null) content2 = ftr.getContent();
                            } else if (part2 instanceof org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) {
                                org.docx4j.wml.Hdr hdr = ((org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) part2).getJaxbElement();
                                if (hdr != null) content2 = hdr.getContent();
                            }
                            if (content2 != null) result.add(new Object[]{content2, isHdr2, target});
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            return result;
        }

        private Set<String> scanTableStyles(MainDocumentPart mdp, TemplateAnalysis analysis) {
            Set<String> tableStyles = new LinkedHashSet<>();
            try {
                // Read all table-type styles from StyleDefinitionsPart (most reliable source)
                StyleDefinitionsPart sdp = analysis.getStyleDefinitionsPart();
                if (sdp != null) {
                    org.docx4j.wml.Styles stylesElement = sdp.getJaxbElement();
                    if (stylesElement != null && stylesElement.getStyle() != null) {
                        for (Style style : stylesElement.getStyle()) {
                            if ("table".equals(style.getType())) {
                                String styleId = style.getStyleId();
                                if (styleId != null) {
                                    tableStyles.add(styleId);
                                    System.out.println("[TemplateAnalyzer.scanTableStyles] Found table style: '" + styleId + "'");
                                }
                                if (styleId != null && style.getName() != null && style.getName().getVal() != null) {
                                    analysis.getTableStyleNameMap().put(styleId, style.getName().getVal());
                                }
                            }
                        }
                    }
                }
                // Also scan body content for tables (including nested tables)
                for (Object c : mdp.getContent()) {
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(c);
                    if (unwrapped instanceof Tbl) {
                        extractTblStyle((Tbl) unwrapped, tableStyles);
                    }
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] Error scanning table styles: " + e.getMessage());
            }
            return tableStyles;
        }

        private void extractTblStyle(Tbl tbl, Set<String> tableStyles) {
            if (tbl.getTblPr() != null && tbl.getTblPr().getTblStyle() != null) {
                String styleVal = tbl.getTblPr().getTblStyle().getVal();
                if (styleVal != null) tableStyles.add(styleVal);
            }
            for (Object row : tbl.getContent()) {
                Object uwRow = org.docx4j.XmlUtils.unwrap(row);
                if (uwRow instanceof Tr) {
                    for (Object cell : ((Tr) uwRow).getContent()) {
                        Object uwCell = org.docx4j.XmlUtils.unwrap(cell);
                        if (uwCell instanceof Tc) {
                            for (Object cellContent : ((Tc) uwCell).getContent()) {
                                Object uwContent = org.docx4j.XmlUtils.unwrap(cellContent);
                                if (uwContent instanceof Tbl) extractTblStyle((Tbl) uwContent, tableStyles);
                            }
                        }
                    }
                }
            }
        }

        /** Scan SDT (content controls) in body for tag/alias information. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void scanSDTPlaceholders(MainDocumentPart mdp, TemplateAnalysis analysis) {
            try {
                List<Object> content = mdp.getContent();
                for (int idx = 0; idx < content.size(); idx++) {
                    Object uw = org.docx4j.XmlUtils.unwrap(content.get(idx));
                    if (uw instanceof org.docx4j.wml.SdtBlock) {
                        org.docx4j.wml.SdtBlock sdt = (org.docx4j.wml.SdtBlock) uw;
                        String tag = null, alias = null;
                        if (sdt.getSdtPr() != null) {
                            for (Object item : sdt.getSdtPr().getRPrOrAliasOrLock()) {
                                Object uitem = org.docx4j.XmlUtils.unwrap(item);
                                if (uitem instanceof org.docx4j.wml.Tag) {
                                    tag = ((org.docx4j.wml.Tag) uitem).getVal();
                                } else if (item instanceof jakarta.xml.bind.JAXBElement) {
                                    jakarta.xml.bind.JAXBElement<?> je = (jakarta.xml.bind.JAXBElement<?>) item;
                                    if ("alias".equals(je.getName().getLocalPart())) {
                                        Object aliasVal = je.getValue();
                                        try {
                                            alias = (String) aliasVal.getClass().getMethod("getVal").invoke(aliasVal);
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                        // Extract text content from SDT
                        StringBuilder sdtText = new StringBuilder();
                        if (sdt.getSdtContent() != null) {
                            for (Object sc : sdt.getSdtContent().getContent()) {
                                Object usc = org.docx4j.XmlUtils.unwrap(sc);
                                if (usc instanceof P) sdtText.append(extractTextFromParagraph((P) usc));
                            }
                        }
                        String role = inferRoleFromText(tag != null ? tag : alias);
                        if (role == null) role = inferRoleFromText(sdtText.toString());
                        WordTemplateMetadata.PlaceholderInfo pi = new WordTemplateMetadata.PlaceholderInfo();
                        pi.setRole(role != null ? role : (tag != null ? tag.toUpperCase() : "UNKNOWN"));
                        pi.setDetectionType("SDT");
                        pi.setLocation("BODY");
                        pi.setOriginalText(sdtText.toString().trim());
                        pi.setSdtTag(tag);
                        pi.setSdtAlias(alias);
                        pi.setParagraphIndex(idx);
                        analysis.getRichPlaceholders().add(pi);
                        System.out.println("[TemplateAnalyzer.scanSDT] Found SDT tag='" + tag + "' alias='" + alias + "' text='" + sdtText.toString().trim() + "'");
                    }
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] scanSDTPlaceholders error: " + e.getMessage());
            }
        }

        /** Scan all body runs for instrText (field instructions). */
        private void scanFieldInstructions(MainDocumentPart mdp, TemplateAnalysis analysis) {
            try {
                List<Object> content = mdp.getContent();
                for (int idx = 0; idx < content.size(); idx++) {
                    Object uw = org.docx4j.XmlUtils.unwrap(content.get(idx));
                    if (uw instanceof P) {
                        P p = (P) uw;
                        for (Object po : p.getContent()) {
                            Object puw = org.docx4j.XmlUtils.unwrap(po);
                            if (puw instanceof R) {
                                R r = (R) puw;
                                for (Object rc : r.getContent()) {
                                    if (rc instanceof jakarta.xml.bind.JAXBElement) {
                                        jakarta.xml.bind.JAXBElement<?> je = (jakarta.xml.bind.JAXBElement<?>) rc;
                                        if ("instrText".equals(je.getName().getLocalPart()) && je.getValue() instanceof org.docx4j.wml.Text) {
                                            String instr = ((org.docx4j.wml.Text) je.getValue()).getValue();
                                            if (instr == null || instr.isBlank()) continue;
                                            instr = instr.trim();
                                            // TOC detection
                                            if (instr.toUpperCase().startsWith("TOC")) {
                                                WordTemplateMetadata.TocInfo toc = new WordTemplateMetadata.TocInfo();
                                                toc.setPresent(true);
                                                toc.setFieldCode(instr);
                                                toc.setParagraphIndex(idx);
                                                // parse \o "1-3" heading levels
                                                java.util.regex.Matcher mLvl = java.util.regex.Pattern.compile("\\\\o\\s+\"([^\"]+)\"").matcher(instr);
                                                if (mLvl.find()) toc.setHeadingLevels(mLvl.group(1));
                                                analysis.setTocInfo(toc);
                                                System.out.println("[TemplateAnalyzer.scanFields] TOC found: " + instr);
                                            }
                                            // DOCPROPERTY / AUTHOR / DATE / TITLE field detection
                                            String upperInstr = instr.toUpperCase();
                                            String role = null;
                                            if (upperInstr.startsWith("DOCPROPERTY") || upperInstr.startsWith("DATE")) role = "DATE";
                                            if (upperInstr.contains("AUTHOR") || upperInstr.contains("CREATOR")) role = "AUTHOR";
                                            if (upperInstr.contains("TITLE")) role = "TITLE";
                                            if (role != null) {
                                                WordTemplateMetadata.PlaceholderInfo pi = new WordTemplateMetadata.PlaceholderInfo();
                                                pi.setRole(role);
                                                pi.setDetectionType("FIELD");
                                                pi.setLocation("BODY");
                                                pi.setFieldCode(instr);
                                                pi.setParagraphIndex(idx);
                                                analysis.getRichPlaceholders().add(pi);
                                                System.out.println("[TemplateAnalyzer.scanFields] Field placeholder role=" + role + " instr=" + instr);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] scanFieldInstructions error: " + e.getMessage());
            }
        }

        /** Extract character styles and heading hierarchy from styles. */
        private void extractCharacterAndHeadingStyles(TemplateAnalysis analysis) {
            try {
                List<WordTemplateMetadata.StyleInfo> charStyles = new ArrayList<>();
                List<WordTemplateMetadata.HeadingInfo> headings = new ArrayList<>();
                for (Map.Entry<String, Style> entry : analysis.getStyleIdToStyleMap().entrySet()) {
                    String sId = entry.getKey();
                    Style style = entry.getValue();
                    String type = style.getType();
                    String displayName = style.getName() != null ? style.getName().getVal() : sId;
                    if ("character".equals(type)) {
                        WordTemplateMetadata.StyleInfo si = new WordTemplateMetadata.StyleInfo();
                        si.setId(sId);
                        si.setDisplayName(displayName);
                        si.setType("character");
                        charStyles.add(si);
                    } else if ("paragraph".equals(type) || type == null) {
                        // Check for outline level
                        if (style.getPPr() != null && style.getPPr().getOutlineLvl() != null) {
                            int outlineLvl = style.getPPr().getOutlineLvl().getVal() != null
                                    ? style.getPPr().getOutlineLvl().getVal().intValue() : -1;
                            if (outlineLvl >= 0 && outlineLvl <= 8) {
                                WordTemplateMetadata.HeadingInfo hi = new WordTemplateMetadata.HeadingInfo();
                                hi.setLevel(outlineLvl + 1);
                                hi.setStyleId(sId);
                                hi.setDisplayName(displayName);
                                hi.setOutlineLevel(outlineLvl);
                                headings.add(hi);
                                System.out.println("[TemplateAnalyzer.extractHeadings] Heading level " + (outlineLvl+1) + " style: " + sId + " / " + displayName);
                            }
                        }
                    }
                }
                headings.sort(Comparator.comparingInt(WordTemplateMetadata.HeadingInfo::getLevel));
                analysis.setCharacterStyleList(charStyles);
                analysis.setHeadingHierarchyList(headings);
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] extractCharacterAndHeadingStyles error: " + e.getMessage());
            }
        }

        /** Enrich header/footer info with field codes, page numbers, dates, classification. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void enrichHeaderFooterInfo(WordprocessingMLPackage pkg, TemplateAnalysis analysis) {
            Pattern classificationPattern = Pattern.compile(
                "\\b(confidential|restricted|public|internal|secret|classified|vertraulich|öffentlich|intern)\\b",
                Pattern.CASE_INSENSITIVE);
            try {
                for (java.util.Map.Entry entry : pkg.getParts().getParts().entrySet()) {
                    org.docx4j.openpackaging.parts.Part part = (org.docx4j.openpackaging.parts.Part) entry.getValue();
                    boolean isFooter = part instanceof org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
                    boolean isHeader = part instanceof org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
                    if (!isFooter && !isHeader) continue;
                    List<Object> hfContent = null;
                    try {
                        if (isFooter) {
                            org.docx4j.wml.Ftr ftr = ((org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) part).getJaxbElement();
                            if (ftr != null) hfContent = ftr.getContent();
                        } else {
                            org.docx4j.wml.Hdr hdr = ((org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) part).getJaxbElement();
                            if (hdr != null) hfContent = hdr.getContent();
                        }
                    } catch (Exception ignored) {}
                    if (hfContent == null) continue;
                    WordTemplateMetadata.HFInfo info = new WordTemplateMetadata.HFInfo();
                    info.setPresent(true);
                    StringBuilder fullText = new StringBuilder();
                    List<String> fieldCodes = new ArrayList<>();
                    for (Object c : hfContent) {
                        Object uw = org.docx4j.XmlUtils.unwrap(c);
                        if (uw instanceof P) {
                            P p = (P) uw;
                            String txt = extractTextFromParagraph(p);
                            if (txt != null && !txt.isBlank()) fullText.append(txt).append(" ");
                            // Also extract text from text boxes in this paragraph
                            String tbText = extractTextBoxTextFromParagraph(p);
                            if (tbText != null && !tbText.isBlank()) fullText.append(tbText).append(" ");
                            // scan instrText in runs
                            for (Object po : p.getContent()) {
                                Object puw = org.docx4j.XmlUtils.unwrap(po);
                                if (puw instanceof R) {
                                    R r = (R) puw;
                                    for (Object rc : r.getContent()) {
                                        if (rc instanceof jakarta.xml.bind.JAXBElement) {
                                            jakarta.xml.bind.JAXBElement<?> je = (jakarta.xml.bind.JAXBElement<?>) rc;
                                            if ("instrText".equals(je.getName().getLocalPart()) && je.getValue() instanceof org.docx4j.wml.Text) {
                                                String instr = ((org.docx4j.wml.Text) je.getValue()).getValue();
                                                if (instr != null && !instr.isBlank()) {
                                                    fieldCodes.add(instr.trim());
                                                    String up = instr.trim().toUpperCase();
                                                    if (up.startsWith("PAGE") || up.startsWith("NUMPAGES")) info.setHasPageNumber(true);
                                                    if (up.startsWith("DATE") || up.startsWith("CREATEDATE") || up.startsWith("SAVEDATE")) info.setHasDate(true);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    info.setFullText(fullText.toString().trim());
                    info.setFieldCodes(fieldCodes);
                    if (classificationPattern.matcher(info.getFullText()).find()) info.setHasClassification(true);
                    if (isFooter) analysis.setRichFooterInfo(info);
                    else analysis.setRichHeaderInfo(info);
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] enrichHeaderFooterInfo error: " + e.getMessage());
            }
        }

        /** Map a text snippet to a placeholder role. */
        private static final java.util.regex.Pattern INFER_DATE_RE = java.util.regex.Pattern.compile(
            "[xX0]{2}[.\\-/][xX0]{2}[.\\-/]" +      // xx/xx/ or 00/00/
            "|dd[.\\-/]mm" +                           // dd/mm
            "|\\[date\\]|\\[datum\\]" +               // [Date]
            "|\\d{2}[.\\-/]\\d{2}[.\\-/]\\d{2,4}",  // 01/01/2024
            java.util.regex.Pattern.CASE_INSENSITIVE);

        private String inferRoleFromText(String text) {
            if (text == null) return null;
            String t = text.toLowerCase(java.util.Locale.ROOT).trim();
            if (t.contains("title") || t.contains("titel") || t.contains("berichtstitel")
                    || t.contains("document title") || t.contains("report title")) return "TITLE";
            if (t.contains("date") || t.contains("datum") || t.contains("time") || t.contains("uhrzeit")
                    || INFER_DATE_RE.matcher(text).find()) return "DATE";
            if (t.contains("author") || t.contains("creator") || t.contains("issuer") || t.contains("erstellt")
                    || t.contains("verantwortlich") || t.contains("prepared by")) return "AUTHOR";
            if (t.contains("org") || t.contains("organisation") || t.contains("department")) return "ORG";
            if (t.contains("version") || t.contains("revision")) return "VERSION";
            if (t.contains("classif") || t.contains("vertraulich") || t.contains("confidential")) return "CLASSIFICATION";
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void scanHeaderFooter(WordprocessingMLPackage pkg, TemplateAnalysis analysis) {
            try {
                for (java.util.Map.Entry entry : pkg.getParts().getParts().entrySet()) {
                    org.docx4j.openpackaging.parts.Part part =
                            (org.docx4j.openpackaging.parts.Part) entry.getValue();
                    if (part instanceof org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) {
                        org.docx4j.openpackaging.parts.WordprocessingML.FooterPart fp =
                                (org.docx4j.openpackaging.parts.WordprocessingML.FooterPart) part;
                        try {
                            org.docx4j.wml.Ftr ftr = fp.getJaxbElement();
                            if (ftr != null) {
                                StringBuilder sb = new StringBuilder();
                                for (Object o : ftr.getContent()) {
                                    Object uw = org.docx4j.XmlUtils.unwrap(o);
                                    if (uw instanceof P) {
                                        String text = extractTextFromParagraph((P) uw);
                                        if (text != null && !text.isBlank()) sb.append(text).append(" ");
                                        String tbText = extractTextBoxTextFromParagraph((P) uw);
                                        if (tbText != null && !tbText.isBlank()) sb.append(tbText).append(" ");
                                    }
                                }
                                analysis.setHasFooter(true);
                                if (analysis.getFooterText() == null) analysis.setFooterText(sb.toString().trim());
                            }
                        } catch (Exception ignored) {}
                    } else if (part instanceof org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) {
                        org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart hp =
                                (org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart) part;
                        try {
                            org.docx4j.wml.Hdr hdr = hp.getJaxbElement();
                            if (hdr != null) {
                                StringBuilder sb = new StringBuilder();
                                for (Object o : hdr.getContent()) {
                                    Object uw = org.docx4j.XmlUtils.unwrap(o);
                                    if (uw instanceof P) {
                                        String text = extractTextFromParagraph((P) uw);
                                        if (text != null && !text.isBlank()) sb.append(text).append(" ");
                                        String tbText = extractTextBoxTextFromParagraph((P) uw);
                                        if (tbText != null && !tbText.isBlank()) sb.append(tbText).append(" ");
                                    }
                                }
                                analysis.setHasHeader(true);
                                if (analysis.getHeaderText() == null) analysis.setHeaderText(sb.toString().trim());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                System.err.println("[TemplateAnalyzer] Error scanning header/footer: " + e.getMessage());
            }
        }
    }

    /**
     * Represents a section in the template with its text and style
     */
    private static class TemplateSection {
        private final String text;
        private final String style;
        private final int contentIndex;
        private int sectionIndex; // unique sequential position in the full structure list
        /** Live JAXB reference to the paragraph — only set for H/F sections; never serialized. */
        private transient P paragraphRef;

        public TemplateSection(String text, String style, int contentIndex) {
            this.text = text;
            this.style = style;
            this.contentIndex = contentIndex;
        }

        public String getText() {
            return text;
        }

        public String getStyle() {
            return style;
        }

        public int getContentIndex() {
            return contentIndex;
        }

        public int getSectionIndex() { return sectionIndex; }
        public void setSectionIndex(int idx) { this.sectionIndex = idx; }
        public P getParagraphRef() { return paragraphRef; }
        public void setParagraphRef(P p) { this.paragraphRef = p; }
    }

    /**
     * Results of template analysis
     */
    private static class TemplateAnalysis {
        private Set<String> availableStyles = new LinkedHashSet<>();
        private List<TemplateSection> templateStructure = new ArrayList<>();
        private List<P> placeholders = new ArrayList<>();
        private Map<String, P> namedPlaceholders = new LinkedHashMap<>();
        private Map<String, P> namedHeaderFooterPlaceholders = new LinkedHashMap<>();
        private Set<String> tableStyles = new LinkedHashSet<>();
        private boolean hasHeader;
        private String headerText;
        private boolean hasFooter;
        private String footerText;
        // User-configured style mapping: report style role → template style id
        private Map<String, String> reportToTemplateStyleMapping = new HashMap<>();

        // Helpful mappings to resolve a requested style name (display name) to a style id used in the document
        private Map<String, String> styleNameToId = new HashMap<>();
        private Map<String, String> styleIdToName = new HashMap<>();
        private Map<String, Style> styleIdToStyle = new HashMap<>();
        private StyleDefinitionsPart styleDefinitionsPart;
        private Map<String, String> placeholderTexts = new LinkedHashMap<>();
        private Map<String, String> tableStyleNameMap = new LinkedHashMap<>();
        // Rich analysis results
        private List<WordTemplateMetadata.PlaceholderInfo> richPlaceholders = new ArrayList<>();
        private List<WordTemplateMetadata.StyleInfo> characterStyleList = new ArrayList<>();
        private List<WordTemplateMetadata.HeadingInfo> headingHierarchyList = new ArrayList<>();
        private WordTemplateMetadata.TocInfo tocInfo;
        private WordTemplateMetadata.HFInfo richHeaderInfo;
        private WordTemplateMetadata.HFInfo richFooterInfo;
        // AI multi-candidates: role → list of {index, text, style}
        private Map<String, List<Map<String, Object>>> aiCandidateDetails = new LinkedHashMap<>();

        public Set<String> getAvailableStyles() { return availableStyles; }
        public void setAvailableStyles(Set<String> availableStyles) { this.availableStyles = availableStyles; }

        public List<TemplateSection> getTemplateStructure() { return templateStructure; }
        public void setTemplateStructure(List<TemplateSection> templateStructure) { this.templateStructure = templateStructure; }

        public List<P> getPlaceholders() { return placeholders; }
        public void setPlaceholders(List<P> placeholders) { this.placeholders = placeholders; }

        public Map<String, P> getNamedPlaceholders() { return namedPlaceholders; }
        public void setNamedPlaceholders(Map<String, P> namedPlaceholders) { this.namedPlaceholders = namedPlaceholders; }

        public Map<String, P> getNamedHeaderFooterPlaceholders() { return namedHeaderFooterPlaceholders; }
        public void setNamedHeaderFooterPlaceholders(Map<String, P> m) { this.namedHeaderFooterPlaceholders = m != null ? m : new LinkedHashMap<>(); }

        public Set<String> getTableStyles() { return tableStyles; }
        public void setTableStyles(Set<String> tableStyles) { this.tableStyles = tableStyles; }

        public Map<String, String> getPlaceholderTexts() { return placeholderTexts; }
        public void setPlaceholderTexts(Map<String, String> m) { this.placeholderTexts = m != null ? m : new LinkedHashMap<>(); }

        public Map<String, String> getTableStyleNameMap() { return tableStyleNameMap; }
        public void setTableStyleNameMap(Map<String, String> m) { this.tableStyleNameMap = m != null ? m : new LinkedHashMap<>(); }

        public List<WordTemplateMetadata.PlaceholderInfo> getRichPlaceholders() { return richPlaceholders; }
        public void setRichPlaceholders(List<WordTemplateMetadata.PlaceholderInfo> l) { this.richPlaceholders = l != null ? l : new ArrayList<>(); }
        public List<WordTemplateMetadata.StyleInfo> getCharacterStyleList() { return characterStyleList; }
        public void setCharacterStyleList(List<WordTemplateMetadata.StyleInfo> l) { this.characterStyleList = l != null ? l : new ArrayList<>(); }
        public List<WordTemplateMetadata.HeadingInfo> getHeadingHierarchyList() { return headingHierarchyList; }
        public void setHeadingHierarchyList(List<WordTemplateMetadata.HeadingInfo> l) { this.headingHierarchyList = l != null ? l : new ArrayList<>(); }
        public WordTemplateMetadata.TocInfo getTocInfo() { return tocInfo; }
        public void setTocInfo(WordTemplateMetadata.TocInfo t) { this.tocInfo = t; }
        public WordTemplateMetadata.HFInfo getRichHeaderInfo() { return richHeaderInfo; }
        public void setRichHeaderInfo(WordTemplateMetadata.HFInfo h) { this.richHeaderInfo = h; }
        public WordTemplateMetadata.HFInfo getRichFooterInfo() { return richFooterInfo; }
        public void setRichFooterInfo(WordTemplateMetadata.HFInfo f) { this.richFooterInfo = f; }

        public Map<String, List<Map<String, Object>>> getAiCandidateDetails() { return aiCandidateDetails; }

        /** Called by identifyPlaceholdersWithAI to store candidate info {index,text,style} for each role. */
        public void setAiCandidates(Map<String, List<Integer>> candidatesMap, List<TemplateSection> structure) {
            // NOTE: do NOT clear — fuzzy candidates populated earlier must survive.
            // AI results overwrite existing entries for roles it recognised; other roles keep their fuzzy candidates.
            if (candidatesMap == null || structure == null) return;
            // build a quick lookup: sectionIndex → TemplateSection (unique per section, unlike contentIndex)
            Map<Integer, TemplateSection> idxMap = new HashMap<>();
            for (TemplateSection ts : structure) idxMap.put(ts.getSectionIndex(), ts);
            for (Map.Entry<String, List<Integer>> entry : candidatesMap.entrySet()) {
                List<Map<String, Object>> candidates = new ArrayList<>();
                for (int idx : entry.getValue()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("index", idx);
                    TemplateSection ts = idxMap.get(idx);
                    c.put("text", ts != null ? ts.getText() : "");
                    c.put("style", ts != null ? ts.getStyle() : "");
                    candidates.add(c);
                }
                // putIfAbsent: don't overwrite fuzzy-detected candidates with AI result
                if (!candidates.isEmpty()) aiCandidateDetails.putIfAbsent(entry.getKey(), candidates);
            }
        }

        public boolean isHasHeader() { return hasHeader; }
        public void setHasHeader(boolean hasHeader) { this.hasHeader = hasHeader; }
        public String getHeaderText() { return headerText; }
        public void setHeaderText(String headerText) { this.headerText = headerText; }

        public boolean isHasFooter() { return hasFooter; }
        public void setHasFooter(boolean hasFooter) { this.hasFooter = hasFooter; }
        public String getFooterText() { return footerText; }
        public void setFooterText(String footerText) { this.footerText = footerText; }

        public void setStyleNameToIdMap(Map<String, String> map) {
            if (map != null) this.styleNameToId.putAll(map);
        }

        public void setStyleIdToNameMap(Map<String, String> map) {
            if (map != null) this.styleIdToName.putAll(map);
        }

        public void setStyleIdToStyleMap(Map<String, Style> map) {
            if (map != null) this.styleIdToStyle.putAll(map);
        }

        public void setStyleDefinitionsPart(StyleDefinitionsPart sdp) {
            this.styleDefinitionsPart = sdp;
        }

        public StyleDefinitionsPart getStyleDefinitionsPart() {
            return this.styleDefinitionsPart;
        }

        public Map<String, Style> getStyleIdToStyleMap() {
            return styleIdToStyle;
        }

        /**
         * Apply a user-configured style mapping so that resolveStyle uses explicit overrides first.
         */
        public void applyStyleMapping(WordStyleMapping mapping) {
            reportToTemplateStyleMapping.clear();
            if (mapping == null) return;
            if (mapping.getTitleStyle() != null && !mapping.getTitleStyle().isBlank())
                reportToTemplateStyleMapping.put("Title", mapping.getTitleStyle());
            if (mapping.getHeading1Style() != null && !mapping.getHeading1Style().isBlank())
                reportToTemplateStyleMapping.put("Heading1", mapping.getHeading1Style());
            if (mapping.getHeading2Style() != null && !mapping.getHeading2Style().isBlank())
                reportToTemplateStyleMapping.put("Heading2", mapping.getHeading2Style());
            if (mapping.getNormalStyle() != null && !mapping.getNormalStyle().isBlank())
                reportToTemplateStyleMapping.put("Normal", mapping.getNormalStyle());
        }

        /**
         * Resolve a requested style (which might be a display name like "Heading 1" or a style id like "Heading1")
         * to a style id that can be used in the document. If not found, return the provided fallback.
         */
        public String resolveStyle(String requested, String fallback) {
            if (requested == null || requested.isBlank()) return fallback;
            String reqTrim = requested.trim();
            // Check explicit user mapping first
            if (reportToTemplateStyleMapping.containsKey(reqTrim)) {
                String mapped = reportToTemplateStyleMapping.get(reqTrim);
                if (mapped != null && !mapped.isBlank()) return mapped;
            }
            if (styleIdToStyle.containsKey(reqTrim)) return reqTrim;
            if (styleNameToId.containsKey(reqTrim)) return styleNameToId.get(reqTrim);
            for (Map.Entry<String, String> e : styleNameToId.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(reqTrim)) return e.getValue();
            }
            String norm = reqTrim.replaceAll("\\s+", "").toLowerCase();
            for (String id : availableStyles) {
                if (id != null && id.replaceAll("\\s+", "").toLowerCase().equals(norm)) return id;
            }
            for (String name : styleNameToId.keySet()) {
                if (name != null && name.replaceAll("\\s+", "").toLowerCase().equals(norm)) return styleNameToId.get(name);
            }
            return fallback;
        }
    }

    /**
     * Serializable metadata about a Word template, persisted as JSON in OrganisationDetails.
     */
    public static class WordTemplateMetadata {
        private String checksum;
        private String analysisTimestamp;
        private List<String> availableStyles = new ArrayList<>();
        private List<String> tableStyles = new ArrayList<>();
        private List<String> foundMarkers = new ArrayList<>();
        private boolean hasHeader;
        private String headerText;
        private boolean hasFooter;
        private String footerText;
        private List<TemplateSectionMeta> structure = new ArrayList<>();

        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public String getAnalysisTimestamp() { return analysisTimestamp; }
        public void setAnalysisTimestamp(String analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
        public List<String> getAvailableStyles() { return availableStyles; }
        public void setAvailableStyles(List<String> availableStyles) { this.availableStyles = availableStyles; }
        public List<String> getTableStyles() { return tableStyles; }
        public void setTableStyles(List<String> tableStyles) { this.tableStyles = tableStyles; }
        public List<String> getFoundMarkers() { return foundMarkers; }
        public void setFoundMarkers(List<String> foundMarkers) { this.foundMarkers = foundMarkers; }
        public boolean isHasHeader() { return hasHeader; }
        public void setHasHeader(boolean hasHeader) { this.hasHeader = hasHeader; }
        public String getHeaderText() { return headerText; }
        public void setHeaderText(String headerText) { this.headerText = headerText; }
        public boolean isHasFooter() { return hasFooter; }
        public void setHasFooter(boolean hasFooter) { this.hasFooter = hasFooter; }
        public String getFooterText() { return footerText; }
        public void setFooterText(String footerText) { this.footerText = footerText; }
        public List<TemplateSectionMeta> getStructure() { return structure; }
        public void setStructure(List<TemplateSectionMeta> structure) { this.structure = structure; }

        private Map<String, Integer> aiBodyPlaceholderHints = new java.util.LinkedHashMap<>();
        public Map<String, Integer> getAiBodyPlaceholderHints() { return aiBodyPlaceholderHints; }
        public void setAiBodyPlaceholderHints(Map<String, Integer> h) { this.aiBodyPlaceholderHints = h; }

        private Map<String, String> tableStyleNames = new java.util.LinkedHashMap<>();
        public Map<String, String> getTableStyleNames() { return tableStyleNames; }
        public void setTableStyleNames(Map<String, String> m) { this.tableStyleNames = m != null ? m : new java.util.LinkedHashMap<>(); }

        private Map<String, String> detectedPlaceholderTexts = new java.util.LinkedHashMap<>();
        public Map<String, String> getDetectedPlaceholderTexts() { return detectedPlaceholderTexts; }
        public void setDetectedPlaceholderTexts(Map<String, String> m) { this.detectedPlaceholderTexts = m != null ? m : new java.util.LinkedHashMap<>(); }

        // AI multi-candidate options per role: role → [{index, text, style}, ...]
        private Map<String, List<Map<String, Object>>> aiCandidates = new java.util.LinkedHashMap<>();
        public Map<String, List<Map<String, Object>>> getAiCandidates() { return aiCandidates; }
        public void setAiCandidates(Map<String, List<Map<String, Object>>> m) { this.aiCandidates = m != null ? m : new java.util.LinkedHashMap<>(); }

        // Rich detected placeholders (SDT, field, fuzzy, AI, explicit)
        private List<PlaceholderInfo> detectedPlaceholders = new ArrayList<>();
        public List<PlaceholderInfo> getDetectedPlaceholders() { return detectedPlaceholders; }
        public void setDetectedPlaceholders(List<PlaceholderInfo> l) { this.detectedPlaceholders = l != null ? l : new ArrayList<>(); }

        // Character styles (type=character from StyleDefinitionsPart)
        private List<StyleInfo> characterStyles = new ArrayList<>();
        public List<StyleInfo> getCharacterStyles() { return characterStyles; }
        public void setCharacterStyles(List<StyleInfo> l) { this.characterStyles = l != null ? l : new ArrayList<>(); }

        // Heading hierarchy (paragraph styles with outline levels)
        private List<HeadingInfo> headingHierarchy = new ArrayList<>();
        public List<HeadingInfo> getHeadingHierarchy() { return headingHierarchy; }
        public void setHeadingHierarchy(List<HeadingInfo> l) { this.headingHierarchy = l != null ? l : new ArrayList<>(); }

        // TOC detection
        private TocInfo toc;
        public TocInfo getToc() { return toc; }
        public void setToc(TocInfo t) { this.toc = t; }

        // Rich header/footer info
        private HFInfo headerInfo;
        private HFInfo footerInfo;
        public HFInfo getHeaderInfo() { return headerInfo; }
        public void setHeaderInfo(HFInfo h) { this.headerInfo = h; }
        public HFInfo getFooterInfo() { return footerInfo; }
        public void setFooterInfo(HFInfo f) { this.footerInfo = f; }

        /** Rich placeholder record. */
        public static class PlaceholderInfo {
            private String role;           // TITLE, DATE, AUTHOR, ORG, CLASSIFICATION, VERSION
            private String detectionType;  // EXPLICIT_MARKER, FIELD, SDT, DOCPROPERTY, FUZZY, AI
            private String location;       // BODY, HEADER, FOOTER
            private String originalText;
            private String fieldCode;
            private String sdtTag;
            private String sdtAlias;
            private int paragraphIndex = -1;

            public String getRole() { return role; }
            public void setRole(String r) { this.role = r; }
            public String getDetectionType() { return detectionType; }
            public void setDetectionType(String t) { this.detectionType = t; }
            public String getLocation() { return location; }
            public void setLocation(String l) { this.location = l; }
            public String getOriginalText() { return originalText; }
            public void setOriginalText(String t) { this.originalText = t; }
            public String getFieldCode() { return fieldCode; }
            public void setFieldCode(String fc) { this.fieldCode = fc; }
            public String getSdtTag() { return sdtTag; }
            public void setSdtTag(String t) { this.sdtTag = t; }
            public String getSdtAlias() { return sdtAlias; }
            public void setSdtAlias(String a) { this.sdtAlias = a; }
            public int getParagraphIndex() { return paragraphIndex; }
            public void setParagraphIndex(int i) { this.paragraphIndex = i; }
        }

        /** Style descriptor. */
        public static class StyleInfo {
            private String id;
            private String displayName;
            private String type;           // paragraph, character, table, numbering
            private Integer outlineLevel;
            private boolean builtIn;

            public String getId() { return id; }
            public void setId(String i) { this.id = i; }
            public String getDisplayName() { return displayName; }
            public void setDisplayName(String n) { this.displayName = n; }
            public String getType() { return type; }
            public void setType(String t) { this.type = t; }
            public Integer getOutlineLevel() { return outlineLevel; }
            public void setOutlineLevel(Integer l) { this.outlineLevel = l; }
            public boolean isBuiltIn() { return builtIn; }
            public void setBuiltIn(boolean b) { this.builtIn = b; }
        }

        /** Heading hierarchy entry. */
        public static class HeadingInfo {
            private int level;
            private String styleId;
            private String displayName;
            private int outlineLevel;

            public int getLevel() { return level; }
            public void setLevel(int l) { this.level = l; }
            public String getStyleId() { return styleId; }
            public void setStyleId(String s) { this.styleId = s; }
            public String getDisplayName() { return displayName; }
            public void setDisplayName(String n) { this.displayName = n; }
            public int getOutlineLevel() { return outlineLevel; }
            public void setOutlineLevel(int l) { this.outlineLevel = l; }
        }

        /** Table of Contents info. */
        public static class TocInfo {
            private boolean present;
            private String fieldCode;
            private String headingLevels;
            private int paragraphIndex = -1;

            public boolean isPresent() { return present; }
            public void setPresent(boolean p) { this.present = p; }
            public String getFieldCode() { return fieldCode; }
            public void setFieldCode(String fc) { this.fieldCode = fc; }
            public String getHeadingLevels() { return headingLevels; }
            public void setHeadingLevels(String hl) { this.headingLevels = hl; }
            public int getParagraphIndex() { return paragraphIndex; }
            public void setParagraphIndex(int i) { this.paragraphIndex = i; }
        }

        /** Rich header/footer info. */
        public static class HFInfo {
            private boolean present;
            private String fullText;
            private boolean hasPageNumber;
            private boolean hasDate;
            private boolean hasClassification;
            private List<String> fieldCodes = new ArrayList<>();
            private List<PlaceholderInfo> placeholders = new ArrayList<>();

            public boolean isPresent() { return present; }
            public void setPresent(boolean p) { this.present = p; }
            public String getFullText() { return fullText; }
            public void setFullText(String t) { this.fullText = t; }
            public boolean isHasPageNumber() { return hasPageNumber; }
            public void setHasPageNumber(boolean b) { this.hasPageNumber = b; }
            public boolean isHasDate() { return hasDate; }
            public void setHasDate(boolean b) { this.hasDate = b; }
            public boolean isHasClassification() { return hasClassification; }
            public void setHasClassification(boolean b) { this.hasClassification = b; }
            public List<String> getFieldCodes() { return fieldCodes; }
            public void setFieldCodes(List<String> l) { this.fieldCodes = l != null ? l : new ArrayList<>(); }
            public List<PlaceholderInfo> getPlaceholders() { return placeholders; }
            public void setPlaceholders(List<PlaceholderInfo> l) { this.placeholders = l != null ? l : new ArrayList<>(); }
        }

        public static class TemplateSectionMeta {
            private String style;
            private String textPreview;
            private String marker;
            private int sectionIndex;

            public String getStyle() { return style; }
            public void setStyle(String style) { this.style = style; }
            public String getTextPreview() { return textPreview; }
            public void setTextPreview(String textPreview) { this.textPreview = textPreview; }
            public String getMarker() { return marker; }
            public void setMarker(String marker) { this.marker = marker; }
            public int getSectionIndex() { return sectionIndex; }
            public void setSectionIndex(int sectionIndex) { this.sectionIndex = sectionIndex; }
        }
    }

    /**
     * User-configured mapping from report style roles to Word template style IDs.
     * Persisted as JSON in OrganisationDetails.wordTemplateStyleMappingJson.
     */
    public static class WordStyleMapping {
        private String titleStyle = "Title";
        private String heading1Style = "Heading1";
        private String heading2Style = "Heading2";
        private String normalStyle = "Normal";
        private String tableStyle = "";

        public String getTitleStyle() { return titleStyle; }
        public void setTitleStyle(String titleStyle) { this.titleStyle = titleStyle; }

        public String getHeading1Style() { return heading1Style; }
        public void setHeading1Style(String heading1Style) { this.heading1Style = heading1Style; }

        public String getHeading2Style() { return heading2Style; }
        public void setHeading2Style(String heading2Style) { this.heading2Style = heading2Style; }

        public String getNormalStyle() { return normalStyle; }
        public void setNormalStyle(String normalStyle) { this.normalStyle = normalStyle; }

        public String getTableStyle() { return tableStyle; }
        public void setTableStyle(String tableStyle) { this.tableStyle = tableStyle; }
    }

    /**
     * Persisted mapping from detected placeholder roles to assessment attribute paths.
     * Stored as JSON in OrganisationDetails.wordTemplatePlaceholderMappingJson.
     */
    public static class WordPlaceholderAttributeMapping {
        // role (e.g. "TITLE", "DATE", "AUTHOR", "ORG") → assessment attribute path
        private Map<String, String> roleToAttribute = new LinkedHashMap<>();
        // role → sectionIndex chosen by the user in the candidate dropdown (-1 = SKIP)
        private Map<String, Integer> roleToSelectedSectionIndex = new LinkedHashMap<>();
        // user-defined role names beyond the default TITLE/DATE/AUTHOR/ORG set
        private List<String> customRoles = new ArrayList<>();

        public Map<String, String> getRoleToAttribute() { return roleToAttribute; }
        public void setRoleToAttribute(Map<String, String> m) { this.roleToAttribute = m != null ? m : new LinkedHashMap<>(); }

        public Map<String, Integer> getRoleToSelectedSectionIndex() { return roleToSelectedSectionIndex; }
        public void setRoleToSelectedSectionIndex(Map<String, Integer> m) { this.roleToSelectedSectionIndex = m != null ? m : new LinkedHashMap<>(); }

        public List<String> getCustomRoles() { return customRoles; }
        public void setCustomRoles(List<String> l) { this.customRoles = l != null ? l : new ArrayList<>(); }
    }
}
