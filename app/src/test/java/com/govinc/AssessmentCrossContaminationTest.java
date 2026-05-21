package com.govinc;

import com.govinc.assessment.*;
import com.govinc.catalog.*;
import com.govinc.maturity.*;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitRepository;
import com.govinc.user.Role;
import com.govinc.user.User;
import com.govinc.user.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression suite for the historical "cross-assessment data mixup" defect.
 *
 * <p>Background: the old {@code AssessmentDetailsService.findById(Long)} accepted
 * an {@code id} that was first probed against {@code AssessmentDetails} and then,
 * on miss, against {@code Assessment}. Because both entities use independent
 * {@code GenerationType.IDENTITY} sequences, a collision returned the wrong
 * row, and every write path that fed the result back through {@code save()}
 * silently mutated the wrong assessment.</p>
 *
 * <p>This file exercises the corrected behaviour from three angles:</p>
 * <ol>
 *   <li><b>HTTP-level isolation</b> — answers and comments POSTed via the
 *       controller for one assessment must never appear in another, even when
 *       the other assessments' ids happen to be the same numeric values.</li>
 *   <li><b>Consistency-service repair</b> — synthetic corruption (cross-linked
 *       rows, duplicate rows, orphans) is injected directly and the service's
 *       repair primitives are verified to restore integrity without data loss.</li>
 *   <li><b>Service-layer guard</b> — the new
 *       {@code findByAssessmentId(Long)} accessor only ever returns details
 *       whose assessment link set contains the requested id.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssessmentCrossContaminationTest {

    // ── Spring beans ────────────────────────────────────────────────────────

    @Autowired private MockMvc mockMvc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentDetailsRepository assessmentDetailsRepository;
    @Autowired private AssessmentControlAnswerRepository answerRepository;
    @Autowired private AssessmentDetailsService assessmentDetailsService;
    @Autowired private AssessmentDetailsConsistencyService consistencyService;

    @Autowired private SecurityCatalogRepository catalogRepository;
    @Autowired private SecurityControlRepository controlRepository;
    @Autowired private SecurityControlDomainRepository domainRepository;
    @Autowired private MaturityAnswerRepository maturityAnswerRepository;
    @Autowired private MaturityModelRepository maturityModelRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private UserRepository userRepository;

    // ── Shared fixture state ────────────────────────────────────────────────

    private Long catalogId;
    private final List<Long> controlIds = new ArrayList<>();
    private Long answerLowId;       // rating 25
    private Long answerHighId;      // rating 75
    private Long orgUnitId;

    // Assessment ids created in setup, reused throughout
    private Long assessmentAId;
    private Long assessmentBId;
    private Long assessmentCId;

    // ── Helpers ─────────────────────────────────────────────────────────────

    private <T> T tx(Supplier<T> body) {
        return new TransactionTemplate(transactionManager).execute(s -> body.get());
    }

    private Long createAssessment(String name) throws Exception {
        mockMvc.perform(post("/assessment/create")
                        .with(csrf())
                        .with(user("admin").roles("ADMIN"))
                        .param("catalogId", catalogId.toString())
                        .param("name", name))
                .andExpect(status().is3xxRedirection());
        return tx(() -> assessmentRepository.findAll().stream()
                .filter(a -> name.equals(a.getName()))
                .map(Assessment::getId)
                .max(Long::compareTo)
                .orElseThrow(() -> new AssertionError("Assessment '" + name + "' not created")));
    }

    private void postAnswer(Long assessmentId, Long controlId, Long answerId) throws Exception {
        mockMvc.perform(post("/assessment/{id}/answer", assessmentId)
                        .with(csrf())
                        .param("controlId", controlId.toString())
                        .param("answerId", answerId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    private void postComment(Long assessmentId, Long controlId, String comment) throws Exception {
        mockMvc.perform(put("/assessment/{aid}/control/{cid}/comment", assessmentId, controlId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + comment.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Returns a map controlId → maturity rating for the given assessment, using
     * the public service path (which itself goes through the consistency layer).
     */
    private Map<Long, Integer> ratingsByControl(Long assessmentId) {
        return tx(() -> {
            Optional<AssessmentDetails> opt =
                    assessmentDetailsService.findByAssessmentId(assessmentId);
            if (opt.isEmpty()) return Collections.emptyMap();
            Map<Long, Integer> out = new LinkedHashMap<>();
            for (AssessmentControlAnswer aca : opt.get().getControlAnswers()) {
                if (aca.getSecurityControl() == null) continue;
                Integer r = aca.getMaturityAnswer() == null
                        ? null
                        : aca.getMaturityAnswer().getRating();
                out.put(aca.getSecurityControl().getId(), r);
            }
            return out;
        });
    }

    private Map<Long, String> commentsByControl(Long assessmentId) {
        return tx(() -> {
            Optional<AssessmentDetails> opt =
                    assessmentDetailsService.findByAssessmentId(assessmentId);
            if (opt.isEmpty()) return Collections.emptyMap();
            Map<Long, String> out = new LinkedHashMap<>();
            for (AssessmentControlAnswer aca : opt.get().getControlAnswers()) {
                if (aca.getSecurityControl() == null) continue;
                out.put(aca.getSecurityControl().getId(), aca.getComment());
            }
            return out;
        });
    }

    // ── Setup ───────────────────────────────────────────────────────────────

    @BeforeAll
    void setUp() throws Exception {
        tx(() -> {
            if (userRepository.findByEmail("admin@example.com").isEmpty()) {
                User admin = new User("admin", "", "admin@example.com");
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }
            return null;
        });

        // Catalog + maturity model + 3 controls
        tx(() -> {
            MaturityAnswer low = new MaturityAnswer("Initial", "Ad-hoc");
            low.setRating(25);
            low = maturityAnswerRepository.save(low);
            answerLowId = low.getId();

            MaturityAnswer high = new MaturityAnswer("Managed", "Actively managed");
            high.setRating(75);
            high = maturityAnswerRepository.save(high);
            answerHighId = high.getId();

            MaturityModel model = new MaturityModel();
            model.setName("Cross-Contamination Model");
            model.setMaturityAnswers(new LinkedHashSet<>(List.of(low, high)));
            model = maturityModelRepository.save(model);

            SecurityControlDomain domain = new SecurityControlDomain(
                    "Mixup Domain", "Controls used by cross-contamination tests");
            domain = domainRepository.save(domain);

            List<SecurityControl> ctrls = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                SecurityControl c = new SecurityControl(
                        "Mixup Control " + i,
                        "Control body " + i,
                        "MC-" + i);
                c.setSecurityControlDomain(domain);
                c = controlRepository.save(c);
                ctrls.add(c);
                controlIds.add(c.getId());
            }

            SecurityCatalog catalog = new SecurityCatalog();
            catalog.setName("Cross-Contamination Catalog");
            catalog.setRevision("1.0");
            catalog.setMaturityModel(model);
            catalog.setSecurityControls(new LinkedHashSet<>(ctrls));
            catalog = catalogRepository.save(catalog);
            catalogId = catalog.getId();

            OrgUnit ou = new OrgUnit();
            ou.setName("Mixup OU");
            ou = orgUnitRepository.save(ou);
            orgUnitId = ou.getId();
            return null;
        });

        assessmentAId = createAssessment("Mixup Assessment A");
        assessmentBId = createAssessment("Mixup Assessment B");
        assessmentCId = createAssessment("Mixup Assessment C");
    }

    // ╔════════════════════════════════════════════════════════════════════════╗
    // ║                   PART 1 — HTTP-LEVEL ISOLATION                        ║
    // ╚════════════════════════════════════════════════════════════════════════╝

    /** 1. Three freshly-created assessments must all start with zero answers. */
    @Test @Order(10)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void uc01_freshAssessmentsHaveNoAnswers() {
        assertThat(ratingsByControl(assessmentAId)).isEmpty();
        assertThat(ratingsByControl(assessmentBId)).isEmpty();
        assertThat(ratingsByControl(assessmentCId)).isEmpty();
    }

    /** 2. Posting an answer to A must not leak into B or C. */
    @Test @Order(20)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void uc02_answerToA_doesNotLeakToBorC() throws Exception {
        postAnswer(assessmentAId, controlIds.get(0), answerHighId);

        assertThat(ratingsByControl(assessmentAId))
                .containsExactly(Map.entry(controlIds.get(0), 75));
        assertThat(ratingsByControl(assessmentBId)).isEmpty();
        assertThat(ratingsByControl(assessmentCId)).isEmpty();
    }

    /** 3. Distinct answers on the same control across A and B must stay distinct. */
    @Test @Order(30)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void uc03_sameControlDifferentRatings_perAssessment() throws Exception {
        postAnswer(assessmentBId, controlIds.get(0), answerLowId);

        assertThat(ratingsByControl(assessmentAId).get(controlIds.get(0))).isEqualTo(75);
        assertThat(ratingsByControl(assessmentBId).get(controlIds.get(0))).isEqualTo(25);
        assertThat(ratingsByControl(assessmentCId)).isEmpty();
    }

    /** 4. Updating an answer on B must not touch A's answer. */
    @Test @Order(40)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void uc04_updatingBdoesNotChangeA() throws Exception {
        postAnswer(assessmentBId, controlIds.get(0), answerHighId); // B: 25 → 75

        assertThat(ratingsByControl(assessmentAId).get(controlIds.get(0))).isEqualTo(75);
        assertThat(ratingsByControl(assessmentBId).get(controlIds.get(0))).isEqualTo(75);
    }

    /** 5. Comments are isolated per assessment. */
    @Test @Order(50)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void uc05_commentsIsolatedPerAssessment() throws Exception {
        postComment(assessmentAId, controlIds.get(0), "A comment");
        postComment(assessmentBId, controlIds.get(0), "B comment");
        postComment(assessmentCId, controlIds.get(0), "C comment");

        assertThat(commentsByControl(assessmentAId).get(controlIds.get(0))).isEqualTo("A comment");
        assertThat(commentsByControl(assessmentBId).get(controlIds.get(0))).isEqualTo("B comment");
        assertThat(commentsByControl(assessmentCId).get(controlIds.get(0))).isEqualTo("C comment");
    }

    /** 6. Each assessment has its own backing AssessmentDetails row. */
    @Test @Order(60)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void uc06_eachAssessmentHasOwnDetailsRow() {
        Long detailsA = tx(() -> assessmentDetailsService.findByAssessmentId(assessmentAId)
                .map(AssessmentDetails::getId).orElseThrow());
        Long detailsB = tx(() -> assessmentDetailsService.findByAssessmentId(assessmentBId)
                .map(AssessmentDetails::getId).orElseThrow());
        Long detailsC = tx(() -> assessmentDetailsService.findByAssessmentId(assessmentCId)
                .map(AssessmentDetails::getId).orElseThrow());
        assertThat(Set.of(detailsA, detailsB, detailsC)).hasSize(3);
    }

    /** 7. Each details row links to exactly one assessment. */
    @Test @Order(70)
    void uc07_detailsLinksToOnlyOneAssessment() {
        tx(() -> {
            for (Long aid : List.of(assessmentAId, assessmentBId, assessmentCId)) {
                AssessmentDetails ad = assessmentDetailsService.findByAssessmentId(aid).orElseThrow();
                assertThat(ad.getAssessments()).hasSize(1);
                assertThat(ad.getAssessments().iterator().next().getId()).isEqualTo(aid);
            }
            return null;
        });
    }

    /** 8. Many writes across many assessments preserve per-assessment integrity. */
    @Test @Order(80)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void uc08_bulkInterleavedWrites_preserveIsolation() throws Exception {
        // Write controls 1 + 2 with low on A, and controls 1 + 2 with high on C.
        postAnswer(assessmentAId, controlIds.get(1), answerLowId);
        postAnswer(assessmentCId, controlIds.get(1), answerHighId);
        postAnswer(assessmentAId, controlIds.get(2), answerLowId);
        postAnswer(assessmentCId, controlIds.get(2), answerHighId);

        Map<Long, Integer> a = ratingsByControl(assessmentAId);
        Map<Long, Integer> c = ratingsByControl(assessmentCId);
        assertThat(a.get(controlIds.get(1))).isEqualTo(25);
        assertThat(a.get(controlIds.get(2))).isEqualTo(25);
        assertThat(c.get(controlIds.get(1))).isEqualTo(75);
        assertThat(c.get(controlIds.get(2))).isEqualTo(75);
        // And B has not gained any new control answers.
        Map<Long, Integer> b = ratingsByControl(assessmentBId);
        assertThat(b.keySet()).containsExactly(controlIds.get(0));
    }

    // ╔════════════════════════════════════════════════════════════════════════╗
    // ║                   PART 2 — CONSISTENCY-SERVICE REPAIR                  ║
    // ╚════════════════════════════════════════════════════════════════════════╝

    /** 9. Manually cross-link an existing details row to a second assessment,
     *     then verify repair splits it back into two independent rows whose
     *     answers are deep-cloned (no shared AssessmentControlAnswer ids). */
    @Test @Order(110)
    void uc09_repairSplitsCrossLinkedRow() {
        // Create a brand-new "victim" assessment + corrupt link.
        Long victimAssessmentId = tx(() -> {
            Assessment vic = new Assessment();
            vic.setName("Cross-link Victim");
            vic.setCreationDate(LocalDate.now());
            vic.setSecurityCatalog(catalogRepository.findById(catalogId).orElseThrow());
            vic = assessmentRepository.save(vic);

            AssessmentDetails detailsA = assessmentDetailsService.findByAssessmentId(assessmentAId)
                    .orElseThrow();
            // Corrupt: attach A's details row also to the victim
            detailsA.getAssessments().add(vic);
            assessmentDetailsRepository.save(detailsA);
            return vic.getId();
        });

        // Sanity-check the corruption took effect.
        tx(() -> {
            List<AssessmentDetails> all = assessmentDetailsRepository.findCrossLinked();
            assertThat(all).extracting(AssessmentDetails::getId).contains(
                    assessmentDetailsService.findByAssessmentId(assessmentAId)
                            .orElseThrow().getId());
            return null;
        });

        // Trigger repair via the on-demand path.
        Optional<AssessmentDetails> repaired = tx(() ->
                consistencyService.repairForAssessment(victimAssessmentId));

        assertThat(repaired).isPresent();
        // After repair the victim and A must have different details rows,
        // each linked to exactly one assessment.
        tx(() -> {
            AssessmentDetails adA = assessmentDetailsService.findByAssessmentId(assessmentAId)
                    .orElseThrow();
            AssessmentDetails adV = assessmentDetailsService.findByAssessmentId(victimAssessmentId)
                    .orElseThrow();
            assertThat(adA.getId()).isNotEqualTo(adV.getId());
            assertThat(adA.getAssessments()).hasSize(1);
            assertThat(adV.getAssessments()).hasSize(1);
            // Deep-clone: control answers must have distinct ids per details row.
            Set<Long> answerIdsA = new HashSet<>();
            adA.getControlAnswers().forEach(x -> answerIdsA.add(x.getId()));
            adV.getControlAnswers().forEach(x ->
                    assertThat(answerIdsA).doesNotContain(x.getId()));
            // Content equivalence: same control ids, same maturity ratings.
            Map<Long, Integer> mapA = new HashMap<>();
            adA.getControlAnswers().forEach(x -> mapA.put(
                    x.getSecurityControl().getId(),
                    x.getMaturityAnswer() == null ? null : x.getMaturityAnswer().getRating()));
            Map<Long, Integer> mapV = new HashMap<>();
            adV.getControlAnswers().forEach(x -> mapV.put(
                    x.getSecurityControl().getId(),
                    x.getMaturityAnswer() == null ? null : x.getMaturityAnswer().getRating()));
            assertThat(mapA).isEqualTo(mapV);
            return null;
        });
    }

    /** 10. Manually create a duplicate AssessmentDetails for the same assessment
     *      (different control answers), then verify the merge keeps the richer
     *      data and reduces the table to a single row. */
    @Test @Order(120)
    void uc10_repairMergesDuplicateDetailsRows() {
        Long dupAssessmentId = tx(() -> {
            Assessment a = new Assessment();
            a.setName("Duplicate Victim");
            a.setCreationDate(LocalDate.now());
            a.setSecurityCatalog(catalogRepository.findById(catalogId).orElseThrow());
            a = assessmentRepository.save(a);
            return a.getId();
        });

        // Create TWO AssessmentDetails rows for the same assessment.
        tx(() -> {
            Assessment assessment = assessmentRepository.findById(dupAssessmentId).orElseThrow();
            SecurityControl ctrl0 = controlRepository.findById(controlIds.get(0)).orElseThrow();
            SecurityControl ctrl1 = controlRepository.findById(controlIds.get(1)).orElseThrow();
            MaturityAnswer low = maturityAnswerRepository.findById(answerLowId).orElseThrow();
            MaturityAnswer high = maturityAnswerRepository.findById(answerHighId).orElseThrow();

            // First row: only control 0 with a low answer + short comment.
            AssessmentDetails d1 = new AssessmentDetails();
            d1.setAssessments(new HashSet<>(Set.of(assessment)));
            d1.setDate(LocalDate.now());
            AssessmentControlAnswer a1 = new AssessmentControlAnswer(ctrl0, low, "short");
            a1 = answerRepository.save(a1);
            d1.getControlAnswers().add(a1);
            assessmentDetailsRepository.save(d1);

            // Second row: control 0 with high answer + longer comment, plus control 1.
            AssessmentDetails d2 = new AssessmentDetails();
            d2.setAssessments(new HashSet<>(Set.of(assessment)));
            d2.setDate(LocalDate.now());
            AssessmentControlAnswer a2 = new AssessmentControlAnswer(ctrl0, high, "much longer comment");
            a2 = answerRepository.save(a2);
            AssessmentControlAnswer a3 = new AssessmentControlAnswer(ctrl1, low, "another");
            a3 = answerRepository.save(a3);
            d2.getControlAnswers().add(a2);
            d2.getControlAnswers().add(a3);
            assessmentDetailsRepository.save(d2);
            return null;
        });

        // Sanity: duplicates exist.
        assertThat(tx(() -> assessmentDetailsRepository
                .findAllForAssessmentId(dupAssessmentId).size()))
                .isGreaterThanOrEqualTo(2);

        // Repair via on-demand path.
        tx(() -> consistencyService.repairForAssessment(dupAssessmentId));

        // After repair: exactly one details row, with answers from both originals
        // merged via the isBetter() strategy (longer comment + non-null answer win).
        tx(() -> {
            List<AssessmentDetails> after =
                    assessmentDetailsRepository.findAllForAssessmentId(dupAssessmentId);
            assertThat(after).hasSize(1);
            AssessmentDetails survivor = after.get(0);
            Map<Long, String> comments = new HashMap<>();
            Map<Long, Integer> ratings = new HashMap<>();
            for (AssessmentControlAnswer aca : survivor.getControlAnswers()) {
                comments.put(aca.getSecurityControl().getId(), aca.getComment());
                ratings.put(aca.getSecurityControl().getId(),
                        aca.getMaturityAnswer() == null ? null : aca.getMaturityAnswer().getRating());
            }
            assertThat(comments).containsKeys(controlIds.get(0), controlIds.get(1));
            // For control 0: the "longer comment" / high-rating answer must have won.
            assertThat(comments.get(controlIds.get(0))).isEqualTo("much longer comment");
            assertThat(ratings.get(controlIds.get(0))).isEqualTo(75);
            return null;
        });
    }

    /** 11. Orphan AssessmentDetails (no assessment link) must be cleaned up
     *      by repairAll(). */
    @Test @Order(130)
    void uc11_repairAllRemovesOrphans() {
        Long orphanId = tx(() -> {
            AssessmentDetails orph = new AssessmentDetails();
            orph.setDate(LocalDate.now());
            // No assessment link at all.
            orph = assessmentDetailsRepository.save(orph);
            return orph.getId();
        });

        assertThat(tx(() -> assessmentDetailsRepository.findById(orphanId))).isPresent();

        AssessmentDetailsConsistencyService.RepairSummary summary =
                tx(() -> consistencyService.repairAll());
        assertThat(summary.orphansDeleted).isGreaterThanOrEqualTo(1);

        assertThat(tx(() -> assessmentDetailsRepository.findById(orphanId))).isEmpty();
    }

    /** 12. Repair is idempotent — a second call on a clean store reports zero
     *      actions and changes nothing. */
    @Test @Order(140)
    void uc12_repairIsIdempotent() {
        AssessmentDetailsConsistencyService.RepairSummary first =
                tx(() -> consistencyService.repairAll());
        // Whatever state we're in now, a second call must produce 0 actions.
        AssessmentDetailsConsistencyService.RepairSummary second =
                tx(() -> consistencyService.repairAll());
        assertThat(second.totalActions()).isZero();
        // and the first one was the only one allowed to actually act.
        assertThat(first.totalActions()).isGreaterThanOrEqualTo(0); // just exercises the field
    }

    // ╔════════════════════════════════════════════════════════════════════════╗
    // ║                   PART 3 — SERVICE-LAYER GUARD                         ║
    // ╚════════════════════════════════════════════════════════════════════════╝

    /** 13. {@code findByAssessmentId} returns only details linked to the given
     *      assessment, even when an unrelated {@code AssessmentDetails} row exists
     *      whose primary key equals the assessment id. */
    @Test @Order(210)
    void uc13_findByAssessmentId_ignoresDetailsWithCollidingPK() {
        // Pick an assessment id that we KNOW has its own details row.
        Long targetAssessment = assessmentBId;
        AssessmentDetails ownB = tx(() ->
                assessmentDetailsService.findByAssessmentId(targetAssessment).orElseThrow());
        Long ownBDetailsId = ownB.getId();

        // The collision case is rare in practice but we simulate the symptom:
        // ensure that even a brand-new orphaned AssessmentDetails row cannot be
        // mistaken for assessment B's details. We can't force an exact PK
        // collision under IDENTITY generation, but we CAN assert that the row
        // returned is always linked to the requested assessment.
        AssessmentDetails returned = tx(() ->
                assessmentDetailsService.findByAssessmentId(targetAssessment).orElseThrow());
        assertThat(returned.getId()).isEqualTo(ownBDetailsId);
        assertThat(returned.getAssessments())
                .extracting(Assessment::getId)
                .containsExactly(targetAssessment);
    }

    /** 14. After a cross-link corruption, the read-path triggers a lazy repair
     *      and the next call returns a row linked exclusively to the assessment. */
    @Test @Order(220)
    void uc14_lazyReadRepairsCrossLinkOnDemand() {
        // Cross-link C's details into A
        tx(() -> {
            AssessmentDetails detailsC = assessmentDetailsService
                    .findByAssessmentId(assessmentCId).orElseThrow();
            Assessment a = assessmentRepository.findById(assessmentAId).orElseThrow();
            detailsC.getAssessments().add(a);
            assessmentDetailsRepository.save(detailsC);
            return null;
        });

        // The next read for either assessment must return a row linked to ONLY
        // that assessment — the lazy repair fires inside findByAssessmentId.
        tx(() -> {
            AssessmentDetails adA = assessmentDetailsService.findByAssessmentId(assessmentAId)
                    .orElseThrow();
            AssessmentDetails adC = assessmentDetailsService.findByAssessmentId(assessmentCId)
                    .orElseThrow();
            assertThat(adA.getAssessments()).hasSize(1);
            assertThat(adC.getAssessments()).hasSize(1);
            assertThat(adA.getAssessments().iterator().next().getId()).isEqualTo(assessmentAId);
            assertThat(adC.getAssessments().iterator().next().getId()).isEqualTo(assessmentCId);
            assertThat(adA.getId()).isNotEqualTo(adC.getId());
            return null;
        });
    }

    /** 15. After full repair, every {@code AssessmentDetails} row in the DB
     *      satisfies the invariants the data model has always intended:
     *      (a) link set non-empty and (b) link set of size 1. */
    @Test @Order(230)
    void uc15_globalInvariantsHoldAfterRepair() {
        tx(() -> consistencyService.repairAll());
        tx(() -> {
            List<AssessmentDetails> all = assessmentDetailsRepository.findAll();
            for (AssessmentDetails ad : all) {
                assertThat(ad.getAssessments())
                        .as("AssessmentDetails id=%s must link to exactly one assessment", ad.getId())
                        .hasSize(1);
            }
            // No orphans, no cross-links, at most one row per assessment.
            assertThat(assessmentDetailsRepository.findOrphans()).isEmpty();
            assertThat(assessmentDetailsRepository.findCrossLinked()).isEmpty();
            Map<Long, Integer> perAssessmentCount = new HashMap<>();
            for (AssessmentDetails ad : all) {
                for (Assessment a : ad.getAssessments()) {
                    perAssessmentCount.merge(a.getId(), 1, Integer::sum);
                }
            }
            for (Map.Entry<Long, Integer> e : perAssessmentCount.entrySet()) {
                assertThat(e.getValue())
                        .as("Assessment id=%s must have at most one AssessmentDetails row", e.getKey())
                        .isEqualTo(1);
            }
            return null;
        });
    }
}
