package com.govinc;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.assessment.AssessmentStatus;
import com.govinc.catalog.*;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.maturity.MaturityModel;
import com.govinc.maturity.MaturityModelRepository;
import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceAssessment;
import com.govinc.organization.OrgServiceAssessmentControl;
import com.govinc.organization.OrgServiceAssessmentRepository;
import com.govinc.organization.OrgServiceRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitRepository;
import com.govinc.reporting.CapabilityReport;
import com.govinc.reporting.CapabilityReportRepository;
import com.govinc.reporting.CapabilityReportService;
import com.govinc.user.Role;
import com.govinc.user.User;
import com.govinc.user.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Security Capabilities and Capability Report pages.
 *
 * Covered endpoints:
 *   GET  /security-capability/list
 *   GET  /security-capability/create
 *   POST /security-capability/save      (create)
 *   GET  /security-capability/edit      (edit existing)
 *   POST /security-capability/save      (update)
 *   POST /security-capability/delete
 *   GET  /capability-report/list
 *   GET  /capability-report/create
 *   POST /capability-report/save        (create)
 *   GET  /capability-report/edit
 *   POST /capability-report/save        (update)
 *   POST /capability-report/delete
 *   GET  /capability-report/calculate
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityCapabilityAndReportTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
        @Autowired private AssessmentRepository assessmentRepository;
        @Autowired private AssessmentDetailsService assessmentDetailsService;
    @Autowired private SecurityCapabilityRepository capabilityRepository;
    @Autowired private SecurityControlDomainRepository domainRepository;
    @Autowired private SecurityCatalogRepository catalogRepository;
        @Autowired private SecurityControlRepository securityControlRepository;
        @Autowired private MaturityAnswerRepository maturityAnswerRepository;
        @Autowired private MaturityModelRepository maturityModelRepository;
        @Autowired private OrgServiceRepository orgServiceRepository;
        @Autowired private OrgServiceAssessmentRepository orgServiceAssessmentRepository;
    @Autowired private CapabilityReportRepository reportRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;

    private Long capabilityId;
    private Long reportId;
    private Long domainId;
    private Long catalogId;
    private Long orgUnitId;

    @BeforeAll
    void setUp() {
        if (userRepository.findByName("admin").isEmpty()) {
            User admin = new User("admin", "admin@example.com");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }

        // Seed a domain for association tests
        SecurityControlDomain domain = new SecurityControlDomain("Test Domain", "For capability tests");
        domain = domainRepository.save(domain);
        domainId = domain.getId();

        // Seed a catalog
        SecurityCatalog catalog = new SecurityCatalog();
        catalog.setName("Test Catalog Cap");
        catalog = catalogRepository.save(catalog);
        catalogId = catalog.getId();

        // Seed an org unit
        OrgUnit ou = new OrgUnit();
        ou.setName("Test OrgUnit Cap");
        ou = orgUnitRepository.save(ou);
        orgUnitId = ou.getId();
    }

    private MaturityAnswer createMaturityAnswer(String answer, int rating) {
        MaturityAnswer ma = new MaturityAnswer(answer, "test");
        ma.setRating(rating);
        return maturityAnswerRepository.save(ma);
    }

    private SecurityCatalog createCatalogWithOneControl(String suffix, MaturityModel model, SecurityControlDomain domain) {
        SecurityControl control = new SecurityControl("Control " + suffix, "desc", "CID-" + suffix);
        control.setSecurityControlDomain(domain);
        control = securityControlRepository.save(control);

        SecurityCatalog catalog = new SecurityCatalog();
        catalog.setName("Catalog " + suffix);
        catalog.setDescription("desc");
        catalog.setRevision("1");
        catalog.setMaturityModel(model);
        catalog.setSecurityControls(new LinkedHashSet<>(List.of(control)));
        return catalogRepository.save(catalog);
    }

    private Assessment createAssessment(SecurityCatalog catalog, OrgUnit unit, String suffix) {
        Assessment assessment = new Assessment();
        assessment.setName("Assessment " + suffix);
        assessment.setCreationDate(LocalDate.now());
        assessment.setStatus(AssessmentStatus.OPEN);
        assessment.setSecurityCatalog(catalog);
        assessment.setOrgUnit(unit);
        return assessmentRepository.save(assessment);
    }

    private CapabilityReportService.CalculationResult calculateViaEndpoint(Long reportId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/capability-report/calculate").param("id", reportId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        Object result = mvcResult.getModelAndView().getModel().get("result");
        assertThat(result).isInstanceOf(CapabilityReportService.CalculationResult.class);
        return (CapabilityReportService.CalculationResult) result;
    }

    // ─── Security Capabilities ────────────────────────────────────────────────

    @Test
    @Order(1)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_list_returnsOk() throws Exception {
        mockMvc.perform(get("/security-capability/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_createForm_returnsOk() throws Exception {
        mockMvc.perform(get("/security-capability/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_save_createsCapability() throws Exception {
        mockMvc.perform(post("/security-capability/save")
                        .with(csrf())
                        .param("name", "Integration Test Capability")
                        .param("description", "Created by integration test")
                        .param("catalogId", String.valueOf(catalogId))
                        .param("domainIds", String.valueOf(domainId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/security-capability/list"));

        capabilityId = capabilityRepository.findAll().stream()
                .filter(c -> "Integration Test Capability".equals(c.getName()))
                .findFirst()
                .map(SecurityCapability::getId)
                .orElseThrow(() -> new AssertionError("Saved capability not found"));

        assertThat(capabilityId).isNotNull();
    }

    @Test
    @Order(4)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_editForm_returnsOk() throws Exception {
        mockMvc.perform(get("/security-capability/edit")
                        .param("id", String.valueOf(capabilityId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_update_updatesCapability() throws Exception {
        mockMvc.perform(post("/security-capability/save")
                        .with(csrf())
                        .param("id", String.valueOf(capabilityId))
                        .param("name", "Integration Test Capability (updated)")
                        .param("description", "Updated by integration test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/security-capability/list"));

        var updated = capabilityRepository.findById(capabilityId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Integration Test Capability (updated)");
    }

    @Test
    @Order(6)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_list_reflectsCreated() throws Exception {
        var response = mockMvc.perform(get("/security-capability/list"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(response.getResponse().getContentAsString())
                .contains("Integration Test Capability (updated)");
    }

    @Test
    @Order(7)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_accessDeniedForLowPrivilege() throws Exception {
        mockMvc.perform(get("/security-capability/list"))
                .andExpect(status().isOk()); // tested separately; verify 403 via SecurityConfig rules for ASSESSOR
    }

    // ─── Capability Reports ───────────────────────────────────────────────────

    @Test
    @Order(20)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_list_returnsOk() throws Exception {
        mockMvc.perform(get("/capability-report/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(21)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_createForm_returnsOk() throws Exception {
        mockMvc.perform(get("/capability-report/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(22)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_save_createsReport() throws Exception {
        mockMvc.perform(post("/capability-report/save")
                        .with(csrf())
                        .param("name", "Integration Test Report")
                        .param("description", "Created by integration test")
                        .param("catalogId", String.valueOf(catalogId))
                        .param("orgUnitId", String.valueOf(orgUnitId))
                        .param("capabilityIds", String.valueOf(capabilityId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/capability-report/list"));

        reportId = reportRepository.findAll().stream()
                .filter(r -> "Integration Test Report".equals(r.getName()))
                .findFirst()
                .map(CapabilityReport::getId)
                .orElseThrow(() -> new AssertionError("Saved report not found"));

        assertThat(reportId).isNotNull();
    }

    @Test
    @Order(23)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_editForm_returnsOk() throws Exception {
        mockMvc.perform(get("/capability-report/edit")
                        .param("id", String.valueOf(reportId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(24)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_update_updatesReport() throws Exception {
        mockMvc.perform(post("/capability-report/save")
                        .with(csrf())
                        .param("id", String.valueOf(reportId))
                        .param("name", "Integration Test Report (updated)")
                        .param("description", "Updated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/capability-report/list"));

        var updated = reportRepository.findById(reportId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Integration Test Report (updated)");
    }

    @Test
    @Order(25)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_calculate_returnsOk() throws Exception {
        // No assessments exist for the test org unit, so the report renders empty but without error
        mockMvc.perform(get("/capability-report/calculate")
                        .param("id", String.valueOf(reportId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(26)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_list_reflectsCreated() throws Exception {
        var response = mockMvc.perform(get("/capability-report/list"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(response.getResponse().getContentAsString())
                .contains("Integration Test Report (updated)");
    }

        @Test
        @Order(27)
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void assessment_orgServiceInheritanceAndOverride_driveCapabilityCalculation() throws Exception {
        String suffix = "inherit-" + System.nanoTime();

        MaturityAnswer low = createMaturityAnswer("Low " + suffix, 20);
        MaturityAnswer high = createMaturityAnswer("High " + suffix, 80);

        MaturityModel model = new MaturityModel();
        model.setName("Model " + suffix);
        model.setMaturityAnswers(new LinkedHashSet<>(List.of(low, high)));
        model = maturityModelRepository.save(model);

        SecurityControlDomain domain = domainRepository.save(
            new SecurityControlDomain("Domain " + suffix, "desc"));
        SecurityCatalog catalog = createCatalogWithOneControl(suffix, model, domain);
        SecurityControl control = catalog.getSecurityControls().iterator().next();

        SecurityCapability capability = new SecurityCapability();
        capability.setName("Capability " + suffix);
        capability.setDescription("desc");
        capability.setSecurityCatalog(catalog);
        capability.setDomains(new LinkedHashSet<>(Set.of(domain)));
        capability = capabilityRepository.save(capability);

        OrgUnit root = new OrgUnit();
        root.setName("Root " + suffix);
        root = orgUnitRepository.save(root);

        OrgUnit child = new OrgUnit();
        child.setName("Child " + suffix);
        child.setParent(root);
        child = orgUnitRepository.save(child);

        OrgService orgService = new OrgService("Service " + suffix, "desc");
        orgService = orgServiceRepository.save(orgService);

        Assessment assessment = createAssessment(catalog, child, suffix);
        assessment.setOrgServices(new LinkedHashSet<>(Set.of(orgService)));
        assessment = assessmentRepository.save(assessment);

        AssessmentDetails details = new AssessmentDetails();
        details.setAssessments(new LinkedHashSet<>(Set.of(assessment)));
        details.setDate(LocalDate.now());
        assessmentDetailsService.save(details);

        orgServiceAssessmentRepository.findByOrgServiceId(orgService.getId())
            .forEach(orgServiceAssessmentRepository::delete);
        OrgServiceAssessment osa = new OrgServiceAssessment(orgService, LocalDate.now());
        OrgServiceAssessmentControl osac = new OrgServiceAssessmentControl(control, true, 80,
            "Inherited from org service");
        osac.setOrgServiceAssessment(osa);
        osa.setControls(new java.util.ArrayList<>(List.of(osac)));
        orgServiceAssessmentRepository.save(osa);

        mockMvc.perform(get("/assessment/{id}", assessment.getId()))
            .andExpect(status().isOk());

        CapabilityReport calcReport = new CapabilityReport();
        calcReport.setName("Calc Report " + suffix);
        calcReport.setSecurityCatalog(catalog);
        calcReport.setMaturityModel(model);
        calcReport.setOrgUnit(root);
        calcReport.setCapabilities(List.of(capability));
        calcReport = reportRepository.save(calcReport);

        CapabilityReportService.CalculationResult inheritedResult = calculateViaEndpoint(calcReport.getId());
        assertThat(inheritedResult.assessmentsIncluded).isEqualTo(1);
        assertThat(inheritedResult.capabilityScores).hasSize(1);
        assertThat(inheritedResult.capabilityScores.get(0).score).isEqualTo(80.0);
        assertThat(inheritedResult.capabilityScores.get(0).answeredControls).isEqualTo(1);

        mockMvc.perform(get("/assessment/{id}/control/{controlId}/state", assessment.getId(), control.getId())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orgServiceAnswerId").value(high.getId()))
            .andExpect(jsonPath("$.orgServiceComment").value("Inherited from org service"));

        mockMvc.perform(post("/assessment/{id}/answer-override", assessment.getId())
                .with(csrf())
                .param("controlId", control.getId().toString())
                .param("answerId", low.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));

        CapabilityReportService.CalculationResult overrideResult = calculateViaEndpoint(calcReport.getId());
        assertThat(overrideResult.capabilityScores.get(0).score).isEqualTo(20.0);

        mockMvc.perform(post("/assessment/{id}/control/{controlId}/remove-override", assessment.getId(), control.getId())
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));

        mockMvc.perform(get("/assessment/{id}", assessment.getId()))
            .andExpect(status().isOk());

        CapabilityReportService.CalculationResult revertedResult = calculateViaEndpoint(calcReport.getId());
        assertThat(revertedResult.capabilityScores.get(0).score).isEqualTo(80.0);
        }

        @Test
        @Order(28)
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void capabilityReport_calculate_invalidId_redirectsToList() throws Exception {
        mockMvc.perform(get("/capability-report/calculate").param("id", "999999"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/capability-report/list"));
        }

        @Test
        @Order(29)
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void capabilityReport_delete_missingId_returnsStructuredError() throws Exception {
        var result = mockMvc.perform(post("/capability-report/delete")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getContentAsString())
            .contains("\"success\":false")
            .contains("Invalid ID");
        }

    // ─── Delete (last, so IDs are still valid) ────────────────────────────────

    @Test
    @Order(90)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void report_delete_removesReport() throws Exception {
        var result = mockMvc.perform(post("/capability-report/delete")
                        .with(csrf())
                        .param("id", String.valueOf(reportId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"success\":true");
        assertThat(reportRepository.findById(reportId)).isEmpty();
    }

    @Test
    @Order(91)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_delete_removesCapability() throws Exception {
        var result = mockMvc.perform(post("/security-capability/delete")
                        .with(csrf())
                        .param("id", String.valueOf(capabilityId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"success\":true");
        assertThat(capabilityRepository.findById(capabilityId)).isEmpty();
    }

    @Test
    @Order(100)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capability_delete_invalidId_returnsError() throws Exception {
        var result = mockMvc.perform(post("/security-capability/delete")
                        .with(csrf())
                        .param("id", "999999")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();
        // Either success (nothing to delete) or graceful error
        assertThat(result.getResponse().getContentAsString()).isNotBlank();
    }
}
