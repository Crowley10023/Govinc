package com.govinc.assessment;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityModel;
import com.govinc.util.OpenAIUtil;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a user-supplied document (PDF/DOCX/DOC/XLSX/XLS/TXT) and proposes
 * answers and comments for every {@link SecurityControl} of the assessment's
 * catalog.
 *
 * <p>Matching runs in two stages:</p>
 * <ol>
 *   <li><b>Exact match</b> &mdash; literal occurrences of the control's
 *       reference, tag(s) or name are located in the extracted text. When
 *       found, a context window around the hit is extracted as evidence and
 *       the highest-rated maturity answer is proposed. The user remains the
 *       final arbiter.</li>
 *   <li><b>AI fallback</b> &mdash; controls for which no exact match was
 *       found are sent in a single batched prompt to {@link OpenAIUtil},
 *       which returns a JSON array of proposed answers with brief comments.</li>
 * </ol>
 *
 * <p>The service never writes anything to the database &mdash; it only
 * <i>proposes</i>. Persistence happens after the user acknowledges the
 * proposals via {@code AssessmentImportController#apply}.</p>
 */
@Service
public class AssessmentImportService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentImportService.class);

    /** Hard cap on prompt size so we don't blow past provider context limits. */
    private static final int MAX_AI_TEXT_CHARS = 14_000;

    /** Half-width of the evidence snippet captured around an exact match. */
    private static final int EVIDENCE_RADIUS = 180;

    @Autowired
    private OpenAIUtil openAIUtil;

    public enum MatchType { EXACT, AI, NONE }

    /** Proposal for one security control. */
    public static class Proposal {
        public Long controlId;
        public String controlName;
        public String controlReference;
        public Long proposedAnswerId;
        public String proposedAnswerName;
        public Integer proposedRating;
        public String comment;
        public String evidence;
        public MatchType matchType;
        public Double confidence;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Extract text from {@code file} and propose answers/comments for every
     * control in {@code catalog} using its associated {@link MaturityModel}.
     */
    public List<Proposal> proposeFromFile(MultipartFile file, SecurityCatalog catalog) throws IOException {
        String text = extractText(file);
        if (text == null || text.isBlank()) {
            throw new IOException("Could not extract any text from " + file.getOriginalFilename());
        }
        return proposeFromText(text, catalog);
    }

    public List<Proposal> proposeFromText(String text, SecurityCatalog catalog) {
        List<SecurityControl> controls = catalog.getSecurityControls();
        List<MaturityAnswer> answers = new ArrayList<>(catalog.getMaturityModel().getMaturityAnswers());
        // Sort answers by rating descending so answers.get(0) is the highest maturity.
        answers.sort(Comparator.comparingInt(MaturityAnswer::getRating).reversed());

        // Phase 1: find controls whose reference literal appears in the document.
        // Only the control's reference field is used (≥4 chars) to avoid the false
        // positives that name/tag matching causes on generic security documents.
        Map<SecurityControl, String> exactEvidence = new LinkedHashMap<>();
        List<SecurityControl> needsAi = new ArrayList<>();
        for (SecurityControl c : controls) {
            String ev = findExactEvidence(c, text);
            if (ev != null) {
                exactEvidence.put(c, ev);
            } else {
                needsAi.add(c);
            }
        }

        List<Proposal> proposals = new ArrayList<>();

        // Phase 1b: for exact reference matches, ask AI to assess the maturity level
        // from the evidence snippet and generate a comment.  This prevents blindly
        // assigning the highest-rated answer whenever a reference is mentioned.
        if (!exactEvidence.isEmpty()) {
            int batchSize = 10;
            List<SecurityControl> matched = new ArrayList<>(exactEvidence.keySet());
            for (int i = 0; i < matched.size(); i += batchSize) {
                List<SecurityControl> batch = matched.subList(i, Math.min(i + batchSize, matched.size()));
                proposals.addAll(aiAssessExactBatch(batch, exactEvidence, answers));
            }
        }

        // Phase 2: AI fallback for controls with no reference match.
        if (!needsAi.isEmpty()) {
            String trimmedText = truncate(text, MAX_AI_TEXT_CHARS);
            int batchSize = 10;
            for (int i = 0; i < needsAi.size(); i += batchSize) {
                List<SecurityControl> batch = needsAi.subList(i, Math.min(i + batchSize, needsAi.size()));
                proposals.addAll(aiMatch(batch, trimmedText, answers));
            }
        }

        // Preserve original catalog ordering.
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < controls.size(); i++) order.put(controls.get(i).getId(), i);
        proposals.sort(Comparator.comparingInt(p -> order.getOrDefault(p.controlId, Integer.MAX_VALUE)));
        return proposals;
    }

    // ── Phase 1 — exact reference matching ───────────────────────────────

    /**
     * Returns an evidence snippet if the control's reference literal appears in
     * {@code text}, or {@code null} if there is no match.
     *
     * <p>Only the {@code reference} field is used as a needle (minimum 4 chars).
     * Name and tag matching was removed because they are too short or too generic
     * (e.g. "user", "risk", "iam") and produce large numbers of false positives
     * on typical security documents.</p>
     */
    private String findExactEvidence(SecurityControl c, String text) {
        String ref = c.getReference();
        if (ref == null || ref.trim().length() < 4) return null;
        String needle = ref.trim();
        int idx = indexOfWord(text, needle);
        if (idx < 0) return null;
        return snippetAround(text, idx, needle.length());
    }

    /**
     * For controls whose reference was found in the document, send the evidence
     * snippets to AI so it can assess the actual maturity level demonstrated by
     * the document — rather than blindly assigning the highest-rated answer.
     * Falls back to the lowest-rated answer (conservative) when AI is unavailable.
     */
    private List<Proposal> aiAssessExactBatch(
            List<SecurityControl> batch,
            Map<SecurityControl, String> evidenceMap,
            List<MaturityAnswer> answers) {

        StringBuilder answerList = buildAnswerList(answers);

        StringBuilder controlList = new StringBuilder();
        for (SecurityControl c : batch) {
            String ev = evidenceMap.getOrDefault(c, "");
            controlList.append("- id=").append(c.getId())
                    .append(" | ref=").append(nullSafe(c.getReference()))
                    .append(" | name=").append(nullSafe(c.getName()));
            if (c.getDetail() != null && !c.getDetail().isBlank()) {
                String detail = c.getDetail().replaceAll("\\s+", " ").trim();
                if (detail.length() > 200) detail = detail.substring(0, 200) + "…";
                controlList.append(" | detail=").append(detail);
            }
            controlList.append("\n  evidence_from_document: \"").append(ev).append("\"\n");
        }

        String prompt = "You are an experienced information security auditor.\n"
                + "The document references the security controls listed below. For each control,\n"
                + "the \"evidence_from_document\" field shows the text extracted from around that\n"
                + "reference. Assess what maturity level the evidence actually demonstrates.\n"
                + "Be conservative: choose a high rating ONLY if the evidence clearly shows\n"
                + "the control is implemented, not just mentioned or planned.\n\n"
                + "Return a JSON array; each entry MUST be a JSON object with EXACTLY these fields:\n"
                + "  { \"controlId\": <number>, \"answerId\": <number>, \"comment\": <string>, \"confidence\": <number 0..1> }\n"
                + "The comment must be 1-2 sentences quoting or paraphrasing the relevant evidence.\n"
                + "Do not include any text outside the JSON array.\n\n"
                + "MATURITY ANSWERS (choose answerId from this list only):\n" + answerList
                + "\nCONTROLS WITH EVIDENCE:\n" + controlList;

        String aiResponse;
        try {
            aiResponse = openAIUtil.askAI(prompt);
        } catch (Exception e) {
            log.warn("[import] AI exact-match assessment failed: {}", e.getMessage());
            return fallbackExactProposals(batch, evidenceMap, answers);
        }

        return parseAiExactResponse(aiResponse, batch, evidenceMap, answers);
    }

    private List<Proposal> parseAiExactResponse(
            String aiResponse,
            List<SecurityControl> batch,
            Map<SecurityControl, String> evidenceMap,
            List<MaturityAnswer> answers) {

        List<Proposal> out = new ArrayList<>();
        try {
            String json = aiResponse == null ? "" : aiResponse.trim();
            int s = json.indexOf('[');
            int en = json.lastIndexOf(']');
            if (s < 0 || en < 0 || en < s) {
                log.warn("[import] AI exact-match returned no JSON array: {}", json);
                return fallbackExactProposals(batch, evidenceMap, answers);
            }
            JSONArray arr = new JSONArray(json.substring(s, en + 1));
            Map<Long, JSONObject> byControl = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                long cid = o.optLong("controlId", -1);
                if (cid > 0) byControl.put(cid, o);
            }
            Map<Long, MaturityAnswer> ansById = new HashMap<>();
            for (MaturityAnswer a : answers) ansById.put(a.getId(), a);

            for (SecurityControl c : batch) {
                JSONObject o = byControl.get(c.getId());
                Proposal p = new Proposal();
                p.controlId = c.getId();
                p.controlName = c.getName();
                p.controlReference = c.getReference();
                p.matchType = MatchType.EXACT;
                p.evidence = evidenceMap.get(c);

                if (o != null) {
                    long aid = o.optLong("answerId", -1);
                    MaturityAnswer chosen = ansById.get(aid);
                    if (chosen == null) {
                        // AI returned an unknown answerId → conservative fallback.
                        chosen = answers.isEmpty() ? null : answers.get(answers.size() - 1);
                    }
                    if (chosen != null) {
                        p.proposedAnswerId = chosen.getId();
                        p.proposedAnswerName = chosen.getAnswer();
                        p.proposedRating = chosen.getRating();
                    }
                    // Use the AI-generated comment; fall back to the raw evidence.
                    String aiComment = o.optString("comment", "").trim();
                    p.comment = aiComment.isEmpty() ? (p.evidence != null ? p.evidence : "") : aiComment;
                    p.confidence = o.optDouble("confidence", 0.7);
                } else {
                    // AI returned nothing for this control.
                    MaturityAnswer fallback = answers.isEmpty() ? null : answers.get(answers.size() - 1);
                    if (fallback != null) {
                        p.proposedAnswerId = fallback.getId();
                        p.proposedAnswerName = fallback.getAnswer();
                        p.proposedRating = fallback.getRating();
                    }
                    p.comment = p.evidence != null ? p.evidence : "";
                    p.confidence = 0.5;
                }
                out.add(p);
            }
        } catch (Exception ex) {
            log.warn("[import] failed to parse AI exact-match JSON: {}", ex.getMessage());
            return fallbackExactProposals(batch, evidenceMap, answers);
        }
        return out;
    }

    /** Conservative fallback when AI is not available for exact matches. */
    private List<Proposal> fallbackExactProposals(
            List<SecurityControl> batch,
            Map<SecurityControl, String> evidenceMap,
            List<MaturityAnswer> answers) {
        MaturityAnswer fallback = answers.isEmpty() ? null : answers.get(answers.size() - 1);
        List<Proposal> out = new ArrayList<>();
        for (SecurityControl c : batch) {
            Proposal p = new Proposal();
            p.controlId = c.getId();
            p.controlName = c.getName();
            p.controlReference = c.getReference();
            p.matchType = MatchType.EXACT;
            p.evidence = evidenceMap.get(c);
            if (fallback != null) {
                p.proposedAnswerId = fallback.getId();
                p.proposedAnswerName = fallback.getAnswer();
                p.proposedRating = fallback.getRating();
            }
            // Use the raw evidence text as the comment; no synthetic text added.
            p.comment = p.evidence != null ? p.evidence : "";
            p.confidence = 0.5;
            out.add(p);
        }
        return out;
    }

    private int indexOfWord(String haystack, String needle) {
        // Word-boundary, case-insensitive match — keeps "AC-1" from matching "AC-10".
        String regex = "(?i)(?<![\\p{L}\\p{N}_-])"
                + Pattern.quote(needle)
                + "(?![\\p{L}\\p{N}_-])";
        Matcher m = Pattern.compile(regex).matcher(haystack);
        return m.find() ? m.start() : -1;
    }

    private String snippetAround(String text, int idx, int hitLen) {
        int start = Math.max(0, idx - EVIDENCE_RADIUS);
        int end = Math.min(text.length(), idx + hitLen + EVIDENCE_RADIUS);
        String s = text.substring(start, end).replaceAll("\\s+", " ").trim();
        if (start > 0) s = "…" + s;
        if (end < text.length()) s = s + "…";
        return s;
    }

    // ── Phase 2 — AI matching ─────────────────────────────────────────────

    /** Shared helper: renders the list of maturity answers for prompt construction. */
    private StringBuilder buildAnswerList(List<MaturityAnswer> answers) {
        StringBuilder sb = new StringBuilder();
        for (MaturityAnswer a : answers) {
            sb.append("  - id=").append(a.getId())
              .append(" | rating=").append(a.getRating())
              .append(" | name=").append(nullSafe(a.getAnswer()));
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                sb.append(" | description=").append(a.getDescription().replaceAll("\\s+", " "));
            }
            sb.append('\n');
        }
        return sb;
    }

    private List<Proposal> aiMatch(List<SecurityControl> batch, String text, List<MaturityAnswer> answers) {
        StringBuilder controlList = new StringBuilder();
        for (SecurityControl c : batch) {
            controlList.append("- id=").append(c.getId())
                    .append(" | ref=").append(nullSafe(c.getReference()))
                    .append(" | name=").append(nullSafe(c.getName()));
            if (c.getDetail() != null && !c.getDetail().isBlank()) {
                String detail = c.getDetail().replaceAll("\\s+", " ").trim();
                if (detail.length() > 240) detail = detail.substring(0, 240) + "…";
                controlList.append(" | detail=").append(detail);
            }
            controlList.append('\n');
        }

        StringBuilder answerList = buildAnswerList(answers);

        String prompt = "You are an experienced information security auditor.\n"
                + "Below is the full text of a document a customer uploaded as evidence of their controls.\n"
                + "For each security control listed, decide which maturity answer best reflects what the\n"
                + "document actually demonstrates. Choose ONLY from the maturity answers listed.\n"
                + "If the document gives no evidence one way or the other, choose the LOWEST-rated\n"
                + "maturity answer (the most conservative).\n\n"
                + "Return a JSON array; each entry MUST be a JSON object with EXACTLY these fields:\n"
                + "  { \"controlId\": <number>, \"answerId\": <number>, \"comment\": <string>, \"confidence\": <number 0..1> }\n"
                + "The comment should be at most 2 sentences and quote or paraphrase the relevant evidence.\n"
                + "Do not include any text outside the JSON array.\n\n"
                + "MATURITY ANSWERS (choose answerId from here):\n" + answerList
                + "\nSECURITY CONTROLS:\n" + controlList
                + "\nDOCUMENT TEXT:\n```\n" + text + "\n```\n";

        String aiResponse;
        try {
            aiResponse = openAIUtil.askAI(prompt);
        } catch (Exception e) {
            log.warn("[import] AI call failed for batch of {} controls: {}", batch.size(), e.getMessage());
            return fallbackProposals(batch, answers, "AI call failed: " + e.getMessage());
        }

        List<Proposal> out = new ArrayList<>();
        try {
            String json = aiResponse == null ? "" : aiResponse.trim();
            int s = json.indexOf('[');
            int e = json.lastIndexOf(']');
            if (s < 0 || e < 0 || e < s) {
                log.warn("[import] AI returned no JSON array: {}", json);
                return fallbackProposals(batch, answers, "AI returned no JSON.");
            }
            JSONArray arr = new JSONArray(json.substring(s, e + 1));

            Map<Long, JSONObject> byControl = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                long cid = o.optLong("controlId", -1);
                if (cid > 0) byControl.put(cid, o);
            }
            Map<Long, MaturityAnswer> ansById = new HashMap<>();
            for (MaturityAnswer a : answers) ansById.put(a.getId(), a);

            for (SecurityControl c : batch) {
                JSONObject o = byControl.get(c.getId());
                Proposal p = new Proposal();
                p.controlId = c.getId();
                p.controlName = c.getName();
                p.controlReference = c.getReference();
                if (o != null) {
                    long aid = o.optLong("answerId", -1);
                    MaturityAnswer chosen = ansById.get(aid);
                    if (chosen == null) {
                        // Fall back to the lowest-rated answer (last in our sort).
                        chosen = answers.isEmpty() ? null : answers.get(answers.size() - 1);
                    }
                    if (chosen != null) {
                        p.proposedAnswerId = chosen.getId();
                        p.proposedAnswerName = chosen.getAnswer();
                        p.proposedRating = chosen.getRating();
                    }
                    p.comment = o.optString("comment", "");
                    p.confidence = o.has("confidence") ? o.optDouble("confidence", 0.5) : 0.5;
                    p.matchType = MatchType.AI;
                } else {
                    MaturityAnswer chosen = answers.isEmpty() ? null : answers.get(answers.size() - 1);
                    if (chosen != null) {
                        p.proposedAnswerId = chosen.getId();
                        p.proposedAnswerName = chosen.getAnswer();
                        p.proposedRating = chosen.getRating();
                    }
                    p.comment = "AI returned no response for this control.";
                    p.matchType = MatchType.NONE;
                    p.confidence = 0.0;
                }
                out.add(p);
            }
        } catch (Exception ex) {
            log.warn("[import] failed to parse AI JSON: {}", ex.getMessage());
            return fallbackProposals(batch, answers, "Could not parse AI response.");
        }
        return out;
    }

    private List<Proposal> fallbackProposals(List<SecurityControl> batch, List<MaturityAnswer> answers, String note) {
        MaturityAnswer fallback = answers.isEmpty() ? null : answers.get(answers.size() - 1);
        List<Proposal> out = new ArrayList<>();
        for (SecurityControl c : batch) {
            Proposal p = new Proposal();
            p.controlId = c.getId();
            p.controlName = c.getName();
            p.controlReference = c.getReference();
            if (fallback != null) {
                p.proposedAnswerId = fallback.getId();
                p.proposedAnswerName = fallback.getAnswer();
                p.proposedRating = fallback.getRating();
            }
            p.comment = note;
            p.matchType = MatchType.NONE;
            p.confidence = 0.0;
            out.add(p);
        }
        return out;
    }

    // ── Text extraction ───────────────────────────────────────────────────

    public String extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("No file uploaded.");
        }
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            if (lower.endsWith(".pdf")) return extractPdf(in);
            if (lower.endsWith(".docx")) return extractDocx(in);
            if (lower.endsWith(".doc")) return extractDoc(in);
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return extractExcel(in);
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
                return new String(in.readAllBytes());
            }
            // Last resort: assume plain text.
            return new String(in.readAllBytes());
        }
    }

    private String extractPdf(InputStream in) throws IOException {
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String extractDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private String extractDoc(InputStream in) throws IOException {
        try (HWPFDocument doc = new HWPFDocument(in);
             WordExtractor ex = new WordExtractor(doc)) {
            return ex.getText();
        } catch (NoClassDefFoundError e) {
            // hwpf is bundled transitively with poi-ooxml; if missing, fall back gracefully.
            throw new IOException("Legacy .doc parsing not available: " + e.getMessage());
        }
    }

    private String extractExcel(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Workbook wb = WorkbookFactory.create(in)) {
            for (Sheet sheet : wb) {
                sb.append("# Sheet: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    boolean first = true;
                    for (Cell cell : row) {
                        if (!first) sb.append(" | ");
                        sb.append(cellAsString(cell));
                        first = false;
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String cellAsString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:  return cell.getStringCellValue();
                case BOOLEAN: return Boolean.toString(cell.getBooleanCellValue());
                case NUMERIC: return Double.toString(cell.getNumericCellValue());
                case FORMULA: return cell.getCellFormula();
                default:      return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String nullSafe(String s) { return s == null ? "" : s; }
}
