package com.govinc;

import com.govinc.assessment.*;
import com.govinc.catalog.*;
import com.govinc.maturity.*;
import com.govinc.user.*;
import com.govinc.organization.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for eager propagation of OrgServiceAssessment control changes
 * to linked open assessments.
 *
 * Scenario matrix:
 *   Test 1 – When a control's percent changes in an org-service assessment, the
 *             linked OPEN assessment's stored answer is updated immediately (eager,
 *             not lazy-on-view).
 *   Test 2 – CLOSED (snapshot) assessments are never touched by propagation.
 *   Test 3 – An answer with isOverride=true in an open assessment is NOT
 *             overwritten by propagation.
 *   Test 4 – When a control becomes applicable=false in an org-service assessment,
 *             the inherited answer is removed from open assessments.
 *   Test 5 – When multiple open assessments use the same org service, all of them
 *             are updated.
 *   Test 6 – Comment propagation updates open assessments that have no user comment.
 *
 * Each test uses its own dedicated SecurityControl so that the "only one org service
 * may own a control" business constraint is never violated during the test runs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrgServiceAssessmentPropagationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentDetailsRepository assessmentDetailsRepository;
    @Autowired private AssessmentControlAnswerRepository assessmentControlAnswerRepository;
    @Autowired private SecurityControlRepository securityControlRepository;
    @Autowired private SecurityControlDomainRepository securityControlDomainRepository;
    @Autowired private SecurityCatalogRepository securityCatalogRepository;
    @Autowired private MaturityAnswerRepository maturityAnswerRepository;
    @Autowired private MaturityModelRepository maturityModelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private OrgServiceRepository orgServiceRepository;
    @Autowired private OrgServiceAssessmentRepository orgServiceAssessmentRepository;
    @Autowired private OrgServiceAssessmentControlRepository orgServiceAssessmentControlRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    // Shared infrastructure
    private Long catalogId;
    private Long orgUnitId;
    private Long answer25Id;   // rating = 25
    private Long answer75Id;   // rating = 75

    // Each test gets its own dedicated control to avoid the "one-service-per-control" conflict
    private Long ctrlT1Id;
    private Long ctrlT2Id;
    private Long ctrlT3Id;
    private Long ctrlT4Id;
    private Long ctrlT5Id;
    private Long ctrlT6Id;

    // Per-test org-service / assessment IDs
    private Long orgServiceId_t1;
    private Long osaId_t1;
    private Long openAssessmentId_t1;

    private Long orgServiceId_t2;
    private Long osaId_t2;
    private Long closedAssessmentId_t2;

    private Long orgServiceId_t3;
    private Long osaId_t3;
    private Long openAssessmentId_t3;

    private Long orgServiceId_t4;
    private Long osaId_t4;
    private Long openAssessmentId_t4;

    private Long orgServiceId_t5;
    private Long osaId_t5;
    private Long openAssessmentId_t5a;
    private Long openAssessmentId_t5b;

    private Long orgServiceId_t6;
    private Long osaId_t6;
    private Long openAssessmentId_t6;

    // ─── helpers ──────────────────────────────────────────────────────────────

    private <T> T writeInTx(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> work.get());
    }

    /**
     * Loads AssessmentDetails for an assessment using the JPQL path (which eagerly
     * fetches controlAnswers with their securityControl and maturityAnswer).
     */
    private Optional<AssessmentDetails> loadDetails(Long assessmentId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status ->
                assessmentDetailsRepository.findByAssessmentId(assessmentId));
    }

    /** Reads the stored MaturityAnswer rating for a given control in an assessment's details. */
    private Integer storedRating(Long assessmentId, Long controlId) {
        return loadDetails(assessmentId)
                .map(det -> det.getControlAnswers().stream()
                        .filter(a -> a.getSecurityControl() != null &&
                                controlId.equals(a.getSecurityControl().getId()))
                        .map(a -> a.getMaturityAnswer() != null
                                ? a.getMaturityAnswer().getRating() : null)
                        .findFirst()
                        .orElse(null))
                .orElse(null);
    }

    /** Returns true when the control answer exists (with a maturity answer) in stored details. */
    private boolean hasStoredAnswer(Long assessmentId, Long controlId) {
        return loadDetails(assessmentId)
                .map(det -> det.getControlAnswers().stream()
                        .anyMatch(a -> a.getSecurityControl() != null &&
                                controlId.equals(a.getSecurityControl().getId()) &&
                                a.getMaturityAnswer() != null))
                .orElse(false);
    }

    /** Reads the stored comment for a given control in an assessment's details. */
    private String storedComment(Long assessmentId, Long controlId) {
        return loadDetails(assessmentId)
                .flatMap(det -> det.getControlAnswers().stream()
                        .filter(a -> a.getSecurityControl() != null &&
                                controlId.equals(a.getSecurityControl().getId()))
                        .findFirst())
                .map(AssessmentControlAnswer::getComment)
                .orElse(null);
    }

    /**
     * Creates an OrgServiceAssessment for the given service with one control entry.
     * Uses the repository directly (no business-rule validation) so tests can set up
     * state that the service layer would otherwise reject.
     */
    private Long createOrgServiceAssessment(Long orgServiceId, Long controlId,
                                            boolean applicable, int percent) {
        return writeInTx(() -> {
            OrgService svc = orgServiceRepository.findById(orgServiceId).orElseThrow();
            SecurityControl ctrl = securityControlRepository.findById(controlId).orElseThrow();
            OrgServiceAssessment osa = new OrgServiceAssessment(svc, LocalDate.now());
            osa = orgServiceAssessmentRepository.save(osa);
            OrgServiceAssessmentControl osac = new OrgServiceAssessmentControl();
            osac.setSecurityControl(ctrl);
            osac.setApplicable(applicable);
            osac.setPercent(percent);
            osac.setOrgServiceAssessment(osa);
            orgServiceAssessmentControlRepository.save(osac);
            return osa.getId();
        });
    }

    /**
     * Creates an open assessment linked to the given org service and optionally
     * seeds an AssessmentDetails with one control answer.
     */
    private Long createOpenAssessment(String name, Long orgServiceId, Long controlId,
                                      Long seedAnswerId, boolean seedOverride) {
        return writeInTx(() -> {
            SecurityCatalog cat = securityCatalogRepository.findById(catalogId).orElseThrow();
            OrgUnit unit = orgUnitRepository.findById(orgUnitId).orElseThrow();
            OrgService svc = orgServiceRepository.findById(orgServiceId).orElseThrow();
            SecurityControl ctrl = securityControlRepository.findById(controlId).orElseThrow();
            MaturityAnswer seedAnswer = maturityAnswerRepository.findById(seedAnswerId).orElseThrow();

            Assessment assessment = new Assessment(cat, LocalDate.now(), name, AssessmentStatus.OPEN);
            assessment.setOrgServices(new HashSet<>(Set.of(svc)));
            assessment.setOrgUnit(unit);
            assessment = assessmentRepository.save(assessment);

            AssessmentControlAnswer aca = new AssessmentControlAnswer(ctrl, seedAnswer);
            aca.setIsOverride(seedOverride);
            aca = assessmentControlAnswerRepository.save(aca);

            AssessmentDetails details = new AssessmentDetails();
            details.setAssessments(new HashSet<>(Set.of(assessment)));
            details.setDate(LocalDate.now());
            details.setControlAnswers(new HashSet<>(Set.of(aca)));
            assessmentDetailsRepository.save(details);

            return assessment.getId();
        });
    }

    // ─── seed ─────────────────────────────────────────────────────────────────

    @BeforeAll
    void setUp() {
        writeInTx(() -> {
            if (userRepository.findByEmail("admin@example.com").isEmpty()) {
                User admin = new User("admin", "", "admin@example.com");
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }

            MaturityAnswer a25 = new MaturityAnswer("PropInitial", "Not yet implemented");
            a25.setRating(25);
            a25 = maturityAnswerRepository.save(a25);
            answer25Id = a25.getId();

            MaturityAnswer a75 = new MaturityAnswer("PropManaged", "Actively managed");
            a75.setRating(75);
            a75 = maturityAnswerRepository.save(a75);
            answer75Id = a75.getId();

            MaturityModel model = new MaturityModel();
            model.setName("Propagation Test Model");
            model.setDescription("Used by OrgServiceAssessmentPropagationTest");
            model.setMaturityAnswers(new LinkedHashSet<>(List.of(a25, a75)));
            model = maturityModelRepository.save(model);

            SecurityControlDomain domain = new SecurityControlDomain(
                    "Propagation Test Domain", "Domain for propagation tests");
            domain = securityControlDomainRepository.save(domain);

            // One dedicated control per test to avoid constraint conflicts
            SecurityControl c1 = new SecurityControl("PropCtrl T1", "Test 1 control", "PROP-T1");
            c1.setSecurityControlDomain(domain);
            c1 = securityControlRepository.save(c1);
            ctrlT1Id = c1.getId();

            SecurityControl c2 = new SecurityControl("PropCtrl T2", "Test 2 control", "PROP-T2");
            c2.setSecurityControlDomain(domain);
            c2 = securityControlRepository.save(c2);
            ctrlT2Id = c2.getId();

            SecurityControl c3 = new SecurityControl("PropCtrl T3", "Test 3 control", "PROP-T3");
            c3.setSecurityControlDomain(domain);
            c3 = securityControlRepository.save(c3);
            ctrlT3Id = c3.getId();

            SecurityControl c4 = new SecurityControl("PropCtrl T4", "Test 4 control", "PROP-T4");
            c4.setSecurityControlDomain(domain);
            c4 = securityControlRepository.save(c4);
            ctrlT4Id = c4.getId();

            SecurityControl c5 = new SecurityControl("PropCtrl T5", "Test 5 control", "PROP-T5");
            c5.setSecurityControlDomain(domain);
            c5 = securityControlRepository.save(c5);
            ctrlT5Id = c5.getId();

            SecurityControl c6 = new SecurityControl("PropCtrl T6", "Test 6 control", "PROP-T6");
            c6.setSecurityControlDomain(domain);
            c6 = securityControlRepository.save(c6);
            ctrlT6Id = c6.getId();

            SecurityCatalog catalog = new SecurityCatalog();
            catalog.setName("Propagation Test Catalog");
            catalog.setDescription("Catalog for propagation tests");
            catalog.setRevision("1.0");
            catalog.setMaturityModel(model);
            catalog.setSecurityControls(new LinkedHashSet<>(List.of(c1, c2, c3, c4, c5, c6)));
            catalog = securityCatalogRepository.save(catalog);
            catalogId = catalog.getId();

            OrgUnit unit = new OrgUnit();
            unit.setName("Propagation Test Org Unit");
            unit = orgUnitRepository.save(unit);
            orgUnitId = unit.getId();

            return null;
        });

        // ── Test-1: percent change propagated to open assessment ──────────
        orgServiceId_t1 = writeInTx(() -> {
            OrgService svc = orgServiceRepository.save(new OrgService("PropSvc T1", "Test 1"));
            return svc.getId();
        });
        osaId_t1 = createOrgServiceAssessment(orgServiceId_t1, ctrlT1Id, true, 25);
        openAssessmentId_t1 = createOpenAssessment("PropTest1 Open", orgServiceId_t1, ctrlT1Id, answer25Id, false);

        // ── Test-2: closed assessment unaffected by propagation ────────────
        orgServiceId_t2 = writeInTx(() -> {
            OrgService svc = orgServiceRepository.save(new OrgService("PropSvc T2", "Test 2"));
            return svc.getId();
        });
        osaId_t2 = createOrgServiceAssessment(orgServiceId_t2, ctrlT2Id, true, 75);
        closedAssessmentId_t2 = writeInTx(() -> {
            SecurityCatalog cat = securityCatalogRepository.findById(catalogId).orElseThrow();
            OrgUnit unit = orgUnitRepository.findById(orgUnitId).orElseThrow();
            OrgService svc = orgServiceRepository.findById(orgServiceId_t2).orElseThrow();
            SecurityControl ctrl = securityControlRepository.findById(ctrlT2Id).orElseThrow();
            MaturityAnswer a75 = maturityAnswerRepository.findById(answer75Id).orElseThrow();

            Assessment a = new Assessment(cat, LocalDate.now(), "PropTest2 Closed", AssessmentStatus.CLOSED);
            a.setOrgServices(new HashSet<>(Set.of(svc)));
            a.setOrgUnit(unit);
            a.setSnapshotControls(new HashSet<>(cat.getSecurityControls()));
            a.setCloseDate(LocalDate.now());
            a = assessmentRepository.save(a);

            AssessmentControlAnswer aca = new AssessmentControlAnswer(ctrl, a75);
            aca = assessmentControlAnswerRepository.save(aca);

            AssessmentDetails details = new AssessmentDetails();
            details.setAssessments(new HashSet<>(Set.of(a)));
            details.setDate(LocalDate.now());
            details.setControlAnswers(new HashSet<>(Set.of(aca)));
            assessmentDetailsRepository.save(details);
            return a.getId();
        });

        // ── Test-3: isOverride=true blocks propagation ────────────────────
        orgServiceId_t3 = writeInTx(() -> {
            OrgService svc = orgServiceRepository.save(new OrgService("PropSvc T3", "Test 3"));
            return svc.getId();
        });
        osaId_t3 = createOrgServiceAssessment(orgServiceId_t3, ctrlT3Id, true, 25);
        // Override: open assessment has ctrlT3 at 75 with isOverride=true
        openAssessmentId_t3 = createOpenAssessment("PropTest3 Override", orgServiceId_t3, ctrlT3Id, answer75Id, true);

        // ── Test-4: applicable=false removes inherited answer ─────────────
        orgServiceId_t4 = writeInTx(() -> {
            OrgService svc = orgServiceRepository.save(new OrgService("PropSvc T4", "Test 4"));
            return svc.getId();
        });
        osaId_t4 = createOrgServiceAssessment(orgServiceId_t4, ctrlT4Id, true, 75);
        openAssessmentId_t4 = createOpenAssessment("PropTest4 Removal", orgServiceId_t4, ctrlT4Id, answer75Id, false);

        // ── Test-5: multiple open assessments updated simultaneously ──────
        orgServiceId_t5 = writeInTx(() -> {
            OrgService svc = orgServiceRepository.save(new OrgService("PropSvc T5", "Test 5"));
            return svc.getId();
        });
        osaId_t5 = createOrgServiceAssessment(orgServiceId_t5, ctrlT5Id, true, 25);
        openAssessmentId_t5a = createOpenAssessment("PropTest5a Multi", orgServiceId_t5, ctrlT5Id, answer25Id, false);
        openAssessmentId_t5b = createOpenAssessment("PropTest5b Multi", orgServiceId_t5, ctrlT5Id, answer25Id, false);

        // ── Test-6: comment propagation ───────────────────────────────────
        orgServiceId_t6 = writeInTx(() -> {
            OrgService svc = orgServiceRepository.save(new OrgService("PropSvc T6", "Test 6"));
            return svc.getId();
        });
        osaId_t6 = writeInTx(() -> {
            OrgService svc = orgServiceRepository.findById(orgServiceId_t6).orElseThrow();
            SecurityControl ctrl = securityControlRepository.findById(ctrlT6Id).orElseThrow();
            OrgServiceAssessment osa = new OrgServiceAssessment(svc, LocalDate.now());
            osa = orgServiceAssessmentRepository.save(osa);
            OrgServiceAssessmentControl osac = new OrgServiceAssessmentControl();
            osac.setSecurityControl(ctrl);
            osac.setApplicable(true);
            osac.setPercent(75);
            osac.setComment("");
            osac.setOrgServiceAssessment(osa);
            orgServiceAssessmentControlRepository.save(osac);
            return osa.getId();
        });
        openAssessmentId_t6 = createOpenAssessment("PropTest6 Comment", orgServiceId_t6, ctrlT6Id, answer75Id, false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 1 — OPEN assessment is updated immediately when control percent changes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test1_openAssessment_updatedImmediatelyOnControlPercentChange() throws Exception {
        // Pre-condition: open assessment has ctrlT1 at rating=25
        assertThat(storedRating(openAssessmentId_t1, ctrlT1Id))
                .as("Pre-condition: stored rating must be 25 before the save-control call")
                .isEqualTo(25);

        // Change ctrlT1 from 25% → 75% via the save-control endpoint
        mockMvc.perform(post("/orgservice-assessment/save-control")
                        .with(csrf())
                        .param("id", osaId_t1.toString())
                        .param("orgServiceId", orgServiceId_t1.toString())
                        .param("assessmentDate", LocalDate.now().toString())
                        .param("controlId", ctrlT1Id.toString())
                        .param("applicable", "true")
                        .param("percent", "75"))
                .andExpect(status().isOk());

        // Post-condition: open assessment's answer must now be 75 ("PropManaged")
        assertThat(storedRating(openAssessmentId_t1, ctrlT1Id))
                .as("Open assessment must be eagerly updated to rating=75 after save-control")
                .isEqualTo(75);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 2 — CLOSED (snapshot) assessment is not touched by propagation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test2_closedAssessment_notTouchedByPropagation() throws Exception {
        // Pre-condition: closed assessment has ctrlT2 frozen at rating=75
        assertThat(storedRating(closedAssessmentId_t2, ctrlT2Id))
                .as("Pre-condition: closed assessment's frozen rating must be 75")
                .isEqualTo(75);

        // Change ctrlT2 to 25% in the org-service assessment
        mockMvc.perform(post("/orgservice-assessment/save-control")
                        .with(csrf())
                        .param("id", osaId_t2.toString())
                        .param("orgServiceId", orgServiceId_t2.toString())
                        .param("assessmentDate", LocalDate.now().toString())
                        .param("controlId", ctrlT2Id.toString())
                        .param("applicable", "true")
                        .param("percent", "25"))
                .andExpect(status().isOk());

        // Post-condition: frozen answer in the closed assessment must remain 75
        assertThat(storedRating(closedAssessmentId_t2, ctrlT2Id))
                .as("Closed assessment's frozen answer must stay at 75 — propagation must not touch it")
                .isEqualTo(75);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 3 — isOverride=true answer in open assessment is NOT overwritten
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test3_overrideAnswer_notOverwrittenByPropagation() throws Exception {
        // Pre-condition: open assessment has ctrlT3 at rating=75 with isOverride=true
        assertThat(storedRating(openAssessmentId_t3, ctrlT3Id))
                .as("Pre-condition: override rating must be 75")
                .isEqualTo(75);

        // Change ctrlT3 in the org service to 25% — the override should block propagation
        mockMvc.perform(post("/orgservice-assessment/save-control")
                        .with(csrf())
                        .param("id", osaId_t3.toString())
                        .param("orgServiceId", orgServiceId_t3.toString())
                        .param("assessmentDate", LocalDate.now().toString())
                        .param("controlId", ctrlT3Id.toString())
                        .param("applicable", "true")
                        .param("percent", "25"))
                .andExpect(status().isOk());

        // Post-condition: override answer must remain at 75
        assertThat(storedRating(openAssessmentId_t3, ctrlT3Id))
                .as("isOverride=true answer (75) must NOT be overwritten by org-service propagation")
                .isEqualTo(75);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 4 — Control becoming applicable=false removes the inherited answer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test4_applicableFalse_removesInheritedAnswerFromOpenAssessment() throws Exception {
        // Pre-condition: open assessment has a stored ctrlT4 answer (rating=75)
        assertThat(hasStoredAnswer(openAssessmentId_t4, ctrlT4Id))
                .as("Pre-condition: ctrlT4 must have a stored answer before removal")
                .isTrue();

        // Mark ctrlT4 as applicable=false in the org-service assessment
        mockMvc.perform(post("/orgservice-assessment/save-control")
                        .with(csrf())
                        .param("id", osaId_t4.toString())
                        .param("orgServiceId", orgServiceId_t4.toString())
                        .param("assessmentDate", LocalDate.now().toString())
                        .param("controlId", ctrlT4Id.toString())
                        .param("applicable", "false")
                        .param("percent", "0"))
                .andExpect(status().isOk());

        // Post-condition: inherited answer for ctrlT4 must be removed
        assertThat(hasStoredAnswer(openAssessmentId_t4, ctrlT4Id))
                .as("Inherited answer must be removed when the control becomes applicable=false")
                .isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 5 — All open assessments linked to the org service are updated
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test5_multipleOpenAssessments_allUpdated() throws Exception {
        // Pre-condition: both assessments have ctrlT5 at rating=25
        assertThat(storedRating(openAssessmentId_t5a, ctrlT5Id)).isEqualTo(25);
        assertThat(storedRating(openAssessmentId_t5b, ctrlT5Id)).isEqualTo(25);

        // Change ctrlT5 from 25% → 75%
        mockMvc.perform(post("/orgservice-assessment/save-control")
                        .with(csrf())
                        .param("id", osaId_t5.toString())
                        .param("orgServiceId", orgServiceId_t5.toString())
                        .param("assessmentDate", LocalDate.now().toString())
                        .param("controlId", ctrlT5Id.toString())
                        .param("applicable", "true")
                        .param("percent", "75"))
                .andExpect(status().isOk());

        // Post-condition: both open assessments must be at rating=75
        assertThat(storedRating(openAssessmentId_t5a, ctrlT5Id))
                .as("First open assessment (T5a) must be updated to rating=75")
                .isEqualTo(75);
        assertThat(storedRating(openAssessmentId_t5b, ctrlT5Id))
                .as("Second open assessment (T5b) must be updated to rating=75")
                .isEqualTo(75);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 6 — Comment propagation reaches open assessments without user comment
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test6_commentPropagation_updatesOpenAssessmentWithNoUserComment() throws Exception {
        // Pre-condition: the open assessment has ctrlT6 with no comment
        String commentBefore = storedComment(openAssessmentId_t6, ctrlT6Id);
        assertThat(commentBefore == null || commentBefore.isEmpty())
                .as("Pre-condition: ctrlT6 comment in open assessment must be empty")
                .isTrue();

        // Save a comment for ctrlT6 in the org-service assessment
        mockMvc.perform(put("/orgservice-assessment/save-control-comment")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"id\":" + osaId_t6 + ",\"controlId\":" + ctrlT6Id
                                + ",\"comment\":\"Covered by T6 service\"}"))
                .andExpect(status().isOk());

        // Post-condition: the open assessment's ctrlT6 comment must now be propagated
        String commentAfter = storedComment(openAssessmentId_t6, ctrlT6Id);
        assertThat(commentAfter)
                .as("Comment from org-service must be propagated to open assessment")
                .isEqualTo("Covered by T6 service");
    }
}
