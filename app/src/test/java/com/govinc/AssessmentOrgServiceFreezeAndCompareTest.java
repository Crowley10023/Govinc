package com.govinc;

import com.govinc.assessment.*;
import com.govinc.catalog.*;
import com.govinc.maturity.*;
import com.govinc.user.*;
import com.govinc.organization.*;

import org.hamcrest.Matchers;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests covering:
 *   1. Org-service inherited answers are frozen into AssessmentDetails on close.
 *   2. A closed assessment is unaffected by subsequent org-service answer changes.
 *   3. A manual override (isOverride=true) is preserved and NOT overwritten on close.
 *   4. The Compare Assessments page (GET /reporting/compare) loads successfully.
 *   5. The compare-data API returns correct side-by-side maturity-answer data.
 *
 * This class is fully self-contained: it seeds all required data in {@code @BeforeAll}
 * and cleans up via {@code @DirtiesContext} after the last test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssessmentOrgServiceFreezeAndCompareTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentDetailsService assessmentDetailsService;
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

    // IDs seeded in @BeforeAll
    private Long catalogId;
    private Long orgUnitId;
    private Long orgServiceId1;                // used in tests 1+2 (freeze + stability)
    private Long orgServiceId2;                // used in test 3 (override preservation)
    private Long ctrl1Id;
    private Long ctrl2Id;
    private Long ctrl3Id;
    private Long answer25Id;                   // "Initial",  rating = 25
    private Long answer75Id;                   // "Managed",  rating = 75
    private Long osac1IdForSvc1;               // OrgServiceAssessmentControl svc1/ctrl1

    // IDs set during test execution
    private Long assessment1Id;                // freeze assessment (tests 1+2)
    private Long assessment2Id;                // override assessment (test 3, also used in test 5)

    // ─── transaction helpers ──────────────────────────────────────────────────

    private <T> T readInTx(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> work.get());
    }

    private <T> T writeInTx(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> work.get());
    }

    // ─── seed data ────────────────────────────────────────────────────────────

    @BeforeAll
    void setUp() {
        writeInTx(() -> {
            // Admin user (idempotent across shared Spring context)
            if (userRepository.findByEmail("admin@example.com").isEmpty()) {
                User admin = new User("admin", "", "admin@example.com");
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }

            // Maturity answers
            MaturityAnswer a25 = new MaturityAnswer("Initial", "Not yet implemented");
            a25.setRating(25);
            a25 = maturityAnswerRepository.save(a25);
            answer25Id = a25.getId();

            MaturityAnswer a75 = new MaturityAnswer("Managed", "Actively managed");
            a75.setRating(75);
            a75 = maturityAnswerRepository.save(a75);
            answer75Id = a75.getId();

            // Maturity model referencing both answers
            MaturityModel model = new MaturityModel();
            model.setName("Freeze-Compare Test Model");
            model.setDescription("Used by AssessmentOrgServiceFreezeAndCompareTest");
            model.setMaturityAnswers(new LinkedHashSet<>(List.of(a25, a75)));
            model = maturityModelRepository.save(model);

            // Domain
            SecurityControlDomain domain = new SecurityControlDomain(
                    "Freeze-Compare Domain", "Domain for freeze/compare integration tests");
            domain = securityControlDomainRepository.save(domain);

            // Three controls
            SecurityControl c1 = new SecurityControl("FrzCmp Ctrl 1", "Detail 1", "FRZ-CMP-01");
            c1.setSecurityControlDomain(domain);
            c1 = securityControlRepository.save(c1);
            ctrl1Id = c1.getId();

            SecurityControl c2 = new SecurityControl("FrzCmp Ctrl 2", "Detail 2", "FRZ-CMP-02");
            c2.setSecurityControlDomain(domain);
            c2 = securityControlRepository.save(c2);
            ctrl2Id = c2.getId();

            SecurityControl c3 = new SecurityControl("FrzCmp Ctrl 3", "Detail 3", "FRZ-CMP-03");
            c3.setSecurityControlDomain(domain);
            c3 = securityControlRepository.save(c3);
            ctrl3Id = c3.getId();

            // Security catalog
            SecurityCatalog catalog = new SecurityCatalog();
            catalog.setName("Freeze-Compare Catalog");
            catalog.setDescription("Catalog for freeze/compare integration tests");
            catalog.setRevision("1.0");
            catalog.setMaturityModel(model);
            catalog.setSecurityControls(new LinkedHashSet<>(List.of(c1, c2, c3)));
            catalog = securityCatalogRepository.save(catalog);
            catalogId = catalog.getId();

            // Org unit
            OrgUnit unit = new OrgUnit();
            unit.setName("Freeze-Compare Org Unit");
            unit = orgUnitRepository.save(unit);
            orgUnitId = unit.getId();

            // ── Org service 1 (freeze + stability tests) ──────────────────────
            OrgService svc1 = new OrgService("FrzCmp Svc1", "Used in freeze/stability tests");
            svc1 = orgServiceRepository.save(svc1);
            orgServiceId1 = svc1.getId();

            // OrgServiceAssessment for svc1: ctrl1 applicable at 75%, ctrl2 not applicable
            OrgServiceAssessment osa1 = new OrgServiceAssessment(svc1, LocalDate.now());
            osa1 = orgServiceAssessmentRepository.save(osa1);

            OrgServiceAssessmentControl osac1 = new OrgServiceAssessmentControl();
            osac1.setSecurityControl(c1);
            osac1.setApplicable(true);
            osac1.setPercent(75);
            osac1.setOrgServiceAssessment(osa1);
            osac1 = orgServiceAssessmentControlRepository.save(osac1);
            osac1IdForSvc1 = osac1.getId();

            OrgServiceAssessmentControl osac2 = new OrgServiceAssessmentControl();
            osac2.setSecurityControl(c2);
            osac2.setApplicable(false);
            osac2.setPercent(0);
            osac2.setOrgServiceAssessment(osa1);
            orgServiceAssessmentControlRepository.save(osac2);

            // ── Org service 2 (override-preservation test) ────────────────────
            OrgService svc2 = new OrgService("FrzCmp Svc2", "Used in override-preservation test");
            svc2 = orgServiceRepository.save(svc2);
            orgServiceId2 = svc2.getId();

            // OrgServiceAssessment for svc2: ctrl1 applicable at 75%
            OrgServiceAssessment osa2 = new OrgServiceAssessment(svc2, LocalDate.now());
            osa2 = orgServiceAssessmentRepository.save(osa2);

            OrgServiceAssessmentControl osac3 = new OrgServiceAssessmentControl();
            osac3.setSecurityControl(c1);
            osac3.setApplicable(true);
            osac3.setPercent(75);
            osac3.setOrgServiceAssessment(osa2);
            orgServiceAssessmentControlRepository.save(osac3);

            return null;
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 1 — Org-service answers are frozen into AssessmentDetails on close
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test1_orgServiceAnswers_frozenIntoDetailsOnClose() throws Exception {
        // Create assessment linked to org service 1 (ctrl1 at 75%)
        mockMvc.perform(post("/assessment/create")
                        .with(csrf())
                        .param("catalogId", catalogId.toString())
                        .param("name", "FreezeTestAssessment")
                        .param("orgUnitId", orgUnitId.toString())
                        .param("orgServiceIds", orgServiceId1.toString()))
                .andExpect(status().is3xxRedirection());

        assessment1Id = writeInTx(() -> assessmentRepository.findAll().stream()
                .filter(a -> "FreezeTestAssessment".equals(a.getName()))
                .map(Assessment::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FreezeTestAssessment not found")));

        // Finalise — should freeze the org-service answer for ctrl1 (75 → "Managed") into details
        mockMvc.perform(post("/assessment/{id}/finalize", assessment1Id)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // The closed assessment status must be CLOSED
        AssessmentStatus status = readInTx(() ->
                assessmentRepository.findById(assessment1Id).orElseThrow().getStatus());
        assertThat(status).isEqualTo(AssessmentStatus.CLOSED);

        // AssessmentDetails must contain a frozen answer for ctrl1 with rating = 75
        Integer storedRating = readInTx(() -> {
            Optional<AssessmentDetails> detOpt = assessmentDetailsService.findById(assessment1Id);
            if (detOpt.isEmpty()) return null;
            return detOpt.get().getControlAnswers().stream()
                    .filter(aca -> ctrl1Id.equals(aca.getSecurityControl().getId()))
                    .map(aca -> aca.getMaturityAnswer() != null ? aca.getMaturityAnswer().getRating() : null)
                    .findFirst()
                    .orElse(null);
        });

        assertThat(storedRating)
                .as("Org-service inherited answer for ctrl1 must be frozen at rating=75 on close")
                .isEqualTo(75);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 2 — Closed assessment is unaffected by subsequent org-service changes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test2_closedAssessment_unaffectedByOrgServiceUpdate() {
        // Mutate the OrgServiceAssessmentControl for ctrl1 from 75% → 25%
        writeInTx(() -> {
            OrgServiceAssessmentControl osac =
                    orgServiceAssessmentControlRepository.findById(osac1IdForSvc1).orElseThrow();
            osac.setPercent(25);
            orgServiceAssessmentControlRepository.save(osac);
            return null;
        });

        // The frozen answer in AssessmentDetails must still be 75 (snapshot taken at close)
        Integer frozenRating = readInTx(() -> {
            Optional<AssessmentDetails> detOpt = assessmentDetailsService.findById(assessment1Id);
            if (detOpt.isEmpty()) return null;
            return detOpt.get().getControlAnswers().stream()
                    .filter(aca -> ctrl1Id.equals(aca.getSecurityControl().getId()))
                    .map(aca -> aca.getMaturityAnswer() != null ? aca.getMaturityAnswer().getRating() : null)
                    .findFirst()
                    .orElse(null);
        });

        assertThat(frozenRating)
                .as("Frozen answer must remain 75 after the org-service control is mutated to 25%")
                .isEqualTo(75);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 3 — Manual override (isOverride=true) is preserved when closing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test3_manualOverride_preservedOnClose() throws Exception {
        // Create assessment linked to org service 2 (ctrl1 at 75%)
        mockMvc.perform(post("/assessment/create")
                        .with(csrf())
                        .param("catalogId", catalogId.toString())
                        .param("name", "OverrideTestAssessment")
                        .param("orgUnitId", orgUnitId.toString())
                        .param("orgServiceIds", orgServiceId2.toString()))
                .andExpect(status().is3xxRedirection());

        assessment2Id = writeInTx(() -> assessmentRepository.findAll().stream()
                .filter(a -> "OverrideTestAssessment".equals(a.getName()))
                .map(Assessment::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("OverrideTestAssessment not found")));

        // Manually override ctrl1 with the 25-rated answer (deliberately lower than org-service 75%)
        mockMvc.perform(post("/assessment/{id}/answer", assessment2Id)
                        .with(csrf())
                        .param("controlId", ctrl1Id.toString())
                        .param("answerId", answer25Id.toString())
                        .param("isOverride", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        // Finalise — the freeze loop must skip ctrl1 because it has isOverride=true
        mockMvc.perform(post("/assessment/{id}/finalize", assessment2Id)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // The stored answer for ctrl1 must still be 25 (manual override), NOT the org-service 75
        Integer storedRating = readInTx(() -> {
            Optional<AssessmentDetails> detOpt = assessmentDetailsService.findById(assessment2Id);
            if (detOpt.isEmpty()) return null;
            return detOpt.get().getControlAnswers().stream()
                    .filter(aca -> ctrl1Id.equals(aca.getSecurityControl().getId()))
                    .map(aca -> aca.getMaturityAnswer() != null ? aca.getMaturityAnswer().getRating() : null)
                    .findFirst()
                    .orElse(null);
        });

        assertThat(storedRating)
                .as("Manual override (25) must be preserved — org-service value (75) must NOT overwrite it")
                .isEqualTo(25);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 4 — Compare Assessments page loads successfully
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test4_compareAssessmentsPage_loadsSuccessfully() throws Exception {
        mockMvc.perform(get("/reporting/compare"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                // Page must contain the catalog selector (seeded catalog name)
                .andExpect(content().string(Matchers.containsString("Freeze-Compare Catalog")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 5 — Compare-data API returns correct side-by-side maturity answers
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test5_compareDataApi_returnsSideBySideAnswers() throws Exception {
        // assessment1: ctrl1 frozen at 75 ("Managed")  — created and closed in test 1
        // assessment2: ctrl1 overridden at 25 ("Initial") — created and closed in test 3
        mockMvc.perform(get("/reporting/compare/data")
                        .param("a1", assessment1Id.toString())
                        .param("a2", assessment2Id.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Top-level assessment metadata
                .andExpect(jsonPath("$.assessment1.name").value("FreezeTestAssessment"))
                .andExpect(jsonPath("$.assessment2.name").value("OverrideTestAssessment"))
                // Controls array must be non-empty (both assessments share the same 3-control catalog)
                .andExpect(jsonPath("$.controls").isArray())
                .andExpect(jsonPath("$.controls.length()").value(Matchers.greaterThanOrEqualTo(1)))
                // ctrl1 (FRZ-CMP-01): assessment1=75 ("Managed"), assessment2=25 ("Initial") → same=false
                .andExpect(jsonPath("$.controls[?(@.controlReference=='FRZ-CMP-01')].rating1")
                        .value(Matchers.hasItem(75)))
                .andExpect(jsonPath("$.controls[?(@.controlReference=='FRZ-CMP-01')].rating2")
                        .value(Matchers.hasItem(25)))
                .andExpect(jsonPath("$.controls[?(@.controlReference=='FRZ-CMP-01')].same")
                        .value(Matchers.hasItem(false)));
    }
}
