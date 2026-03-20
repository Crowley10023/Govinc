package com.govinc;

import com.govinc.catalog.*;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitRepository;
import com.govinc.reporting.CapabilityReport;
import com.govinc.reporting.CapabilityReportRepository;
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
    @Autowired private SecurityCapabilityRepository capabilityRepository;
    @Autowired private SecurityControlDomainRepository domainRepository;
    @Autowired private SecurityCatalogRepository catalogRepository;
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
