package com.govinc.assessment;

import com.govinc.authorization.AuthorizationService;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.maturity.MaturityAnswer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * HTTP entry point for the "import answers from document" feature.
 *
 * <p>The controller deliberately performs <b>no database access of its own</b>.
 * All reads go through {@link AssessmentImportService} and all writes go
 * through the standard {@link AssessmentController#saveAnswer} /
 * {@link AssessmentController#saveComment} endpoints, so the import inherits
 * exactly the same persistence, authorization, inheritance and SSE-broadcast
 * behaviour as the manual UI.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /assessment/{id}/import/upload} — accepts a multipart
 *       file. If it is a Govinc Excel export, answers + comments are written
 *       back row-by-row and the response carries {@code matched / written /
 *       skipped} counts that the modal renders verbatim. Otherwise the file
 *       is parsed by the AI auditor and a list of per-control proposals is
 *       returned for review.</li>
 *   <li>{@code POST /assessment/{id}/import/apply} — applies acknowledged
 *       proposals via the same per-row write path.</li>
 * </ul>
 */
@RestController
@RequestMapping("/assessment/{id}/import")
public class AssessmentImportController {

    private static final Logger log = LoggerFactory.getLogger(AssessmentImportController.class);

    @Autowired private AssessmentImportService importService;
    @Autowired private AuthorizationService authorizationService;
    @Autowired private AssessmentSseService assessmentSseService;
    @Autowired private AssessmentController assessmentController;

    // ── Upload + propose ──────────────────────────────────────────────────

    @PostMapping(path = "/upload")
    public ResponseEntity<?> upload(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file) {
        if (!authorizationService.canAnswerAssessment(id)) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }
        if (!importService.assessmentExists(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Assessment not found"));
        }
        SecurityCatalog catalog = importService.loadCatalogForAssessment(id);
        if (catalog == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Assessment has no catalog or maturity model attached."));
        }

        // Round-trip path: when the user uploads the workbook produced by the
        // assessment Excel export, write the answers/comments back verbatim
        // without a proposal review step.
        try {
            AssessmentImportService.ExportImportData exportData =
                    importService.parseExportForAssessment(id, file);
            if (exportData.detected) {
                ImportSummary summary = applyExportRows(id, exportData.rows);
                try {
                    assessmentSseService.broadcast(id, "import",
                            Map.of("answers", summary.answers,
                                   "comments", summary.comments));
                } catch (Exception ignored) { /* SSE is best-effort */ }
                log.info("[import] excel apply for assessment {} → rows={}, matched={}, written={}, skipped={}",
                        id, exportData.totalRows, exportData.rows.size(),
                        summary.answers, summary.skipped);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("directApply", true);
                body.put("fileName", file.getOriginalFilename());
                // Counters surfaced to the modal:
                //   rows     – non-empty data rows in the workbook
                //   matched  – rows that resolved to a unique catalog control + maturity answer
                //   written  – rows actually persisted via saveAnswer/saveComment
                //   skipped  – matched - written (writes that returned non-"ok")
                body.put("rows", exportData.totalRows);
                body.put("matched", exportData.rows.size());
                body.put("written", summary.answers);
                body.put("skipped", summary.skipped);
                body.put("comments", summary.comments);
                body.put("totalRows", exportData.totalRows);
                // backwards-compat aliases
                body.put("answers", summary.answers);
                body.put("applied", summary.answers);
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            // Failure here means the workbook was not a Govinc export — fall
            // through to the AI proposal flow rather than aborting the upload.
            log.debug("[import] export-format probe failed for assessment {}: {}", id, e.getMessage());
        }

        try {
            List<AssessmentImportService.Proposal> proposals =
                    importService.proposeFromFile(file, catalog);

            // Also return the catalog's maturity answers so the UI can let
            // the user change the proposed answer per row.
            List<Map<String, Object>> maturityAnswers = new ArrayList<>();
            for (MaturityAnswer ma : catalog.getMaturityModel().getMaturityAnswers()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", ma.getId());
                m.put("name", ma.getAnswer());
                m.put("rating", ma.getRating());
                maturityAnswers.add(m);
            }
            maturityAnswers.sort(Comparator.comparingInt(m -> (int) m.get("rating")));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("directApply", false);
            body.put("fileName", file.getOriginalFilename());
            body.put("proposalCount", proposals.size());
            body.put("maturityAnswers", maturityAnswers);
            body.put("proposals", proposals.stream().map(this::toJson).toList());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.warn("[import] upload failed for assessment {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Apply acknowledged proposals ──────────────────────────────────────

    /**
     * Body schema:
     * <pre>{ "items": [ { "controlId": 1, "answerId": 2, "comment": "..." }, ... ] }</pre>
     * Items with {@code answerId == null} are skipped. An empty {@code comment}
     * clears any existing comment.
     */
    @PostMapping(path = "/apply")
    public ResponseEntity<?> apply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!authorizationService.canAnswerAssessment(id)) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }
        if (!importService.assessmentExists(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Assessment not found"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No items to apply."));
        }

        ImportSummary s = new ImportSummary();
        int matched = 0;
        for (Map<String, Object> item : items) {
            Long controlId = toLong(item.get("controlId"));
            Long answerId  = toLong(item.get("answerId"));
            String comment = item.get("comment") == null ? null : String.valueOf(item.get("comment"));
            if (controlId != null && answerId != null) matched++;
            writeOneRow(id, controlId, answerId, comment, s);
        }
        try {
            assessmentSseService.broadcast(id, "import",
                    Map.of("applied", s.answers, "comments", s.comments, "skipped", s.skipped));
        } catch (Exception ignored) { /* SSE is best-effort */ }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("matched", matched);
        resp.put("written", s.answers);
        resp.put("skipped", s.skipped);
        resp.put("comments", s.comments);
        // backwards-compat alias
        resp.put("applied", s.answers);
        return ResponseEntity.ok(resp);
    }

    // ── Shared write path: delegate to the standard AssessmentController ──

    /**
     * Writes back all rows resolved from a Govinc Excel re-import by
     * delegating each row to the same controller methods the manual UI
     * uses ({@code POST /{id}/answer} and
     * {@code PUT /{id}/control/{cid}/comment}). The import therefore
     * inherits exactly the persistence, authorization, inheritance and
     * SSE-broadcast behaviour of the regular write path.
     *
     * <p><b>Override flag policy:</b> the import passes {@code isOverride=null}
     * so {@link AssessmentController#saveAnswer} preserves whatever flag the
     * row already has. Re-importing therefore cannot flip a control's
     * "taken over from org service" status — only its answer and comment
     * values are touched.</p>
     */
    private ImportSummary applyExportRows(Long id,
                                          List<AssessmentImportService.ExportRow> rows) {
        ImportSummary s = new ImportSummary();
        for (AssessmentImportService.ExportRow r : rows) {
            writeOneRow(id, r.controlId, r.answerId, r.comment, s);
        }
        return s;
    }

    /**
     * Persists one (controlId, answerId, comment) tuple via the standard
     * AssessmentController endpoints and updates {@code s}. No direct
     * repository access — the controller does not own any JPA dependencies.
     */
    private void writeOneRow(Long assessmentId, Long controlId, Long answerId,
                             String comment, ImportSummary s) {
        if (controlId == null || answerId == null) { s.skipped++; return; }

        // 1) Answer — isOverride=null preserves the existing override flag,
        //    so the import never alters "taken over" status.
        String res;
        try {
            res = assessmentController.saveAnswer(assessmentId, controlId, answerId, null);
        } catch (Exception e) {
            log.warn("[import] saveAnswer failed for assessment {} control {}: {}",
                    assessmentId, controlId, e.toString());
            res = "fail";
        }
        if (!"ok".equals(res)) { s.skipped++; return; }
        s.answers++;

        // 2) Comment — only when non-blank. We never blank existing comments.
        if (comment != null && !comment.isBlank()) {
            String cres;
            try {
                cres = assessmentController.saveComment(assessmentId, controlId,
                        Map.of("comment", comment));
            } catch (Exception e) {
                log.warn("[import] saveComment failed for assessment {} control {}: {}",
                        assessmentId, controlId, e.toString());
                cres = "fail";
            }
            if ("ok".equals(cres)) s.comments++;
        }
    }

    private static final class ImportSummary {
        int answers;
        int comments;
        int skipped;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Map<String, Object> toJson(AssessmentImportService.Proposal p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("controlId", p.controlId);
        m.put("controlName", p.controlName);
        m.put("controlReference", p.controlReference);
        m.put("proposedAnswerId", p.proposedAnswerId);
        m.put("proposedAnswerName", p.proposedAnswerName);
        m.put("proposedRating", p.proposedRating);
        m.put("comment", p.comment);
        m.put("evidence", p.evidence);
        m.put("matchType", p.matchType == null ? "NONE" : p.matchType.name());
        m.put("confidence", p.confidence);
        return m;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o).trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
