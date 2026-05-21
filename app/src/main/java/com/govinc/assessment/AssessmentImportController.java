package com.govinc.assessment;

import com.govinc.authorization.AuthorizationService;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

/**
 * HTTP entry point for the "import answers from document" feature.
 *
 * <p>Two endpoints:</p>
 * <ul>
 *   <li>{@code POST /assessment/{id}/import/upload} &mdash; accepts a multipart
 *       file, returns a JSON list of proposals (one per control). No data is
 *       written to the database.</li>
 *   <li>{@code POST /assessment/{id}/import/apply} &mdash; accepts a JSON body
 *       describing which proposals the user acknowledged; applies them via the
 *       same answer/comment write path the UI normally uses.</li>
 * </ul>
 */
@RestController
@RequestMapping("/assessment/{id}/import")
public class AssessmentImportController {

    private static final Logger log = LoggerFactory.getLogger(AssessmentImportController.class);

    @Autowired private AssessmentImportService importService;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentDetailsService assessmentDetailsService;
    @Autowired private AssessmentControlAnswerRepository answerRepository;
    @Autowired private SecurityControlRepository securityControlRepository;
    @Autowired private MaturityAnswerRepository maturityAnswerRepository;
    @Autowired private AuthorizationService authorizationService;
    @Autowired private AssessmentSseService assessmentSseService;

    // ── Upload + propose ──────────────────────────────────────────────────

    @PostMapping(path = "/upload")
    public ResponseEntity<?> upload(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file) {
        if (!authorizationService.canAnswerAssessment(id)) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }
        Optional<Assessment> aOpt = assessmentRepository.findById(id);
        if (aOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Assessment not found"));
        }
        SecurityCatalog catalog = aOpt.get().getSecurityCatalog();
        if (catalog == null || catalog.getMaturityModel() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Assessment has no catalog or maturity model attached."));
        }

        try {
            List<AssessmentImportService.Proposal> proposals =
                    importService.proposeFromFile(file, catalog);

            // Also return the catalog's maturity answers so the UI can let the
            // user change the proposed answer per row.
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
    @Transactional
    public ResponseEntity<?> apply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!authorizationService.canAnswerAssessment(id)) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }
        Optional<Assessment> aOpt = assessmentRepository.findById(id);
        if (aOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Assessment not found"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No items to apply."));
        }

        // Load-or-create the AssessmentDetails for this assessment.
        AssessmentDetails details = assessmentDetailsService.findByAssessmentId(id).orElse(null);
        if (details == null) {
            details = new AssessmentDetails();
            Set<Assessment> link = new HashSet<>();
            link.add(aOpt.get());
            details.setAssessments(link);
            details.setDate(LocalDate.now());
        }

        int applied = 0;
        int skipped = 0;
        for (Map<String, Object> item : items) {
            Long controlId = toLong(item.get("controlId"));
            Long answerId  = toLong(item.get("answerId"));
            String comment = item.get("comment") == null ? null : String.valueOf(item.get("comment"));
            if (controlId == null || answerId == null) { skipped++; continue; }

            SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
            MaturityAnswer ma = maturityAnswerRepository.findById(answerId).orElse(null);
            if (control == null || ma == null) { skipped++; continue; }

            // Find existing answer-row for this control inside the details set.
            AssessmentControlAnswer existing = null;
            for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                if (aca.getSecurityControl() != null
                        && Objects.equals(aca.getSecurityControl().getId(), controlId)) {
                    existing = aca;
                    break;
                }
            }
            if (existing == null) {
                existing = new AssessmentControlAnswer(control, ma, comment);
                existing = answerRepository.save(existing);
                details.getControlAnswers().add(existing);
            } else {
                existing.setMaturityAnswer(ma);
                if (comment != null) existing.setComment(comment);
                answerRepository.save(existing);
            }
            applied++;
        }

        assessmentDetailsService.save(details);
        try {
            // Notify other viewers that this assessment changed; payload kept
            // tiny — the UI re-fetches its full state on this signal.
            assessmentSseService.broadcast(id, "import",
                    Map.of("applied", applied, "skipped", skipped));
        } catch (Exception ignored) { /* SSE is best-effort */ }

        return ResponseEntity.ok(Map.of("ok", true, "applied", applied, "skipped", skipped));
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
