package com.govinc.assessment;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.user.User;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceAssessmentService;
import com.govinc.util.OpenAIUtil;
import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
import com.govinc.entity.OpenAIConfiguration;
import com.govinc.repository.OpenAIConfigurationRepository;

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
import org.docx4j.wml.Tr;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Style;
import org.docx4j.wml.Color;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.XmlUtils;
import jakarta.xml.bind.JAXBElement;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced implementation using docx4j with template-aware AI prompt generation.
 */
@Component
public class AssessmentReporterWord {

    private final OrgServiceAssessmentService orgServiceAssessmentService;
    private final OpenAIConfigurationRepository openAIConfigurationRepository;
    private final OpenAIUtil openAIUtil;
    private final OrganisationDetailsRepository organisationDetailsRepository;

    // Progress tracking
    private final Map<Long, ReportProgress> progressMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public AssessmentReporterWord(OrgServiceAssessmentService orgServiceAssessmentService,
                                  OpenAIConfigurationRepository openAIConfigurationRepository,
                                  OpenAIUtil openAIUtil,
                                  OrganisationDetailsRepository organisationDetailsRepository) {
        this.orgServiceAssessmentService = orgServiceAssessmentService;
        this.openAIConfigurationRepository = openAIConfigurationRepository;
        this.openAIUtil = openAIUtil;
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
        try {
            if (tplPath == null || tplPath.isBlank()) {
                OrganisationDetails orgDetails = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
                if (orgDetails != null && orgDetails.getWordTemplatePath() != null && !orgDetails.getWordTemplatePath().isBlank()) {
                    tplPath = orgDetails.getWordTemplatePath();
                }
            }
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] Could not read organisation details: " + e.getMessage());
        }

        WordprocessingMLPackage wordMLPackage = null;
        TemplateAnalysis templateAnalysis = new TemplateAnalysis();

        if (tplPath != null && !tplPath.isBlank()) {
            File tplFile = new File(tplPath);
            if (tplFile.exists()) {
                try {
                    wordMLPackage = WordprocessingMLPackage.load(tplFile);

                    // Step 2: Analyze template BEFORE building prompt
                    updateProgress(assessmentId, 15, "Analyzing template structure...");
                    TemplateAnalyzer analyzer = new TemplateAnalyzer(wordMLPackage);
                    templateAnalysis = analyzer.analyze();

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
            // Step 3: Build prompt that includes template information
            updateProgress(assessmentId, 20, "Building AI prompt with template information...");
            String prompt = buildPrompt(assessment, details, users, orgUnit, answers, templateAnalysis);

            // Step 4: Generate content via AI
            updateProgress(assessmentId, 35, "Generating report content from AI...");
            String aiResult = openAIUtil.askAI(prompt);

            // Validate AI response
            if (aiResult == null || aiResult.isBlank()) {
                throw new Exception("AI returned an empty response");
            }
            String aiResultTrim = aiResult.trim();
            String lower = aiResultTrim.toLowerCase();
            if (lower.startsWith("no ai provider") || lower.startsWith("the configured") || lower.startsWith("error")
                    || lower.contains("openai api response") || lower.contains("openai returned code")
                    || lower.contains("error calling")) {
                throw new Exception("AI returned an error: " + aiResultTrim);
            }

            // Step 5: Parse AI response
            updateProgress(assessmentId, 55, "Parsing AI response...");
            JSONObject reportJson = extractJson(aiResultTrim);
            if (reportJson == null) {
                throw new Exception("AI response did not contain valid JSON. Response: " + aiResultTrim);
            }

            // Step 6: Build document from generated content
            updateProgress(assessmentId, 70, "Inserting content into template...");
            MainDocumentPart mdp = wordMLPackage.getMainDocumentPart();
            ObjectFactory factory = new ObjectFactory();

            // Now we accept that AI may provide paragraphs and also real tables.
            List<Object> generatedElements = buildElementsFromAiResponse(factory, reportJson, templateAnalysis);

            // Determine if a real table should still be inserted based on security catalog instructions
            SecurityCatalog cat = assessment.getSecurityCatalog();
            boolean needTable = false;
            if (cat != null && cat.getReportInstructions() != null) {
                String ri = cat.getReportInstructions().toLowerCase(Locale.ROOT);
                if (ri.contains("table") || ri.contains("tabelle")) {
                    needTable = true;
                }
            }

            // Prepare table data: controls and answers (used only as fallback if AI didn't include a table)
            List<SecurityControl> allControls = assessment.getSecurityCatalog() != null ?
                    assessment.getSecurityCatalog().getSecurityControls() : Collections.emptyList();
            Map<Long, AssessmentControlAnswer> answerMap = new HashMap<>();
            for (AssessmentControlAnswer a : answers) {
                if (a.getSecurityControl() != null) answerMap.put(a.getSecurityControl().getId(), a);
            }

            Tbl summaryTable = null;
            boolean aiProvidedTable = false;
            for (Object el : generatedElements) {
                if (el instanceof Tbl) {
                    aiProvidedTable = true;
                    break;
                }
            }

            if (needTable && !aiProvidedTable && !allControls.isEmpty()) {
                try {
                    summaryTable = createSummaryTable(factory, allControls, answerMap, templateAnalysis);
                    System.out.println("[AssessmentReporterWord] Summary table created (as requested by report instructions)");
                } catch (Exception e) {
                    System.err.println("[AssessmentReporterWord] Failed to create summary table: " + e.getMessage());
                }
            }

            // Step 7: Insert into template at placeholder location
            List<P> placeholders = templateAnalysis.getPlaceholders();
            if (!placeholders.isEmpty()) {
                updateProgress(assessmentId, 80, "Replacing placeholder with generated content...");
                List<Object> content = mdp.getContent();
                List<Integer> indexes = new ArrayList<>();
                for (P p : placeholders) {
                    int idx = content.indexOf(p);
                    if (idx >= 0) {
                        indexes.add(idx);
                    }
                }
                Collections.sort(indexes, Collections.reverseOrder());
                if (!indexes.isEmpty()) {
                    int insertAt = indexes.get(indexes.size() - 1);
                    for (int idx : indexes) {
                        if (idx >= 0 && idx < content.size()) {
                            content.remove(idx);
                        }
                    }
                    // Build combined content objects (paragraphs and optional table)
                    List<Object> combined = new ArrayList<>();
                    combined.addAll(generatedElements);
                    if (summaryTable != null) combined.add(summaryTable);

                    content.addAll(insertAt, combined);
                }
            } else {
                updateProgress(assessmentId, 80, "No placeholder found, appending content to document...");
                if (summaryTable != null) {
                    mdp.getContent().addAll(new ArrayList<Object>(generatedElements));
                    mdp.getContent().add(summaryTable);
                } else {
                    mdp.getContent().addAll(new ArrayList<Object>(generatedElements));
                }
            }

            // Step 8: Save document
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

    /**
     * Build the prompt with detailed template information so AI knows what to generate
     */
    private String buildPrompt(Assessment assessment, AssessmentDetails details, List<User> users, OrgUnit orgUnit,
                               List<AssessmentControlAnswer> answers, TemplateAnalysis templateAnalysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI assistant that generates a structured JSON representation of a Microsoft Word report.\n");
        sb.append("You MUST follow the template structure provided below to ensure your output integrates correctly.\n\n");

        // ============ TEMPLATE STRUCTURE ============
        sb.append("========================================\n");
        sb.append("TEMPLATE STRUCTURE AND FORMATTING\n");
        sb.append("========================================\n\n");

        sb.append("IMPORTANT: The template already has predefined sections and styles.\n");
        sb.append("Your generated content MUST follow this structure:\n\n");

        sb.append("You may generate sections that contain only paragraphs (i.e., no heading).\n");
        sb.append("When creating plain paragraphs, omit the 'heading' field and provide 'content' only.\n\n");

        if (!templateAnalysis.getTemplateStructure().isEmpty()) {
            sb.append("Template sections and headings found:\n");
            int i = 1;
            for (TemplateSection section : templateAnalysis.getTemplateStructure()) {
                sb.append(String.format("%d. Text (style=%s): %s\n", i, section.getStyle(), section.getText().length() > 100 ? section.getText().substring(0, 100) + "..." : section.getText()));
                i++;
            }
            sb.append("\n");
        }

        sb.append("Available paragraph styles in template (you MUST use these):\n");
        sb.append(templateAnalysis.getAvailableStyles()).append("\n\n");

        sb.append("Preferred style mapping for your sections:\n");
        sb.append("  - Report title: use 'Title' style (or the equivalent in the template)\n");
        sb.append("  - Major sections: use 'Heading1' style if available\n");
        sb.append("  - Subsections: use 'Heading2' style if available\n");
        sb.append("  - Body content: use 'Normal' style\n\n");

        // Styling markup instructions
        sb.append("You MAY apply simple inline styling for color and bold. Use XML-like inline tags: <style color=\"#RRGGBB\" bold=\"true|false\">text</style>.\n");
        sb.append("The AI should only use these tags around spans that need different color/bold; otherwise return plain text.\n");
        sb.append("For tables, cells may be provided as objects with 'text' and optional 'color' and 'bold' fields.\n");
        sb.append("Example table cell: { \"text\": \"Control A\", \"color\": \"#FF0000\", \"bold\": true }\n\n");

        // ============ OUTPUT SCHEMA ============
        sb.append("========================================\n");
        sb.append("OUTPUT JSON SCHEMA\n");
        sb.append("========================================\n\n");
        sb.append("You MUST return ONLY valid JSON (no markdown, no HTML, no commentary):\n\n");
        sb.append("{\n");
        sb.append("  \"title\": \"Report title text\",\n");
        sb.append("  \"titleStyle\": \"Title\",\n");
        sb.append("  \"sections\": [\n");
        sb.append("    {\n");
        sb.append("      \"heading\": \"Section heading\",\n");
        sb.append("      \"headingStyle\": \"Heading1\",  // must match available styles from template\n");
        sb.append("      \"contentStyle\": \"Normal\",    // must match available styles from template (or 'Table' for tables)\n");
        sb.append("      \"content\": \"Paragraph text. Use double-newline (\\\\n\\\\n) to separate paragraphs.\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        // ============ SECURITY CATALOG INSTRUCTIONS ============
        sb.append("========================================\n");
        sb.append("SECURITY CATALOG REPORT INSTRUCTIONS\n");
        sb.append("========================================\n\n");

        SecurityCatalog cat = assessment.getSecurityCatalog();
        if (cat != null && cat.getReportInstructions() != null && !cat.getReportInstructions().isBlank()) {
            sb.append("Instructions from Security Catalog '").append(cat.getName()).append("':\n\n");
            sb.append(cat.getReportInstructions()).append("\n\n");
        } else {
            sb.append("No specific instructions defined in security catalog.\n");
            sb.append("Use standard security assessment report best practices.\n\n");
        }

        // ============ ASSESSMENT DATA ============
        sb.append("========================================\n");
        sb.append("ASSESSMENT DATA\n");
        sb.append("========================================\n\n");

        sb.append("Assessment Metadata:\n");
        sb.append("  ID: ").append(assessment.getId()).append("\n");
        sb.append("  Name: ").append(assessment.getName() == null ? "-" : assessment.getName()).append("\n");
        sb.append("  Date: ").append(assessment.getDate() == null ? "-" : assessment.getDate().toString()).append("\n");
        sb.append("  Organization: ").append(orgUnit == null ? "-" : orgUnit.getName()).append("\n");
        sb.append("  Completed: ").append(details == null || details.getDate() == null ? "-" : details.getDate().toString()).append("\n");
        sb.append("  Security Catalog: ").append(cat == null ? "-" : cat.getName() + " (Rev. " + (cat.getRevision() == null ? "-" : cat.getRevision()) + ")").append("\n\n");

        sb.append("Assessment Participants:\n");
        for (User u : users) {
            sb.append("  - ").append(u.getName()).append(" <").append(u.getEmail()).append(">\n");
        }
        sb.append("\n");

        // Include assigned Org Services so AI knows which services were used in this assessment
        sb.append("Assigned Org Services:\n");
        if (assessment.getOrgServices() != null && !assessment.getOrgServices().isEmpty()) {
            for (OrgService svc : assessment.getOrgServices()) {
                sb.append("  - ").append(svc.getName() == null ? "-" : svc.getName());
                if (svc.getDescription() != null && !svc.getDescription().isBlank()) sb.append(": ").append(svc.getDescription());
                sb.append("\n");
            }
        } else {
            sb.append("  - None\n");
        }
        sb.append("\n");

        // Provide a concise list of security control domains present in this assessment
        sb.append("Security Control Domains:\n");
        Set<String> domains = new java.util.TreeSet<>();
        if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getSecurityControls() != null) {
            for (SecurityControl ctrl : assessment.getSecurityCatalog().getSecurityControls()) {
                if (ctrl.getSecurityControlDomain() != null && ctrl.getSecurityControlDomain().getName() != null) {
                    domains.add(ctrl.getSecurityControlDomain().getName());
                }
            }
        }
        if (!domains.isEmpty()) {
            for (String d : domains) sb.append("  - ").append(d).append("\n");
        } else {
            sb.append("  - None\n");
        }
        sb.append("\n");

        // ============ CONTROLS AND ANSWERS ============
        sb.append("========================================\n");
        sb.append("SECURITY CONTROLS AND ASSESSMENT RESULTS\n");
        sb.append("========================================\n\n");

        List<SecurityControl> allControls = assessment.getSecurityCatalog() != null ?
                assessment.getSecurityCatalog().getSecurityControls() : Collections.emptyList();
        Map<Long, AssessmentControlAnswer> answerMap = new HashMap<>();
        for (AssessmentControlAnswer a : answers) {
            if (a.getSecurityControl() != null) answerMap.put(a.getSecurityControl().getId(), a);
        }

        for (SecurityControl ctrl : allControls) {
            AssessmentControlAnswer aca = answerMap.get(ctrl.getId());
            sb.append("CONTROL: ").append(ctrl.getName() == null ? "-" : ctrl.getName()).append("\n");
            if (ctrl.getDetail() != null && !ctrl.getDetail().isBlank()) {
                sb.append("  Description: ").append(ctrl.getDetail()).append("\n");
            }
            if (ctrl.getReference() != null && !ctrl.getReference().isBlank()) {
                sb.append("  Reference: ").append(ctrl.getReference()).append("\n");
            }
            if (aca != null) {
                MaturityAnswer ma = aca.getMaturityAnswer();
                if (ma != null && ma.getAnswer() != null) {
                    sb.append("  Maturity Level: ").append(ma.getAnswer()).append("\n");
                }
                sb.append("  Score: ").append(aca.getScore()).append("\n");
            } else {
                sb.append("  Status: Not yet assessed\n");
            }
            sb.append("\n");
        }

        // ============ GUIDANCE ============
        sb.append("========================================\n");
        sb.append("REPORT WRITING GUIDANCE\n");
        sb.append("========================================\n\n");
        sb.append("1. Generate a professional security assessment report.\n");
        sb.append("2. Follow the security catalog's report instructions strictly.\n");
        sb.append("3. Use the template's available styles EXACTLY as specified.\n");
        sb.append("7. Write for a management audience (balance technical detail with clarity).\n");
        sb.append("8. Use double-newlines (\\\\n\\\\n) to separate paragraphs within sections.\n");
        sb.append("9. Return ONLY valid JSON - no markdown, no HTML, no extra text.\n");
        sb.append("10. Ensure the report is actionable and aligned with security best practices.\n\n");
        sb.append("11. Be complete, do not shorten anything nor miss anything.\n\n");

        sb.append("Now generate the complete report in JSON format:\n");

        return sb.toString();
    }

    /**
     * Build a mixed list of docx elements (paragraphs and tables) from AI JSON response.
     */
    private List<Object> buildElementsFromAiResponse(ObjectFactory factory, JSONObject reportJson, TemplateAnalysis templateAnalysis) {
        List<Object> elements = new ArrayList<>();

        // Add title
        if (reportJson.has("title")) {
            String title = reportJson.optString("title");
            String requestedStyle = reportJson.optString("titleStyle", "Title");
            String resolved = resolveStylePreferAI(requestedStyle, templateAnalysis);
            elements.add(createParagraphWithStyle(factory, title, resolved, templateAnalysis));
        }

        // Add sections
        if (reportJson.has("sections")) {
            JSONArray sections = reportJson.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.getJSONObject(i);

                // Add section heading if present
                if (section.has("heading")) {
                    String heading = section.getString("heading");
                    String requestedHeadingStyle = section.optString("headingStyle", "Heading1");
                    String resolvedHeading = resolveStylePreferAI(requestedHeadingStyle, templateAnalysis);
                    elements.add(createParagraphWithStyle(factory, heading, resolvedHeading, templateAnalysis));
                }

                String requestedContentStyle = section.optString("contentStyle", "Normal");
                String resolvedContentStyle = resolveStylePreferAI(requestedContentStyle, templateAnalysis);
                String contentStyleNormalized = requestedContentStyle == null ? "" : requestedContentStyle.trim().toLowerCase(Locale.ROOT);

                // If contentStyle indicates a table, or the section contains table-like data, try to build a real table
                if (contentStyleNormalized.contains("table") || section.has("table") || section.has("headers") || section.has("rows") || section.has("columns")) {
                    try {
                        Tbl tbl = null;

                        // Case 1: Explicit "table" field (could be object or array)
                        if (section.has("table")) {
                            Object tblObj = section.get("table");
                            if (tblObj instanceof JSONObject) {
                                JSONObject tjo = (JSONObject) tblObj;
                                JSONArray headers = tjo.optJSONArray("headers");
                                JSONArray rows = tjo.optJSONArray("rows");
                                tbl = createTableFromJson(factory, headers, rows);
                            } else if (tblObj instanceof JSONArray) {
                                JSONArray tarr = (JSONArray) tblObj;
                                // assume first row headers
                                if (tarr.length() > 0) {
                                    if (tarr.get(0) instanceof JSONArray) {
                                        JSONArray headers = tarr.getJSONArray(0);
                                        JSONArray rows = new JSONArray();
                                        for (int r = 1; r < tarr.length(); r++) rows.put(tarr.getJSONArray(r));
                                        tbl = createTableFromJson(factory, headers, rows);
                                    } else {
                                        // treat as rows only (no header)
                                        JSONArray rows = tarr;
                                        tbl = createTableFromJson(factory, null, rows);
                                    }
                                }
                            }
                        }

                        // Case 2: Separate headers/rows fields at section level
                        if (tbl == null && (section.has("headers") || section.has("rows"))) {
                            JSONArray headers = section.optJSONArray("headers");
                            JSONArray rows = section.optJSONArray("rows");
                            tbl = createTableFromJson(factory, headers, rows);
                        }

                        // Case 3: content field may contain JSON array-of-arrays or a JSON representation of table
                        if (tbl == null && section.has("content")) {
                            Object contentObj = section.get("content");
                            try {
                                if (contentObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) contentObj;
                                    if (arr.length() > 0 && arr.get(0) instanceof JSONArray) {
                                        JSONArray headers = arr.getJSONArray(0);
                                        JSONArray rows = new JSONArray();
                                        for (int r = 1; r < arr.length(); r++) rows.put(arr.getJSONArray(r));
                                        tbl = createTableFromJson(factory, headers, rows);
                                    } else {
                                        // treat as rows only
                                        tbl = createTableFromJson(factory, null, arr);
                                    }
                                } else if (contentObj instanceof JSONObject) {
                                    JSONObject obj = (JSONObject) contentObj;
                                    JSONArray headers = obj.optJSONArray("headers");
                                    JSONArray rows = obj.optJSONArray("rows");
                                    if (headers != null || rows != null) {
                                        tbl = createTableFromJson(factory, headers, rows);
                                    }
                                } else {
                                    // try parsing string content as JSON
                                    String content = section.optString("content");
                                    try {
                                        JSONArray arr = new JSONArray(content);
                                        if (arr.length() > 0 && arr.get(0) instanceof JSONArray) {
                                            JSONArray headers = arr.getJSONArray(0);
                                            JSONArray rows = new JSONArray();
                                            for (int r = 1; r < arr.length(); r++) rows.put(arr.getJSONArray(r));
                                            tbl = createTableFromJson(factory, headers, rows);
                                        } else {
                                            tbl = createTableFromJson(factory, null, arr);
                                        }
                                    } catch (Exception e) {
                                        System.out.println("[AssessmentReporterWord] Could not parse content into table JSON: " + e.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("[AssessmentReporterWord] Error while interpreting section.content for table: " + e.getMessage());
                            }
                        }

                        if (tbl != null) elements.add(tbl);
                    } catch (Exception e) {
                        System.err.println("[AssessmentReporterWord] Error creating table from AI response: " + e.getMessage());
                    }

                    // continue to next section
                    continue;
                }

                // Add section content as paragraphs (allow sections without heading)
                if (section.has("content")) {
                    String content = section.getString("content");
                    // Split by double newline
                    String[] parts = content.split("\\n\\n");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            elements.add(createParagraphWithStyle(factory, trimmed, resolvedContentStyle, templateAnalysis));
                        }
                    }
                }
            }
        }

        return elements;
    }

    /**
     * Try to resolve a requested style with local template analysis first;
     * if no clear match, ask the AI to pick the closest style from the list.
     * Returns a style id usable in the document (falls back to 'Normal').
     */
    private String resolveStylePreferAI(String requested, TemplateAnalysis ta) {
        if (requested == null || requested.isBlank()) return "Normal";
        String req = requested.trim();

        // quick local resolution
        String local = ta.resolveStyle(req, null);
        if (local != null && !local.isBlank()) {
            return local;
        }

        // Build a concise prompt for the AI to pick the most appropriate style id/name
        StringBuilder p = new StringBuilder();
        p.append("You are a helper that selects the best matching Microsoft Word paragraph style from a provided list.\n");
        p.append("Available styles (styleId or display name):\n");
        int i = 0;
        for (String s : ta.getAvailableStyles()) {
            p.append(++i).append(". ").append(s).append("\n");
        }
        p.append("\n");
        p.append("Requested style name: '" + req + "'\n");
        p.append("Return EXACTLY one of the available styles above (style id or display name) that best matches the requested name.\n");
        p.append("If none are appropriate, return the word NONE. Respond with a single token only, no explanation.\n");

        String aiResp = null;
        try {
            aiResp = openAIUtil.askAI(p.toString());
            if (aiResp != null) aiResp = aiResp.trim();
        } catch (Exception e) {
            System.err.println("[AssessmentReporterWord] AI style matching failed: " + e.getMessage());
            aiResp = null;
        }

        if (aiResp == null || aiResp.isBlank()) return "Normal";
        // strip quotes
        aiResp = aiResp.replaceAll("^[\"']+|[\"']+$", "");
        if ("none".equalsIgnoreCase(aiResp)) return "Normal";

        // If the AI returned a display name, map to id if possible
        if (ta.getStyleIdToStyleMap().containsKey(aiResp)) return aiResp;
        for (String s : ta.getAvailableStyles()) if (s.equals(aiResp)) return s;

        // try mapping via styleNameToId
        Map<String, String> nameToId = ta.styleNameToId;
        if (nameToId != null) {
            String mapped = nameToId.get(aiResp);
            if (mapped != null) return mapped;
            for (Map.Entry<String, String> e : nameToId.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(aiResp)) return e.getValue();
                if (e.getKey() != null && e.getKey().replaceAll("\\s+", "").equalsIgnoreCase(aiResp.replaceAll("\\s+", ""))) return e.getValue();
            }
        }

        String norm = aiResp.replaceAll("\\s+", "").toLowerCase();
        for (String s : ta.getAvailableStyles()) if (s != null && s.replaceAll("\\s+", "").toLowerCase().equals(norm)) return s;

        return "Normal";
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
     * Create a paragraph and attempt to apply the template's style.
     * Use type-safe access to the StyleDefinitionsPart and apply paragraph and run properties.
     * Also processes inline styling tags parsed from AI.
     */
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

    /**
     * Create a simple summary table from controls and answers
     */
    private Tbl createSummaryTable(ObjectFactory factory, List<SecurityControl> controls, Map<Long, AssessmentControlAnswer> answerMap, TemplateAnalysis ta) {
        Tbl tbl = factory.createTbl();

        // Header row
        Tr header = factory.createTr();
        header.getContent().add(createTableCell(factory, "Control", null, false));
        header.getContent().add(createTableCell(factory, "Maturity", null, false));
        header.getContent().add(createTableCell(factory, "Score", null, false));
        header.getContent().add(createTableCell(factory, "Reference", null, false));
        tbl.getContent().add(header);

        for (SecurityControl sc : controls) {
            Tr row = factory.createTr();
            AssessmentControlAnswer aca = answerMap.get(sc.getId());
            String maturity = "-";
            String score = "-";
            if (aca != null) {
                if (aca.getMaturityAnswer() != null && aca.getMaturityAnswer().getAnswer() != null) maturity = aca.getMaturityAnswer().getAnswer();
                score = String.valueOf(aca.getScore());
            }
            row.getContent().add(createTableCell(factory, sc.getName() == null ? "-" : sc.getName(), null, false));
            row.getContent().add(createTableCell(factory, maturity, null, false));
            row.getContent().add(createTableCell(factory, score, null, false));
            row.getContent().add(createTableCell(factory, sc.getReference() == null ? "-" : sc.getReference(), null, false));
            tbl.getContent().add(row);
        }

        return tbl;
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

    private Tbl createTableFromJson(ObjectFactory factory, JSONArray headers, JSONArray rows) {
        Tbl tbl = factory.createTbl();
        // header row
        if (headers != null) {
            Tr hr = factory.createTr();
            for (int c = 0; c < headers.length(); c++) {
                Object hv = headers.opt(c);
                if (hv instanceof JSONObject) {
                    JSONObject ho = (JSONObject) hv;
                    String text = ho.optString("text", "");
                    String color = ho.optString("color", null);
                    boolean bold = ho.optBoolean("bold", false);
                    hr.getContent().add(createTableCell(factory, text, color, bold));
                } else {
                    String text = headers.optString(c, "");
                    hr.getContent().add(createTableCell(factory, text, null, false));
                }
            }
            tbl.getContent().add(hr);
        }
        if (rows != null) {
            for (int r = 0; r < rows.length(); r++) {
                Tr row = factory.createTr();
                Object rowObj = rows.opt(r);
                if (rowObj instanceof JSONArray) {
                    JSONArray rowArr = (JSONArray) rowObj;
                    for (int c = 0; c < rowArr.length(); c++) {
                        Object cv = rowArr.opt(c);
                        if (cv instanceof JSONObject) {
                            JSONObject co = (JSONObject) cv;
                            String text = co.optString("text", "");
                            String color = co.optString("color", null);
                            boolean bold = co.optBoolean("bold", false);
                            row.getContent().add(createTableCell(factory, text, color, bold));
                        } else {
                            String text = rowArr.optString(c, "");
                            row.getContent().add(createTableCell(factory, text, null, false));
                        }
                    }
                } else {
                    // unexpected - try to coerce
                    String text = rows.optString(r, "");
                    row.getContent().add(createTableCell(factory, text, null, false));
                }
                tbl.getContent().add(row);
            }
        }
        return tbl;
    }

    private JSONObject extractJson(String aiText) {
        int first = aiText.indexOf('{');
        int last = aiText.lastIndexOf('}');
        if (first >= 0 && last > first) {
            String sub = aiText.substring(first, last + 1);
            try {
                return new JSONObject(sub);
            } catch (Exception e) {
                System.out.println("[AssessmentReporterWord] First JSON parsing attempt failed: " + e.getMessage());
                System.out.println("[AssessmentReporterWord] Attempting lenient parsing...");

                String lenient = sub;

                // Fix 1: Remove trailing commas before closing braces/brackets
                lenient = lenient.replaceAll(",\\s*([\\]}])", "$1");

                // Fix 2: Replace single quotes with double quotes for property names
                lenient = lenient.replaceAll("'([^']*)'\\s*:", "\"$1\":");

                // Fix 3: Replace single quotes with double quotes for property values
                lenient = lenient.replaceAll(":\\s*'([^']*)'([,\\n\\r\\s}])", ": \"$1\"$2");

                try {
                    JSONObject result = new JSONObject(lenient);
                    System.out.println("[AssessmentReporterWord] Lenient parsing successful");
                    return result;
                } catch (Exception e2) {
                    System.err.println("[AssessmentReporterWord] Lenient parsing also failed: " + e2.getMessage());
                    System.err.println("[AssessmentReporterWord] Original JSON (first 500 chars): " + sub.substring(0, Math.min(500, sub.length())));
                    return null;
                }
            }
        }
        return null;
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

                // Find placeholder paragraphs
                List<P> placeholders = findPlaceholderParagraphs(mdp);
                analysis.setPlaceholders(placeholders);
                System.out.println("[TemplateAnalyzer] Found " + placeholders.size() + " placeholder paragraphs");
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
                for (Object c : content) {
                    Object unwrapped = org.docx4j.XmlUtils.unwrap(c);
                    if (unwrapped instanceof P) {
                        P p = (P) unwrapped;
                        totalParagraphs++;
                        String style = extractStyleFromParagraph(p);
                        String text = extractTextFromParagraph(p);
                        if (text != null && !text.isBlank()) {
                            String finalStyle = style != null ? style : "Normal";
                            sections.add(new TemplateSection(text, finalStyle));
                            sectionCount++;
                            String truncatedText = text.length() > 80 ? text.substring(0, 80) + "..." : text;
                            System.out.println("[TemplateAnalyzer.extractTemplateStructure]   Section " + sectionCount + " (style='" + finalStyle + "'): " + truncatedText);
                        } else {
                            emptyParagraphs++;
                        }
                    }
                }
                System.out.println("[TemplateAnalyzer.extractTemplateStructure] Total sections extracted: " + sectionCount + " from " + totalParagraphs + " total paragraphs (" + emptyParagraphs + " were empty)");
            } catch (Exception e) {
                System.err.println("[AssessmentReporterWord] Error extracting template structure: " + e.getMessage());
                e.printStackTrace();
            }
            return sections;
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
                System.err.println("[AssessmentReporterWord] Error extracting paragraph style: " + e.getMessage());
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
    }

    /**
     * Represents a section in the template with its text and style
     */
    private static class TemplateSection {
        private final String text;
        private final String style;

        public TemplateSection(String text, String style) {
            this.text = text;
            this.style = style;
        }

        public String getText() {
            return text;
        }

        public String getStyle() {
            return style;
        }
    }

    /**
     * Results of template analysis
     */
    private static class TemplateAnalysis {
        private Set<String> availableStyles = new LinkedHashSet<>();
        private List<TemplateSection> templateStructure = new ArrayList<>();
        private List<P> placeholders = new ArrayList<>();

        // Helpful mappings to resolve a requested style name (display name) to a style id used in the document
        private Map<String, String> styleNameToId = new HashMap<>();
        private Map<String, String> styleIdToName = new HashMap<>();
        private Map<String, Style> styleIdToStyle = new HashMap<>();
        private StyleDefinitionsPart styleDefinitionsPart;

        public Set<String> getAvailableStyles() {
            return availableStyles;
        }

        public void setAvailableStyles(Set<String> availableStyles) {
            this.availableStyles = availableStyles;
        }

        public List<TemplateSection> getTemplateStructure() {
            return templateStructure;
        }

        public void setTemplateStructure(List<TemplateSection> templateStructure) {
            this.templateStructure = templateStructure;
        }

        public List<P> getPlaceholders() {
            return placeholders;
        }

        public void setPlaceholders(List<P> placeholders) {
            this.placeholders = placeholders;
        }

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
         * Resolve a requested style (which might be a display name like "Heading 1" or a style id like "Heading1")
         * to a style id that can be used in the document. If not found, return the provided fallback.
         */
        public String resolveStyle(String requested, String fallback) {
            if (requested == null || requested.isBlank()) return fallback;

            // Trim and normalize
            String reqTrim = requested.trim();

            // direct id match
            if (styleIdToStyle.containsKey(reqTrim)) return reqTrim;

            // If we already have a mapping from display name to id, prefer returning id
            if (styleNameToId.containsKey(reqTrim)) return styleNameToId.get(reqTrim);

            // case-insensitive display name match
            for (Map.Entry<String, String> e : styleNameToId.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(reqTrim)) return e.getValue();
            }

            // try normalize (remove spaces, lower-case) to match common id/display name differences
            String norm = reqTrim.replaceAll("\\s+", "").toLowerCase();
            for (String id : availableStyles) {
                if (id != null && id.replaceAll("\\s+", "").toLowerCase().equals(norm)) return id;
            }
            for (String name : styleNameToId.keySet()) {
                if (name != null && name.replaceAll("\\s+", "").toLowerCase().equals(norm)) return styleNameToId.get(name);
            }

            // last resort: return fallback
            return fallback;
        }
    }
}
