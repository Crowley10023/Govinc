package com.govinc;

import com.govinc.assessment.*;
import com.govinc.catalog.*;
import com.govinc.compliance.*;
import com.govinc.maturity.*;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitRepository;
import com.govinc.reporting.*;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for the Assessment Snapshot feature.
 *
 * Scenario:
 *   1.  Create a security catalog with 5 security controls.
 *   2.  Create an org unit and two users (ISM + assessor).
 *   3.  Create a ComplianceCheck with an AVERAGE_ABOVE=50 threshold linked to that catalog.
 *   4.  Create an assessment for that catalog, linked to the org unit.
 *   5.  Fill answers + comments for all 5 controls (rating=75 â†’ "Managed" level).
 *   6.  Finalize the assessment via the HTTP endpoint â†’ status becomes CLOSED and snapshot is populated.
 *   7.  Verify the snapshot contains exactly the 5 controls from the catalog.
 *   8.  Add a 6th control to the catalog AFTER finalization.
 *   9.  Verify the finalized assessment still shows only 5 controls (snapshot isolation).
 *  10.  Run ComplianceService.calculateCompliance and compare results with those obtained by
 *       calling the assessment JSON endpoint directly.
 *  11.  Create SecurityControlDomains / SecurityCapabilities and a CapabilityReport,
 *       then verify that the capability score calculation uses the finalized (snapshot) controls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssessmentSnapshotIntegrationTest {

    // â”€â”€ Spring-injected beans â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Autowired private MockMvc mockMvc;
    @Autowired private PlatformTransactionManager transactionManager;

    // Repositories
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private AssessmentDetailsService assessmentDetailsService;
    @Autowired private SecurityCatalogRepository catalogRepository;
    @Autowired private SecurityControlRepository controlRepository;
    @Autowired private SecurityControlDomainRepository domainRepository;
    @Autowired private SecurityCapabilityRepository capabilityRepository;
    @Autowired private MaturityAnswerRepository maturityAnswerRepository;
    @Autowired private MaturityModelRepository maturityModelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private ComplianceCheckRepository complianceCheckRepository;
    @Autowired private ComplianceThresholdRepository thresholdRepository;
    @Autowired private CapabilityReportRepository capabilityReportRepository;

    // Services
    @Autowired private ComplianceService complianceService;
    @Autowired private CapabilityReportService capabilityReportService;

    // â”€â”€ State shared across ordered tests â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Long catalogId;
    private Long assessmentId;
    private Long orgUnitId;
    private Long complianceCheckId;
    private Long capabilityId;
    private Long capabilityReportId;
    private Long domainId;

    /** Rating 75 â†’ "Managed" level used for all answers */
    private Long answerId75;

    private final List<Long> control5Ids = new ArrayList<>();
    private Long extraControlId; // added AFTER finalization

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private <T> T readInTx(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(s -> work.get());
    }

    private <T> T writeInTx(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(s -> work.get());
    }

    // â”€â”€ Setup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @BeforeAll
    void setUp() {
        // Ensure admin user exists so Spring Security can resolve the authenticated principal
        writeInTx(() -> {
            if (userRepository.findByEmail("admin@example.com").isEmpty()) {
                User admin = new User("admin", "", "admin@example.com");
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }
            return null;
        });
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 1 â€“ Create security catalog with 5 controls
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(100)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step1_createCatalogWith5Controls() {
        writeInTx(() -> {
            // Maturity model with a 75-rated answer
            MaturityAnswer ma75 = new MaturityAnswer("Managed", "Actively managed process");
            ma75.setRating(75);
            ma75 = maturityAnswerRepository.save(ma75);
            answerId75 = ma75.getId();

            MaturityModel model = new MaturityModel();
            model.setName("Snapshot Test Model");
            model.setDescription("Used by AssessmentSnapshotIntegrationTest");
            model.setMaturityAnswers(new LinkedHashSet<>(List.of(ma75)));
            model = maturityModelRepository.save(model);

            // Domain: all 5 controls belong to one domain
            SecurityControlDomain domain = new SecurityControlDomain(
                    "Snapshot Test Domain", "Domain for snapshot test controls");
            domain = domainRepository.save(domain);
            domainId = domain.getId();

            // Five controls
            List<SecurityControl> controls = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                SecurityControl ctrl = new SecurityControl(
                        "Snapshot Control " + i,
                        "Detail for control " + i,
                        "SC-SNAP-" + String.format("%02d", i));
                ctrl.setSecurityControlDomain(domain);
                ctrl = controlRepository.save(ctrl);
                controls.add(ctrl);
                control5Ids.add(ctrl.getId());
            }

            // Catalog
            SecurityCatalog catalog = new SecurityCatalog();
            catalog.setName("Snapshot Test Catalog");
            catalog.setDescription("Catalog used in snapshot integration test");
            catalog.setRevision("1.0");
            catalog.setMaturityModel(model);
            catalog.setSecurityControls(new LinkedHashSet<>(controls));
            catalog = catalogRepository.save(catalog);
            catalogId = catalog.getId();

            return null;
        });

        assertThat(control5Ids).hasSize(5);
        assertThat(catalogId).isNotNull();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 2 â€“ Create org unit and users
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(200)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step2_createOrgUnitAndUsers() {
        writeInTx(() -> {
            OrgUnit unit = new OrgUnit();
            unit.setName("Snapshot Test Org Unit");
            unit = orgUnitRepository.save(unit);
            orgUnitId = unit.getId();

            // ISM user
            User ism = new User("snap-ism", "ISM", "snap-ism@test.example");
            ism.setRole(Role.INFORMATION_SECURITY_MANAGER);
            userRepository.save(ism);

            // Assessor user
            User assessor = new User("snap-assessor", "Assessor", "snap-assessor@test.example");
            assessor.setRole(Role.ASSESSOR);
            userRepository.save(assessor);

            return null;
        });

        assertThat(orgUnitId).isNotNull();
        assertThat(userRepository.findAll().stream()
                .anyMatch(u -> "snap-ism@test.example".equals(u.getEmail()))).isTrue();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 3 â€“ Create ComplianceCheck (AVERAGE_ABOVE 50 %)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(300)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step3_createComplianceCheck() {
        writeInTx(() -> {
            SecurityCatalog catalog = catalogRepository.findById(catalogId).orElseThrow();

            ComplianceCheck check = new ComplianceCheck();
            check.setName("Snapshot Compliance Check");
            check.setDescription("Requires average maturity â‰¥ 50%");
            check.setSecurityCatalog(catalog);
            check = complianceCheckRepository.save(check);

            ComplianceThreshold threshold = new ComplianceThreshold();
            threshold.setRuleDescription("Average maturity above 50%");
            threshold.setType("AVERAGE_ABOVE");
            threshold.setValue(50);
            threshold.setComplianceCheck(check);
            thresholdRepository.save(threshold);

            complianceCheckId = check.getId();
            return null;
        });

        assertThat(complianceCheckId).isNotNull();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 4 â€“ Create assessment via HTTP endpoint
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(400)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step4_createAssessment() throws Exception {
        mockMvc.perform(post("/assessment/create")
                        .with(csrf())
                        .param("catalogId", catalogId.toString())
                        .param("name", "Snapshot E2E Assessment"))
                .andExpect(status().is3xxRedirection());

        assessmentId = writeInTx(() -> assessmentRepository.findAll().stream()
                .filter(a -> "Snapshot E2E Assessment".equals(a.getName()))
                .map(Assessment::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Assessment not created")));

        // Assign org unit
        mockMvc.perform(post("/assessment/{id}/set-orgunit", assessmentId)
                        .with(csrf())
                        .param("orgUnitId", orgUnitId.toString()))
                .andExpect(status().is3xxRedirection());

        // Attach the compliance check directly (no dedicated HTTP endpoint for this)
        writeInTx(() -> {
            Assessment a = assessmentRepository.findById(assessmentId).orElseThrow();
            ComplianceCheck cc = complianceCheckRepository.findById(complianceCheckId).orElseThrow();
            a.setComplianceCheck(cc);
            assessmentRepository.save(a);
            return null;
        });

        assertThat(assessmentId).isNotNull();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 5 â€“ Fill all 5 answers + comments via HTTP endpoints
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(500)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step5_fillAllAnswersAndComments() throws Exception {
        // Re-read answerId75 from DB (in case test order shuffled previous state)
        if (answerId75 == null) {
            answerId75 = readInTx(() -> maturityAnswerRepository.findAll().stream()
                    .filter(a -> Integer.valueOf(75).equals(a.getRating()))
                    .map(MaturityAnswer::getId)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("MaturityAnswer(75) not found")));
        }

        for (int i = 0; i < control5Ids.size(); i++) {
            Long controlId = control5Ids.get(i);

            // Answer
            mockMvc.perform(post("/assessment/{id}/answer", assessmentId)
                            .with(csrf())
                            .param("controlId", controlId.toString())
                            .param("answerId", answerId75.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));

            // Comment
            mockMvc.perform(put("/assessment/{aid}/control/{cid}/comment", assessmentId, controlId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"comment\": \"Snapshot test comment for control " + (i + 1) + "\"}"))
                    .andExpect(status().isOk());
        }

        // Verify all 5 answers were persisted
        Integer answeredCount = readInTx(() -> {
            Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findByAssessmentId(assessmentId);
            return detailsOpt.map(d -> d.getControlAnswers().size()).orElse(0);
        });
        assertThat(answeredCount).isEqualTo(5);
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 6 â€“ Finalize the assessment via HTTP endpoint
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(600)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step6_finalizeAssessment() throws Exception {
        mockMvc.perform(post("/assessment/{id}/finalize", assessmentId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // The assessment must now be CLOSED
        AssessmentStatus status = readInTx(() ->
                assessmentRepository.findById(assessmentId).orElseThrow().getStatus());
        assertThat(status).isEqualTo(AssessmentStatus.CLOSED);

        // The snapshot must contain exactly the 5 original controls
        Set<Long> snapshotIds = readInTx(() -> {
            Assessment a = assessmentRepository.findById(assessmentId).orElseThrow();
            // Force-load snapshotControls inside the transaction
            return a.getSnapshotControls().stream()
                    .map(SecurityControl::getId)
                    .collect(Collectors.toSet());
        });

        assertThat(snapshotIds)
                .as("Snapshot must contain exactly the 5 catalog controls")
                .containsExactlyInAnyOrderElementsOf(control5Ids);
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 7a â€“ Verify ComplianceCheck vs single assessment results match
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(700)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step7a_complianceCheck_matchesSingleAssessmentResults() {
        // Run ComplianceService.calculateCompliance (aggregated through OrgUnit)
        ComplianceService.ComplianceResult complianceResult = readInTx(() -> {
            ComplianceCheck check = complianceCheckRepository.findById(complianceCheckId).orElseThrow();
            OrgUnit unit = orgUnitRepository.findById(orgUnitId).orElseThrow();
            return complianceService.calculateCompliance(check, unit);
        });

        // Run evaluateComplianceForOrgAndChildren and look at the single OrgUnit entry
        ComplianceService.ComplianceResult perUnitResult = readInTx(() -> {
            ComplianceCheck check = complianceCheckRepository.findById(complianceCheckId).orElseThrow();
            OrgUnit unit = orgUnitRepository.findById(orgUnitId).orElseThrow();
            SecurityCatalog catalog = catalogRepository.findById(catalogId).orElseThrow();
            Map<OrgUnit, ComplianceService.ComplianceResult> map =
                    complianceService.evaluateComplianceForOrgAndChildren(unit, check, catalog);
            return map.values().stream().findFirst().orElseThrow();
        });

        // Both must agree on coverage (5/5 = 100 %) and average (75 %)
        assertThat(complianceResult.getControlsTotal())
                .as("Total controls must be 5")
                .isEqualTo(5);
        assertThat(complianceResult.getControlsAnswered())
                .as("All 5 controls must be answered")
                .isEqualTo(5);
        assertThat(complianceResult.getCoveragePercent())
                .as("Coverage must be 100%")
                .isEqualTo(100.0);
        assertThat(complianceResult.getAveragePercent())
                .as("Average must be 75%")
                .isEqualTo(75.0);
        assertThat(complianceResult.isCompliant())
                .as("Assessment with avg=75% must pass the AVERAGE_ABOVE=50% threshold")
                .isTrue();

        // The per-unit result must be identical to the aggregate for a single org unit
        assertThat(perUnitResult.getControlsAnswered())
                .isEqualTo(complianceResult.getControlsAnswered());
        assertThat(perUnitResult.getCoveragePercent())
                .isEqualTo(complianceResult.getCoveragePercent());
        assertThat(perUnitResult.getAveragePercent())
                .isEqualTo(complianceResult.getAveragePercent());
        assertThat(perUnitResult.isCompliant())
                .isEqualTo(complianceResult.isCompliant());
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 7b â€“ Assessment JSON endpoint agrees with compliance calculation
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(710)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step7b_assessmentJsonEndpoint_shows5Controls() throws Exception {
        MvcResult result = mockMvc.perform(get("/assessment/{id}", assessmentId)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn();

        // The rendered page must reference all 5 snapshot control names
        String html = result.getResponse().getContentAsString();
        for (int i = 1; i <= 5; i++) {
            assertThat(html)
                    .as("Page must contain 'Snapshot Control " + i + "'")
                    .contains("Snapshot Control " + i);
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 7c â€“ Add a 6th control to the catalog; snapshot must stay frozen
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(720)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step7c_newCatalogControl_doesNotAppearInClosedAssessment() {
        // Add a 6th control to the catalog AFTER finalization
        writeInTx(() -> {
            SecurityCatalog catalog = catalogRepository.findById(catalogId).orElseThrow();
            SecurityControlDomain domain = domainRepository.findById(domainId).orElseThrow();

            SecurityControl extra = new SecurityControl(
                    "Snapshot Control 6 (added post-finalization)",
                    "This control must NOT appear in the frozen assessment",
                    "SC-SNAP-06");
            extra.setSecurityControlDomain(domain);
            extra = controlRepository.save(extra);
            extraControlId = extra.getId();

            Set<SecurityControl> controls = new LinkedHashSet<>(catalog.getSecurityControls());
            controls.add(extra);
            catalog.setSecurityControls(controls);
            catalogRepository.save(catalog);
            return null;
        });

        // Catalog now has 6 controls
        int catalogControlCount = readInTx(() ->
                catalogRepository.findById(catalogId).orElseThrow().getSecurityControls().size());
        assertThat(catalogControlCount).isEqualTo(6);

        // The finalized assessment must still show exactly 5 effective controls
        List<Long> effectiveIds = readInTx(() -> {
            Assessment a = assessmentRepository.findById(assessmentId).orElseThrow();
            // Trigger lazy init of snapshotControls inside the transaction
            a.getSnapshotControls().size();
            return a.getEffectiveControls().stream()
                    .map(SecurityControl::getId)
                    .collect(Collectors.toList());
        });

        assertThat(effectiveIds)
                .as("Closed assessment must use frozen snapshot â€“ 5 controls, NOT 6")
                .hasSize(5)
                .doesNotContain(extraControlId)
                .containsExactlyInAnyOrderElementsOf(control5Ids);
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 8 â€“ Create SecurityCapability and CapabilityReport, verify score
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(800)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step8_createSecurityCapabilityAndDomains() throws Exception {
        // Create the SecurityCapability mapped to our domain via the HTTP endpoint
        mockMvc.perform(post("/security-capability/save")
                        .with(csrf())
                        .param("name", "Snapshot Test Capability")
                        .param("description", "Capability covering snapshot test domain")
                        .param("catalogId", catalogId.toString())
                        .param("domainIds", domainId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/security-capability/list"));

        capabilityId = readInTx(() -> capabilityRepository.findAll().stream()
                .filter(c -> "Snapshot Test Capability".equals(c.getName()))
                .map(SecurityCapability::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Capability not saved")));

        assertThat(capabilityId).isNotNull();
    }

    @Test
    @Order(810)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step8b_securityCapabilityList_showsNewCapability() throws Exception {
        MvcResult result = mockMvc.perform(get("/security-capability/list"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("Snapshot Test Capability");
    }

    @Test
    @Order(820)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step8c_capabilityMappingPage_containsDomainAndCapability() throws Exception {
        MvcResult result = mockMvc.perform(get("/security-capability/mapping"))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Snapshot Test Domain");
        assertThat(html).contains("Snapshot Test Capability");
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 9 â€“ Create CapabilityReport and calculate scores
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(900)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step9a_createCapabilityReport() throws Exception {
        // Look up the maturity model id
        Long modelId = readInTx(() ->
                maturityModelRepository.findAll().stream()
                        .filter(m -> "Snapshot Test Model".equals(m.getName()))
                        .map(MaturityModel::getId)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Model not found")));

        mockMvc.perform(post("/capability-report/save")
                        .with(csrf())
                        .param("name", "Snapshot Test Capability Report")
                        .param("description", "E2E capability report for snapshot test")
                        .param("catalogId", catalogId.toString())
                        .param("orgUnitId", orgUnitId.toString())
                        .param("maturityModelId", modelId.toString())
                        .param("capabilityIds", capabilityId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/capability-report/list"));

        capabilityReportId = readInTx(() -> capabilityReportRepository.findAll().stream()
                .filter(r -> "Snapshot Test Capability Report".equals(r.getName()))
                .map(CapabilityReport::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CapabilityReport not saved")));

        assertThat(capabilityReportId).isNotNull();
    }

    @Test
    @Order(910)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step9b_capabilityReport_calculate_scoreReflectsSnapshotControls() throws Exception {
        MvcResult mvcResult = mockMvc.perform(
                        get("/capability-report/calculate")
                                .param("id", capabilityReportId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Object resultObj = mvcResult.getModelAndView().getModel().get("result");
        assertThat(resultObj)
                .as("Model must contain a CapabilityReportService.CalculationResult")
                .isInstanceOf(CapabilityReportService.CalculationResult.class);

        CapabilityReportService.CalculationResult calcResult =
                (CapabilityReportService.CalculationResult) resultObj;

        // The report should have found 1 assessment (the finalized one)
        assertThat(calcResult.assessmentsIncluded)
                .as("Exactly 1 assessment must be included")
                .isEqualTo(1);

        // Exactly 1 capability score
        assertThat(calcResult.capabilityScores)
                .as("Report must have exactly 1 capability score")
                .hasSize(1);

        CapabilityReportService.CapabilityScore score = calcResult.capabilityScores.get(0);

        // All 5 snapshot controls must be counted (NOT 6 â€“ the extra control added post-finalization
        // is in the catalog but NOT in the capability score because it is not in the snapshot)
        assertThat(score.answeredControls)
                .as("Answered controls must be 5 (from the snapshot, all answered with rating=75)")
                .isEqualTo(5);

        // Average score must be 75
        assertThat(score.score)
                .as("Capability average score must equal 75.0 (all answers at rating=75)")
                .isEqualTo(75.0);

        // Coverage: 5 answered out of 5 snapshot controls in the domain
        assertThat(score.coverage)
                .as("Coverage must be 100%")
                .isEqualTo(100.0);
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STEP 10 â€“ Reopen, verify snapshot cleared, re-finalize, snapshot refreshed
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @Order(1000)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void step10_reopen_clearsSnapshot_andRefinalize_refreshesSnapshot() throws Exception {
        // Reopen
        mockMvc.perform(post("/assessment/{id}/reopen", assessmentId)
                        .with(csrf()))
                .andExpect(status().isOk());

        AssessmentStatus statusAfterReopen = readInTx(() ->
                assessmentRepository.findById(assessmentId).orElseThrow().getStatus());
        assertThat(statusAfterReopen).isEqualTo(AssessmentStatus.OPEN);

        // Snapshot must be cleared after reopening
        int snapshotSizeAfterReopen = readInTx(() -> {
            Assessment a = assessmentRepository.findById(assessmentId).orElseThrow();
            return a.getSnapshotControls().size();
        });
        assertThat(snapshotSizeAfterReopen)
                .as("Snapshot must be empty after reopening")
                .isEqualTo(0);

        // With snapshot cleared, effective controls should come from the live catalog (now 6)
        List<Long> effectiveAfterReopen = readInTx(() -> {
            Assessment a = assessmentRepository.findById(assessmentId).orElseThrow();
            a.getSnapshotControls().size(); // force init
            return a.getEffectiveControls().stream()
                    .map(SecurityControl::getId)
                    .collect(Collectors.toList());
        });
        assertThat(effectiveAfterReopen)
                .as("Open assessment with empty snapshot must show all 6 live catalog controls")
                .hasSize(6)
                .contains(extraControlId);

        // Finalize again
        mockMvc.perform(post("/assessment/{id}/finalize", assessmentId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // New snapshot must now include all 6 controls (catalog has 6 at this point)
        Set<Long> newSnapshotIds = readInTx(() -> {
            Assessment a = assessmentRepository.findById(assessmentId).orElseThrow();
            return a.getSnapshotControls().stream()
                    .map(SecurityControl::getId)
                    .collect(Collectors.toSet());
        });

        assertThat(newSnapshotIds)
                .as("Re-finalized snapshot must contain all 6 current catalog controls")
                .hasSize(6)
                .contains(extraControlId)
                .containsAll(control5Ids);
    }
}
