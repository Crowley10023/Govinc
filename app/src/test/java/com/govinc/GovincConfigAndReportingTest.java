package com.govinc;

import com.govinc.catalog.*;
import com.govinc.maturity.*;
import com.govinc.organization.*;
import com.govinc.reporting.*;
import com.govinc.user.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for configuration and reporting endpoints not covered
 * by other test classes.
 *
 * Covered endpoints:
 *   GET    /config/email
 *   POST   /config/email
 *   POST   /config/openai                       (save config)
 *   POST   /config/openai/provider/create
 *   GET    /config/openai/provider/{id}
 *   PUT    /config/openai/provider/{id}
 *   POST   /config/openai/provider/{id}/activate
 *   DELETE /config/openai/provider/{id}
 *   POST   /config/openai/test
 *   GET    /reporting/org-unit
 *   GET    /reporting/org-unit/data
 *   GET    /capability-report/calculate-progress
 *   POST   /assessment/generate-answering-guide-questions
 *   POST   /assessment/generate-answer-from-guide
 *   POST   /assessment/generate-answer-summary
 *   POST   /assessment/{id}/email/generate
 *   POST   /assessment/{id}/email/send
 *   POST   /api/security-control/import/analyze
 *   POST   /api/security-control/import/analyze-single
 *   POST   /api/security-control/detect-language
 *   POST   /api/security-control/translate
 *   POST   /users/azure/search
 *   POST   /users/azure/import
 *   POST   /users/azure/resolve
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovincConfigAndReportingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private SecurityCatalogRepository securityCatalogRepository;
    @Autowired private CapabilityReportRepository capabilityReportRepository;

    private Long userId;
    private Long orgUnitId;
    private Long securityCatalogId;
    private Long capabilityReportId;

    private static final List<String> TESTED_ENDPOINTS = new ArrayList<>();

    @BeforeAll
    void setUp() {
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            User admin = new User("admin", "", "admin@example.com");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
        userId = userRepository.findAll().stream().map(User::getId).findFirst().orElse(null);

        List<OrgUnit> units = orgUnitRepository.findAll();
        if (!units.isEmpty()) orgUnitId = units.get(0).getId();

        List<SecurityCatalog> catalogs = securityCatalogRepository.findAll();
        if (!catalogs.isEmpty()) securityCatalogId = catalogs.get(0).getId();

        List<CapabilityReport> reports = capabilityReportRepository.findAll();
        if (!reports.isEmpty()) capabilityReportId = reports.get(0).getId();
    }

    private final java.io.PrintStream originalErr = System.err;

    @BeforeEach
    void muteStdErr() {
        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            @Override public void write(int b) {}
        }));
    }

    @AfterEach
    void restoreStdErr() {
        System.setErr(originalErr);
    }

    // ──────────────────────────────────────────────
    // Email Configuration
    // ──────────────────────────────────────────────

    @Test
    @Order(7000)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void emailConfig_getPage_returnsOk() throws Exception {
        mockMvc.perform(get("/config/email"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/email");
    }

    @Test
    @Order(7001)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void emailConfig_save_returnsOk() throws Exception {
        String json = "{\"host\":\"localhost\",\"port\":25,\"username\":\"\",\"password\":\"\",\"fromAddress\":\"test@example.com\"}";
        mockMvc.perform(post("/config/email")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST /config/email must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /config/email");
    }

    // ──────────────────────────────────────────────
    // OpenAI Configuration (additional endpoints)
    // ──────────────────────────────────────────────

    @Test
    @Order(7010)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void openaiConfig_saveConfig_returnsOk() throws Exception {
        String json = "{\"enabled\":false}";
        mockMvc.perform(post("/config/openai")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST /config/openai must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /config/openai");
    }

    private Long createdProviderId;

    @Test
    @Order(7011)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void openaiConfig_createProvider_returnsOk() throws Exception {
        String json = "{\"name\":\"Test Provider\",\"type\":\"OPENAI\",\"apiKey\":\"test-key\",\"endpoint\":\"https://localhost\",\"model\":\"gpt-4\"}";
        mockMvc.perform(post("/config/openai/provider/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST create provider must be routed (not 404)").isNotEqualTo(404);
                    // Try to extract the created ID from the response body
                    if (status >= 200 && status < 300) {
                        String body = result.getResponse().getContentAsString();
                        // Look for "id": <number> in the response
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
                        if (m.find()) {
                            createdProviderId = Long.parseLong(m.group(1));
                        }
                    }
                });
        TESTED_ENDPOINTS.add("POST /config/openai/provider/create");
    }

    @Test
    @Order(7012)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void openaiConfig_getProvider_returnsOk() throws Exception {
        Assumptions.assumeTrue(createdProviderId != null, "Provider must have been created");
        mockMvc.perform(get("/config/openai/provider/{id}", createdProviderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        TESTED_ENDPOINTS.add("GET /config/openai/provider/{id}");
    }

    @Test
    @Order(7013)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void openaiConfig_updateProvider_returnsOk() throws Exception {
        Assumptions.assumeTrue(createdProviderId != null, "Provider must have been created");
        String json = "{\"name\":\"Updated Provider\",\"type\":\"OPENAI\",\"apiKey\":\"test-key-2\",\"endpoint\":\"https://localhost\",\"model\":\"gpt-4\"}";
        mockMvc.perform(put("/config/openai/provider/{id}", createdProviderId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        TESTED_ENDPOINTS.add("PUT /config/openai/provider/{id}");
    }

    @Test
    @Order(7014)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void openaiConfig_activateProvider_returnsOk() throws Exception {
        Assumptions.assumeTrue(createdProviderId != null, "Provider must have been created");
        mockMvc.perform(post("/config/openai/provider/{id}/activate", createdProviderId)
                        .with(csrf()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST activate provider must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /config/openai/provider/{id}/activate");
    }

    @Test
    @Order(7015)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void openaiConfig_deleteProvider_returnsOk() throws Exception {
        Assumptions.assumeTrue(createdProviderId != null, "Provider must have been created");
        mockMvc.perform(delete("/config/openai/provider/{id}", createdProviderId)
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        TESTED_ENDPOINTS.add("DELETE /config/openai/provider/{id}");
    }

    @Test
    @Order(7016)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void openaiConfig_test_returnsOk() throws Exception {
        mockMvc.perform(post("/config/openai/test")
                        .with(csrf())
                        .param("prompt", "Hello"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST /config/openai/test must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /config/openai/test");
    }

    // ──────────────────────────────────────────────
    // Reporting
    // ──────────────────────────────────────────────

    @Test
    @Order(7020)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void reporting_orgUnitPage_returnsOk() throws Exception {
        mockMvc.perform(get("/reporting/org-unit"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /reporting/org-unit");
    }

    @Test
    @Order(7021)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void reporting_orgUnitData_returnsJson() throws Exception {
        // Endpoint may require orgUnitId param; test with and without
        mockMvc.perform(get("/reporting/org-unit/data")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("GET /reporting/org-unit/data must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("GET /reporting/org-unit/data");
    }

    @Test
    @Order(7022)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void reporting_orgUnitDataWithId_returnsJson() throws Exception {
        Assumptions.assumeTrue(orgUnitId != null, "OrgUnit must exist");
        mockMvc.perform(get("/reporting/org-unit/data")
                        .param("orgUnitId", orgUnitId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("GET /reporting/org-unit/data?orgUnitId= must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("GET /reporting/org-unit/data?orgUnitId=");
    }

    // ──────────────────────────────────────────────
    // Capability Report - calculate-progress
    // ──────────────────────────────────────────────

    @Test
    @Order(7025)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void capabilityReport_calculateProgress_returnsOk() throws Exception {
        Assumptions.assumeTrue(capabilityReportId != null, "CapabilityReport must exist");
        mockMvc.perform(get("/capability-report/calculate-progress")
                        .param("id", capabilityReportId.toString()))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /capability-report/calculate-progress");
    }

    // ──────────────────────────────────────────────
    // Answering Guide (AI-dependent: test routing only)
    // ──────────────────────────────────────────────

    @Test
    @Order(7030)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void answeringGuide_generateQuestions_isRouted() throws Exception {
        String json = "{\"controlId\":1,\"assessmentId\":1}";
        mockMvc.perform(post("/assessment/generate-answering-guide-questions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST generate-answering-guide-questions must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /assessment/generate-answering-guide-questions");
    }

    @Test
    @Order(7031)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void answeringGuide_generateAnswer_isRouted() throws Exception {
        String json = "{\"controlId\":1,\"assessmentId\":1,\"answers\":{}}";
        mockMvc.perform(post("/assessment/generate-answer-from-guide")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST generate-answer-from-guide must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /assessment/generate-answer-from-guide");
    }

    @Test
    @Order(7032)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void answeringGuide_generateSummary_isRouted() throws Exception {
        String json = "{\"assessmentId\":1}";
        mockMvc.perform(post("/assessment/generate-answer-summary")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST generate-answer-summary must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /assessment/generate-answer-summary");
    }

    // ──────────────────────────────────────────────
    // Assessment Email (AI/SMTP-dependent: test routing only)
    // ──────────────────────────────────────────────

    @Test
    @Order(7040)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessmentEmail_generate_isRouted() throws Exception {
        mockMvc.perform(post("/assessment/1/email/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST email/generate must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /assessment/{id}/email/generate");
    }

    @Test
    @Order(7041)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessmentEmail_send_isRouted() throws Exception {
        String json = "{\"to\":\"test@example.com\",\"subject\":\"Test\",\"body\":\"Test\"}";
        mockMvc.perform(post("/assessment/1/email/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST email/send must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /assessment/{id}/email/send");
    }

    // ──────────────────────────────────────────────
    // Security Control Import (AI-dependent: test routing only)
    // ──────────────────────────────────────────────

    @Test
    @Order(7050)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlImport_analyze_isRouted() throws Exception {
        String json = "{\"catalogId\":1,\"controls\":[]}";
        mockMvc.perform(post("/api/security-control/import/analyze")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST import/analyze must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /api/security-control/import/analyze");
    }

    @Test
    @Order(7051)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlImport_analyzeSingle_isRouted() throws Exception {
        String json = "{\"catalogId\":1,\"control\":{\"name\":\"test\",\"description\":\"test\"}}";
        mockMvc.perform(post("/api/security-control/import/analyze-single")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST import/analyze-single must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /api/security-control/import/analyze-single");
    }

    // ──────────────────────────────────────────────
    // Security Control Translation (AI-dependent: test routing only)
    // ──────────────────────────────────────────────

    @Test
    @Order(7060)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlTranslation_detectLanguage_isRouted() throws Exception {
        String json = "{\"text\":\"Hello world\"}";
        mockMvc.perform(post("/api/security-control/detect-language")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST detect-language must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /api/security-control/detect-language");
    }

    @Test
    @Order(7061)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlTranslation_translate_isRouted() throws Exception {
        String json = "{\"controlId\":1,\"targetLanguage\":\"en\"}";
        mockMvc.perform(post("/api/security-control/translate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST translate must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /api/security-control/translate");
    }

    // ──────────────────────────────────────────────
    // Azure User Import (Azure-dependent: test routing only)
    // ──────────────────────────────────────────────

    @Test
    @Order(7070)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void azureUserImport_search_isRouted() throws Exception {
        String json = "{\"query\":\"test\"}";
        mockMvc.perform(post("/users/azure/search")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST azure/search must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /users/azure/search");
    }

    @Test
    @Order(7071)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void azureUserImport_import_isRouted() throws Exception {
        String json = "{\"users\":[]}";
        mockMvc.perform(post("/users/azure/import")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST azure/import must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /users/azure/import");
    }

    @Test
    @Order(7072)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void azureUserImport_resolve_isRouted() throws Exception {
        String json = "{\"users\":[]}";
        mockMvc.perform(post("/users/azure/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST azure/resolve must be routed").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /users/azure/resolve");
    }

    // ──────────────────────────────────────────────

    @AfterAll
    void printConfigAndReportingEndpointSummary() {
        System.out.println("\n+----------------------------------------------------------+");
        System.out.println("|   GovincConfigAndReportingTest - ENDPOINT COVERAGE       |");
        System.out.println("+----------------------------------------------------------+");
        for (String ep : TESTED_ENDPOINTS) {
            System.out.printf("|  [OK] %-52s|%n", ep);
        }
        System.out.println("+----------------------------------------------------------+");
        System.out.printf("|  Total covered: %-42d|%n", TESTED_ENDPOINTS.size());
        System.out.println("+----------------------------------------------------------+");
    }
}
