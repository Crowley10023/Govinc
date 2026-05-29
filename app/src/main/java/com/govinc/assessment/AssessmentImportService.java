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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
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

/**
 * Parses a user-supplied document (PDF/DOCX/DOC/XLSX/XLS/TXT) and proposes
 * answers and comments for every {@link SecurityControl} of the assessment's
 * catalog.
 *
 * <p>Two import paths are supported:</p>
 * <ol>
 *   <li><b>Round-trip Govinc Excel re-import</b> &mdash; see
 *       {@link #tryParseExportFormat}. When the uploaded workbook matches
 *       the exact column layout produced by the assessment Excel export,
 *       answers and comments are read verbatim and written back without
 *       a review step.</li>
 *   <li><b>AI proposal</b> &mdash; for every other document type the full
 *       extracted text is sent to {@link OpenAIUtil} in batched prompts;
 *       the model returns a JSON array of proposed answers with brief
 *       comments. Persistence happens only after the user acknowledges
 *       the proposals via {@code AssessmentImportController#apply}.</li>
 * </ol>
 */
@Service
public class AssessmentImportService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentImportService.class);

    /** Hard cap on prompt size so we don't blow past provider context limits. */
    private static final int MAX_AI_TEXT_CHARS = 14_000;

    /**
     * Minimum AI-reported confidence required to actually propose a maturity
     * answer for a control. Below this, the row is left as "not answered" so
     * the user is not pushed toward a guess.
     */
    private static final double MIN_CONFIDENCE = 0.5;

    @Autowired
    private OpenAIUtil openAIUtil;

    @Autowired
    private AssessmentRepository assessmentRepository;

    public enum MatchType { EXACT, AI, NONE }

    /** One row of a re-imported Govinc export workbook, already resolved
     *  to catalog ids. Consumed by the controller to write back answers
     *  without going through the proposal review UI. */
    public static class ExportRow {
        public Long controlId;
        public Long answerId;     // may be null when the export row had no answer
        public String comment;    // never null; empty string clears any existing
        /** Mirrors the workbook's Source column: true for Override rows, false for Direct rows. Inherited rows are filtered out. */
        public boolean isOverride;
    }

    /** Result of attempting to read a file as a Govinc Excel export. */
    public static class ExportImportData {
        /** True if the workbook contains the export's header row. */
        public boolean detected;
        /** Rows that resolved to a catalog control + maturity answer. */
        public List<ExportRow> rows = new ArrayList<>();
        /** Total non-empty data rows seen (including unresolved ones). */
        public int totalRows;
    }

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

    /** Loads the assessment and returns its associated SecurityCatalog, or
     *  {@code null} if the assessment does not exist or has no catalog with
     *  a maturity model attached. Centralised here so the controller does
     *  not need its own AssessmentRepository dependency. */
    public SecurityCatalog loadCatalogForAssessment(Long assessmentId) {
        if (assessmentId == null) return null;
        return assessmentRepository.findById(assessmentId)
                .map(Assessment::getSecurityCatalog)
                .filter(c -> c != null && c.getMaturityModel() != null)
                .orElse(null);
    }

    /** True when an Assessment with the given id exists. */
    public boolean assessmentExists(Long assessmentId) {
        return assessmentId != null && assessmentRepository.existsById(assessmentId);
    }

    /**
     * Convenience wrapper around {@link #tryParseExportFormat(MultipartFile, SecurityCatalog)}
     * that loads the catalog itself from the assessment id. Returns an empty
     * (non-detected) result when the assessment or its catalog is missing.
     */
    public ExportImportData parseExportForAssessment(Long assessmentId, MultipartFile file) throws IOException {
        SecurityCatalog catalog = loadCatalogForAssessment(assessmentId);
        if (catalog == null) return new ExportImportData();
        return tryParseExportFormat(file, catalog);
    }

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

        // AI-only: hand every control + the document text to the model and let
        // it decide the maturity answer and a justifying comment. Exact-token
        // reference matching is intentionally not used here — that path is
        // reserved for the Govinc Excel export re-import (see
        // {@link #tryParseExportFormat}). For arbitrary user-supplied
        // documents we rely solely on the AI auditor.
        List<Proposal> proposals = new ArrayList<>();
        if (!controls.isEmpty()) {
            String trimmedText = truncate(text, MAX_AI_TEXT_CHARS);
            int batchSize = 10;
            for (int i = 0; i < controls.size(); i += batchSize) {
                List<SecurityControl> batch = controls.subList(i, Math.min(i + batchSize, controls.size()));
                proposals.addAll(aiMatch(batch, trimmedText, answers));
            }
        }

        // Preserve original catalog ordering.
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < controls.size(); i++) order.put(controls.get(i).getId(), i);
        proposals.sort(Comparator.comparingInt(p -> order.getOrDefault(p.controlId, Integer.MAX_VALUE)));
        return proposals;
    }

    // ── Round-trip re-import of the Govinc Excel export ──────────────────

    /**
     * Header labels that identify the Govinc Excel export. When all of these
     * appear in a single row of the workbook, the file is treated as a
     * round-trip re-import and answers/comments are written verbatim — no
     * AI fallback, no review step.
     */
    /**
     * Header labels that the Govinc Excel export writes. The detection
     * routine only requires the subset listed in {@link #EXPORT_REQUIRED_HEADERS};
     * the rest are recognised when present.
     */
    private static final String[] EXPORT_HEADERS = {
            "Domain", "Reference", "Control Name", "Description",
            "Answer", "Score (%)", "Source", "Comment"
    };

    /** Columns whose joint presence in a single row marks the workbook as
     *  a Govinc export (and triggers the round-trip re-import). Kept small
     *  on purpose so manually-edited exports still match. */
    private static final String[] EXPORT_REQUIRED_HEADERS = {
            "Domain", "Reference", "Control Name", "Answer", "Source"
    };

    /**
     * Inspects {@code file} and, when it matches the Govinc Excel export
     * layout, resolves every data row to a catalog control + maturity answer.
     *
     * <p>The returned {@link ExportImportData#rows} contain only rows that
     * successfully resolved against {@code catalog} — unknown controls,
     * unknown maturity answers and rows with no answer cell are skipped.
     * The caller is expected to write them back through the same answer
     * persistence path the apply endpoint uses.</p>
     *
     * <p>If the file is not an .xlsx/.xls or no header row is found,
     * {@link ExportImportData#detected} is {@code false} and the caller
     * should fall back to the AI proposal flow.</p>
     */
    public ExportImportData tryParseExportFormat(MultipartFile file, SecurityCatalog catalog) throws IOException {
        ExportImportData out = new ExportImportData();
        if (file == null || file.isEmpty()) return out;
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            return out;
        }
        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) return out;
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            // Locate the header row. We require Domain + Reference +
            // Control Name + Description (see EXPORT_REQUIRED_HEADERS);
            // any other recognised labels we encounter are remembered so
            // we can read Answer / Comment columns from rows below.
            int headerRowIdx = -1;
            Map<String, Integer> colByLabel = new HashMap<>();
            for (Row r : sheet) {
                Map<String, Integer> found = new HashMap<>();
                for (Cell c : r) {
                    String v = fmt.formatCellValue(c);
                    if (v == null) continue;
                    String s = v.trim();
                    for (String h : EXPORT_HEADERS) {
                        if (s.equalsIgnoreCase(h)) {
                            found.putIfAbsent(h, c.getColumnIndex());
                            break;
                        }
                    }
                }
                boolean hasAllRequired = true;
                for (String required : EXPORT_REQUIRED_HEADERS) {
                    if (!found.containsKey(required)) { hasAllRequired = false; break; }
                }
                if (hasAllRequired) {
                    headerRowIdx = r.getRowNum();
                    colByLabel = found;
                    break;
                }
            }
            if (headerRowIdx < 0) return out;
            out.detected = true;

            // Build catalog lookup maps. Many catalogs (e.g. NIS2) share the
            // same Reference and Control Name across many SecurityControls
            // and only the Description (= SecurityControl.detail) is unique
            // per row. So we:
            //   1. Index by Description as the PRIMARY key (most specific).
            //   2. Index by a composite "ref || name || desc" key for
            //      tie-breaking when descriptions differ in whitespace etc.
            //   3. Only fall back to Reference / Name when they are unique
            //      across the catalog \u2014 otherwise we would silently
            //      collapse N export rows onto the same SecurityControl,
            //      which is exactly the 161-matched/60-written bug.
            Map<String, SecurityControl> ctlByDesc     = new HashMap<>();
            Map<String, SecurityControl> ctlByCompound = new HashMap<>();
            Map<String, SecurityControl> ctlByRef      = new HashMap<>();
            Map<String, SecurityControl> ctlByName     = new HashMap<>();
            Set<String> ambiguousRefs  = new HashSet<>();
            Set<String> ambiguousNames = new HashSet<>();
            Set<String> ambiguousDescs = new HashSet<>();
            for (SecurityControl c : catalog.getSecurityControls()) {
                String kRef  = normaliseKey(c.getReference());
                String kName = normaliseKey(c.getName());
                String kDesc = normaliseKey(c.getDetail());
                if (!kDesc.isEmpty()) {
                    if (ctlByDesc.putIfAbsent(kDesc, c) != null) {
                        ambiguousDescs.add(kDesc);
                    }
                }
                String kCompound = kRef + "||" + kName + "||" + kDesc;
                if (!kCompound.equals("||||")) {
                    ctlByCompound.putIfAbsent(kCompound, c);
                }
                if (!kRef.isEmpty()) {
                    if (ctlByRef.putIfAbsent(kRef, c) != null) {
                        ambiguousRefs.add(kRef);
                    }
                }
                if (!kName.isEmpty()) {
                    if (ctlByName.putIfAbsent(kName, c) != null) {
                        ambiguousNames.add(kName);
                    }
                }
            }
            // Purge ambiguous single-field keys so they cannot be used to
            // resolve a row \u2014 the compound key (or description) must
            // win in that case.
            for (String k : ambiguousRefs)  ctlByRef.remove(k);
            for (String k : ambiguousNames) ctlByName.remove(k);
            for (String k : ambiguousDescs) ctlByDesc.remove(k);
            Map<String, MaturityAnswer> ansByName = new HashMap<>();
            for (MaturityAnswer a : catalog.getMaturityModel().getMaturityAnswers()) {
                String k = normaliseKey(a.getAnswer());
                if (!k.isEmpty()) ansByName.putIfAbsent(k, a);
            }

            int refCol  = colByLabel.getOrDefault("Reference", -1);
            int nameCol = colByLabel.getOrDefault("Control Name", -1);
            int descCol = colByLabel.getOrDefault("Description", -1);
            int ansCol  = colByLabel.getOrDefault("Answer", -1);
            int srcCol  = colByLabel.getOrDefault("Source", -1);
            int cmtCol  = colByLabel.getOrDefault("Comment", -1);

            // Track which catalog controls we have already resolved during
            // this import. The Govinc export writes one row per
            // SecurityControl, so a second row hitting the same controlId
            // means we just resolved two distinct export rows to the same
            // catalog entry (typically because Description matching failed
            // and the row collapsed onto a duplicate Reference / Name).
            // Counting that as "matched" would reproduce the historical
            // 161-matched / 60-written discrepancy, so we skip it instead
            // and surface it as totalRows - rows.size().
            Set<Long> seenControlIds = new HashSet<>();

            for (Row r : sheet) {
                if (r.getRowNum() <= headerRowIdx) continue;
                String ref  = cellText(r, refCol, fmt);
                String cn   = cellText(r, nameCol, fmt);
                String desc = cellText(r, descCol, fmt);
                String ans  = cellText(r, ansCol, fmt);
                String src  = cellText(r, srcCol, fmt);
                String cmt  = cellText(r, cmtCol, fmt);

                boolean hasAnyKey = !normaliseKey(ref).isEmpty()
                        || !normaliseKey(cn).isEmpty()
                        || !normaliseKey(desc).isEmpty();
                if (!hasAnyKey) continue;
                out.totalRows++;

                // Skip rows that were "Not answered" on export.
                if (ans == null || ans.isBlank()) continue;

                // Skip controls inherited from an org service. The org-service
                // supplies the value at runtime; writing a local row would
                // silently flip "taken over" to "direct".
                String srcNorm = normaliseKey(src);
                if (srcNorm.startsWith("inherited from")) continue;

                SecurityControl ctl = null;
                String kRef  = normaliseKey(ref);
                String kName = normaliseKey(cn);
                String kDesc = normaliseKey(desc);
                // 1) Description is the most specific row identifier in
                //    Govinc exports \u2014 try it first.
                if (!kDesc.isEmpty()) ctl = ctlByDesc.get(kDesc);
                // 2) Composite key disambiguates the (common) case where
                //    Description duplicates exist but the full triple is
                //    unique.
                if (ctl == null) {
                    String kCompound = kRef + "||" + kName + "||" + kDesc;
                    if (!kCompound.equals("||||")) ctl = ctlByCompound.get(kCompound);
                }
                // 3) Reference / Name only when they are globally unique
                //    in the catalog (ambiguous ones were purged above).
                if (ctl == null && !kRef.isEmpty())  ctl = ctlByRef.get(kRef);
                if (ctl == null && !kName.isEmpty()) ctl = ctlByName.get(kName);
                if (ctl == null) continue;

                MaturityAnswer ma = ansByName.get(normaliseKey(ans));
                if (ma == null) continue;

                if (!seenControlIds.add(ctl.getId())) {
                    // Two rows resolved to the same catalog control \u2014
                    // do not overwrite the first one. The discrepancy
                    // shows up in the modal as (totalRows - matched).
                    log.debug("[import] duplicate row collapsed onto control id={} (ref='{}', desc='{}')",
                            ctl.getId(), ref, desc);
                    continue;
                }

                ExportRow er = new ExportRow();
                er.controlId = ctl.getId();
                er.answerId  = ma.getId();
                er.comment   = cmt == null ? "" : cmt;
                // Source column "Override (overriding: â€¦)" â†’ isOverride=true.
                // "Direct" / blank / anything else â†’ isOverride=false.
                // ("Inherited from: â€¦" rows were already filtered out above.)
                er.isOverride = srcNorm.startsWith("override");
                out.rows.add(er);
            }
        } catch (Exception e) {
            // If reading the workbook fails for any reason, treat it as a
            // non-export file and let the caller fall back to AI parsing.
            log.debug("[import] not an export workbook ({}): {}", name, e.getMessage());
            ExportImportData empty = new ExportImportData();
            return empty;
        }
        return out;
    }

    private static String cellText(Row row, int col, DataFormatter fmt) {
        if (row == null || col < 0) return "";
        Cell c = row.getCell(col);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC) {
            // Numeric cells (e.g. Score column) — format without trailing .0
            double d = c.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
            return Double.toString(d);
        }
        return fmt.formatCellValue(c);
    }

    /**
     * Normalises a string for lenient matching: Unicode NFKC, replaces
     * non-breaking spaces, collapses whitespace, trims, lower-cases.
     * Returns {@code ""} for null/blank input.
     */
    private static String normaliseKey(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
        n = n.replace('\u00A0', ' ');
        n = n.replaceAll("\\s+", " ").trim();
        return n.toLowerCase(Locale.ROOT);
    }

    // ── AI matching ───────────────────────────────────────────────────────

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
