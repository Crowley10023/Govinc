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

    /**
     * Minimum AI-reported confidence required to actually propose a maturity
     * answer for a control. Below this, the row is left as "not answered" so
     * the user is not pushed toward a guess.
     */
    private static final double MIN_CONFIDENCE = 0.5;

    /** Maximum number of evidence snippets collected per control. */
    private static final int MAX_EVIDENCE_SNIPPETS = 3;

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
     * Returns an evidence snippet if the control's reference (or one of its
     * tags) literally appears in {@code text}, or {@code null} if there is no
     * match.
     *
     * <p>Needles considered:</p>
     * <ul>
     *   <li>{@code reference} when at least 4 chars long &mdash; primary signal.</li>
     *   <li>each comma/whitespace-separated token of {@code tag} when at least
     *       5 chars long &mdash; secondary signal.</li>
     * </ul>
     * <p>Name matching is intentionally excluded: control names are typically
     * short generic phrases ("user access", "risk register") that produce a
     * flood of false positives on real-world security documents.</p>
     *
     * <p>Up to {@link #MAX_EVIDENCE_SNIPPETS} non-overlapping occurrences are
     * collected and concatenated so the AI auditor sees broader context.</p>
     */
    private String findExactEvidence(SecurityControl c, String text) {
        java.util.LinkedHashSet<String> needles = new java.util.LinkedHashSet<>();
        String ref = c.getReference();
        if (ref != null && ref.trim().length() >= 4) {
            needles.add(ref.trim());
        }
        String tag = c.getTag();
        if (tag != null && !tag.isBlank()) {
            for (String t : tag.split("[,;\\s]+")) {
                String tt = t.trim();
                if (tt.length() >= 5) needles.add(tt);
            }
        }
        if (needles.isEmpty()) return null;

        List<String> snippets = new ArrayList<>();
        Set<Integer> seenBuckets = new HashSet<>();
        for (String needle : needles) {
            for (int idx : allIndicesOfWord(text, needle)) {
                // De-duplicate overlapping windows: two hits within ~200 chars
                // produce essentially the same snippet.
                int bucket = idx / (EVIDENCE_RADIUS * 2);
                if (!seenBuckets.add(bucket)) continue;
                snippets.add(snippetAround(text, idx, needle.length()));
                if (snippets.size() >= MAX_EVIDENCE_SNIPPETS) break;
            }
            if (snippets.size() >= MAX_EVIDENCE_SNIPPETS) break;
        }
        return snippets.isEmpty() ? null : String.join(" \u2014 ", snippets);
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
                + "the \"evidence_from_document\" field shows one or more text snippets extracted\n"
                + "from around that reference. Assess what maturity level the evidence actually\n"
                + "demonstrates. Be conservative: choose a high rating ONLY if the evidence\n"
                + "clearly shows the control is implemented, not just mentioned or planned.\n\n"
                + "Calibrate the confidence score honestly:\n"
                + "  - >= 0.8 when the evidence unambiguously describes implementation;\n"
                + "  - 0.5 to 0.8 when the evidence is suggestive but not conclusive;\n"
                + "  - <  0.5 when the reference is only mentioned or the evidence is too vague.\n"
                + "If confidence would be < 0.5, return answerId=null \u2014 we will leave the row\n"
                + "as \"not answered\" rather than guess.\n\n"
                + "Return a JSON array; each entry MUST be a JSON object with EXACTLY these fields:\n"
                + "  { \"controlId\": <number>, \"answerId\": <number|null>, \"comment\": <string>, \"confidence\": <number 0..1> }\n"
                + "The comment must be 1-2 sentences quoting or paraphrasing the relevant evidence.\n"
                + "Leave the comment as an empty string \"\" if the evidence does not justify any\n"
                + "statement \u2014 do NOT invent a rationale.\n"
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
                p.evidence = evidenceMap.get(c);

                if (o != null) {
                    double conf = o.optDouble("confidence", 0.0);
                    long aid = o.optLong("answerId", -1);
                    MaturityAnswer chosen = ansById.get(aid);

                    // Use the AI-generated comment verbatim; an empty/missing
                    // comment stays empty — never synthesise a rationale.
                    String aiComment = o.optString("comment", "").trim();
                    p.comment = aiComment;
                    p.confidence = conf;

                    if (chosen != null && conf >= MIN_CONFIDENCE) {
                        p.proposedAnswerId = chosen.getId();
                        p.proposedAnswerName = chosen.getAnswer();
                        p.proposedRating = chosen.getRating();
                        p.matchType = MatchType.EXACT;
                    } else {
                        // Low confidence or AI declined to answer — leave as
                        // "not answered". Comment (if any) and evidence are
                        // still shown so the user can decide manually.
                        p.matchType = MatchType.NONE;
                    }
                } else {
                    // AI returned nothing for this control.
                    p.comment = "";
                    p.confidence = 0.0;
                    p.matchType = MatchType.NONE;
                }
                out.add(p);
            }
        } catch (Exception ex) {
            log.warn("[import] failed to parse AI exact-match JSON: {}", ex.getMessage());
            return fallbackExactProposals(batch, evidenceMap, answers);
        }
        return out;
    }

    /**
     * Fallback when the AI call itself fails for the exact-match batch. We do
     * NOT guess an answer: the row is surfaced as "not answered" so the user
     * can decide. The evidence is still attached so they have context.
     */
    private List<Proposal> fallbackExactProposals(
            List<SecurityControl> batch,
            Map<SecurityControl, String> evidenceMap,
            List<MaturityAnswer> answers) {
        List<Proposal> out = new ArrayList<>();
        for (SecurityControl c : batch) {
            Proposal p = new Proposal();
            p.controlId = c.getId();
            p.controlName = c.getName();
            p.controlReference = c.getReference();
            p.matchType = MatchType.NONE;
            p.evidence = evidenceMap.get(c);
            // No proposed answer, no synthesised comment.
            p.comment = "";
            p.confidence = 0.0;
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

    /** Word-boundary, case-insensitive — returns every match index. */
    private List<Integer> allIndicesOfWord(String haystack, String needle) {
        String regex = "(?i)(?<![\\p{L}\\p{N}_-])"
                + Pattern.quote(needle)
                + "(?![\\p{L}\\p{N}_-])";
        Matcher m = Pattern.compile(regex).matcher(haystack);
        List<Integer> out = new ArrayList<>();
        while (m.find()) out.add(m.start());
        return out;
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
                + "document actually demonstrates. Choose ONLY from the maturity answers listed.\n\n"
                + "Calibrate confidence honestly:\n"
                + "  - >= 0.8 only when the document clearly demonstrates the control;\n"
                + "  - 0.5 to 0.8 when the document is suggestive but not conclusive;\n"
                + "  - <  0.5 when the document gives no real evidence one way or the other.\n"
                + "If confidence would be < 0.5, return answerId=null \u2014 we will leave the row\n"
                + "as \"not answered\" rather than guess.\n\n"
                + "Return a JSON array; each entry MUST be a JSON object with EXACTLY these fields:\n"
                + "  { \"controlId\": <number>, \"answerId\": <number|null>, \"comment\": <string>, \"confidence\": <number 0..1> }\n"
                + "The comment should be at most 2 sentences and quote or paraphrase the relevant evidence.\n"
                + "Leave the comment as an empty string \"\" if the document does not justify any\n"
                + "statement \u2014 do NOT invent a rationale.\n"
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
                    double conf = o.has("confidence") ? o.optDouble("confidence", 0.0) : 0.0;
                    long aid = o.optLong("answerId", -1);
                    MaturityAnswer chosen = ansById.get(aid);

                    String aiComment = o.optString("comment", "").trim();
                    p.comment = aiComment;     // empty if AI declined to comment
                    p.confidence = conf;

                    if (chosen != null && conf >= MIN_CONFIDENCE) {
                        p.proposedAnswerId = chosen.getId();
                        p.proposedAnswerName = chosen.getAnswer();
                        p.proposedRating = chosen.getRating();
                        p.matchType = MatchType.AI;
                    } else {
                        // Low confidence or AI declined — leave "not answered".
                        p.matchType = MatchType.NONE;
                    }
                } else {
                    p.comment = "";
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
        // No guess: leave each row as "not answered" with an empty comment.
        // The {@code note} parameter is retained for call-site compatibility
        // and logged below for diagnostics, but never surfaces to the user.
        if (note != null && !note.isBlank()) {
            log.debug("[import] fallbackProposals reason: {}", note);
        }
        List<Proposal> out = new ArrayList<>();
        for (SecurityControl c : batch) {
            Proposal p = new Proposal();
            p.controlId = c.getId();
            p.controlName = c.getName();
            p.controlReference = c.getReference();
            p.comment = "";
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
