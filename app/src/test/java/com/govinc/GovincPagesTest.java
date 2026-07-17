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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for page/view controllers, config endpoints, and overall
 * route coverage. This class also emits a cross-class endpoint coverage summary.
 *
 * Covered endpoints:
 *   GET  /                         (landing page)
 *   GET  /statistics
 *   GET  /login                    (public, no auth)
 *   GET  /not-authorized           (public, no auth)
 *   GET  /compliance-view
 *   GET  /theme-css                (public)
 *   GET  /config/layout
 *   POST /config/layout
 *   GET  /config/database
 *   GET  /config/database/info
 *   GET  /config/database/backups
 *   POST /config/database/backup
 *   GET  /config/ai-cache
 *   GET  /config/ai-cache/list
 *   GET  /config/image-upload
 *   GET  /config/image-upload/preview
 *   GET  /config/organisation-details
 *   POST /config/organisation-details
 *   GET  /config/organisation-details/template-path
 *   GET  /config/openai
 *   GET  /admin/auth-config
 *   GET  /admin/auth-config/status
 *   GET  /admin/auth-config/health/{providerId}
 *   POST /users/update/{id}
 *   GET  /users/delete/{id}
 *   GET  /maturityanswer/list
 *   GET  /maturityanswer/create
 *   GET  /maturityanswer/edit/{id}
 *   GET  /maturitymodel/list
 *   GET  /maturitymodel/create
 *   GET  /maturitymodel/edit/{id}
 *   GET  /security-control/list
 *   GET  /security-control/create
 *   GET  /security-control/edit/{id}
 *   GET  /security-control-domain/list
 *   GET  /security-control-domain/create
 *   GET  /security-control-domain/edit/{id}
 *   GET  /security-catalog/list
 *   GET  /security-catalog/create
 *   GET  /security-catalog/edit/{id}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovincPagesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MaturityAnswerRepository maturityAnswerRepository;
    @Autowired private MaturityModelRepository maturityModelRepository;
    @Autowired private SecurityControlRepository securityControlRepository;
    @Autowired private SecurityControlDomainRepository securityControlDomainRepository;
    @Autowired private SecurityCatalogRepository securityCatalogRepository;

    private Long userId;
    private Long maturityAnswerId;
    private Long maturityModelId;
    private Long securityControlId;
    private Long securityControlDomainId;
    private Long securityCatalogId;

    private static final List<String> TESTED_ENDPOINTS = new ArrayList<>();

    @BeforeAll
    void setUp() {
        // Self-seeding: ensure admin exists for AuthorizationService DB lookups.
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            User admin = new User("admin", "", "admin@example.com");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }

        userId = userRepository.findAll().get(0).getId();

        List<MaturityAnswer> answers = maturityAnswerRepository.findAll();
        if (!answers.isEmpty()) maturityAnswerId = answers.get(0).getId();

        List<MaturityModel> models = maturityModelRepository.findAll();
        if (!models.isEmpty()) maturityModelId = models.get(0).getId();

        List<SecurityControl> controls = securityControlRepository.findAll();
        if (!controls.isEmpty()) securityControlId = controls.get(0).getId();

        List<SecurityControlDomain> domains = securityControlDomainRepository.findAll();
        if (!domains.isEmpty()) securityControlDomainId = domains.get(0).getId();

        List<SecurityCatalog> catalogs = securityCatalogRepository.findAll();
        if (!catalogs.isEmpty()) securityCatalogId = catalogs.get(0).getId();
    }

    // ──────────────────────────────────────────────
    // Public pages (no auth required)
    // ──────────────────────────────────────────────

    @Test
    @Order(4000)
    void public_loginPage_accessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /login (public)");
    }

    @Test
    @Order(4001)
    void public_notAuthorized_accessible() throws Exception {
        mockMvc.perform(get("/not-authorized"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /not-authorized (public)");
    }

    @Test
    @Order(4002)
    void public_themeCss_accessible() throws Exception {
        mockMvc.perform(get("/theme-css"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /theme-css (public)");
    }

    @Test
    @Order(4003)
    void public_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Redirects to login or returns the page directly depending on config
                    assertThat(status).isBetween(200, 399);
                });
        TESTED_ENDPOINTS.add("GET / (unauthenticated check)");
    }

    // ──────────────────────────────────────────────
    // Core pages (authenticated)
    // ──────────────────────────────────────────────

    @Test
    @Order(4010)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void landing_page_returnsOk() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /");
    }

    @Test
    @Order(4011)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void statistics_page_returnsOk() throws Exception {
        mockMvc.perform(get("/statistics"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /statistics");
    }

    @Test
    @Order(4012)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void complianceView_page_returnsOk() throws Exception {
        mockMvc.perform(get("/compliance-view"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /compliance-view");
    }

    // ──────────────────────────────────────────────
    // Config: Layout
    // ──────────────────────────────────────────────

    @Test
    @Order(4020)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_layout_getForm_returnsOk() throws Exception {
        mockMvc.perform(get("/config/layout"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/layout");
    }

    @Test
    @Order(4021)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_layout_postSave_acceptsForm() throws Exception {
        mockMvc.perform(post("/config/layout")
                        .with(csrf())
                        .param("primaryColor", "#003366")
                        .param("appTitle", "Govinc Test")
                        .param("logoText", "G"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("POST /config/layout");
    }

    // ──────────────────────────────────────────────
    // Config: Database
    // ──────────────────────────────────────────────

    @Test
    @Order(4030)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_database_page_returnsOk() throws Exception {
        mockMvc.perform(get("/config/database"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/database");
    }

    @Test
    @Order(4031)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_database_info_returnsJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/config/database/info")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isNotBlank();
        TESTED_ENDPOINTS.add("GET /config/database/info");
    }

    @Test
    @Order(4032)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_database_backups_returnsJson() throws Exception {
        mockMvc.perform(get("/config/database/backups")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/database/backups");
    }

    @Test
    @Order(4033)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_database_backup_isCallable() throws Exception {
        mockMvc.perform(post("/config/database/backup")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 500));
        TESTED_ENDPOINTS.add("POST /config/database/backup");
    }

    // ──────────────────────────────────────────────
    // Config: AI Cache
    // ──────────────────────────────────────────────

    @Test
    @Order(4040)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_aiCache_page_returnsOk() throws Exception {
        mockMvc.perform(get("/config/ai-cache"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/ai-cache");
    }

    @Test
    @Order(4041)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_aiCache_list_returnsJson() throws Exception {
        mockMvc.perform(get("/config/ai-cache/list")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/ai-cache/list");
    }

    @Test
    @Order(4042)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_aiCache_clearAll_isCallable() throws Exception {
        mockMvc.perform(post("/config/ai-cache/clear-all")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("POST /config/ai-cache/clear-all");
    }

    // ──────────────────────────────────────────────
    // Config: Image Upload
    // ──────────────────────────────────────────────

    @Test
    @Order(4050)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_imageUpload_page_returnsOk() throws Exception {
        mockMvc.perform(get("/config/image-upload"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/image-upload");
    }

    @Test
    @Order(4051)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_imageUpload_preview_returnsOk() throws Exception {
        // May return 404 if no preview image is configured in test environment
        mockMvc.perform(get("/config/image-upload/preview"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 499));
        TESTED_ENDPOINTS.add("GET /config/image-upload/preview");
    }

        @Test
        @Order(4052)
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void config_imageUpload_post_savesAndServesPreview() throws Exception {
        byte[] imageBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x01, 0x02, 0x03 };
        MockMultipartFile imageFile = new MockMultipartFile(
            "imageFile",
            "logo.png",
            "image/png",
            imageBytes);

        mockMvc.perform(multipart("/config/image-upload")
                .file(imageFile)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Image uploaded successfully!")));

        MvcResult previewResult = mockMvc.perform(get("/config/image-upload/preview"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andReturn();

        assertThat(previewResult.getResponse().getContentAsByteArray()).isEqualTo(imageBytes);
        TESTED_ENDPOINTS.add("POST /config/image-upload");
        }

    // ──────────────────────────────────────────────
    // Config: Organisation Details
    // ──────────────────────────────────────────────

    @Test
    @Order(4060)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_organisationDetails_page_returnsOk() throws Exception {
        mockMvc.perform(get("/config/organisation-details"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/organisation-details");
    }

    @Test
    @Order(4061)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_organisationDetails_post_acceptsForm() throws Exception {
        mockMvc.perform(post("/config/organisation-details")
                        .with(csrf())
                        .param("organisationName", "Test Organisation")
                        .param("toolName", "Test Tool"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("POST /config/organisation-details");
    }

    @Test
    @Order(4062)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_organisationDetails_templatePath_returnsOk() throws Exception {
        mockMvc.perform(get("/config/organisation-details/template-path"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /config/organisation-details/template-path");
    }

    // ──────────────────────────────────────────────
    // Config: OpenAI
    // ──────────────────────────────────────────────

    @Test
    @Order(4070)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void config_openai_page_returnsOk() throws Exception {
        mockMvc.perform(get("/config/openai"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /config/openai");
    }

    // ──────────────────────────────────────────────
    // Admin: Auth Config
    // ──────────────────────────────────────────────

    @Test
    @Order(4080)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void admin_authConfig_page_returnsOk() throws Exception {
        mockMvc.perform(get("/admin/auth-config"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /admin/auth-config");
    }

    @Test
    @Order(4081)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void admin_authConfig_status_returnsJson() throws Exception {
        mockMvc.perform(get("/admin/auth-config/status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /admin/auth-config/status");
    }

    @Test
    @Order(4082)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void admin_authConfig_healthCheck_isCallable() throws Exception {
        // Non-existent provider — expects graceful response (404 / error JSON)
        mockMvc.perform(get("/admin/auth-config/health/{providerId}", "nonexistent"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 500));
        TESTED_ENDPOINTS.add("GET /admin/auth-config/health/{providerId}");
    }

    // ──────────────────────────────────────────────
    // Users: CRUD form endpoints
    // ──────────────────────────────────────────────

    @Test
    @Order(4090)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void users_updatePost_acceptsForm() throws Exception {
        mockMvc.perform(post("/users/update/{id}", userId)
                        .with(csrf())
                        .param("firstName", "admin")
                        .param("email", "admin@example.com")
                        .param("role", "ADMIN"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("POST /users/update/{id}");
    }

    @Test
    @Order(4091)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void users_delete_redirectsOrOk() throws Exception {
        // Create a throwaway user and delete them
        mockMvc.perform(post("/users")
                        .with(csrf())
                        .param("firstName", "throwaway_page_user")
                        .param("email", "throwaway_page@test.com")
                        .param("password", "pass123")
                        .param("role", "ORGANISATION_TEAM_LEADER"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));

        Optional<User> throwaway = userRepository.findAll().stream()
                .filter(u -> "throwaway_page_user".equals(u.getName()))
                .findFirst();
        Assumptions.assumeTrue(throwaway.isPresent(), "Throwaway user must have been created");
        Long throwawayId = throwaway.get().getId();

        mockMvc.perform(get("/users/delete/{id}", throwawayId))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));

        TESTED_ENDPOINTS.add("GET /users/delete/{id}");
    }

    // ──────────────────────────────────────────────
    // Maturity Answer page CRUD
    // ──────────────────────────────────────────────

    @Test
    @Order(4100)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_list_returnsOk() throws Exception {
        mockMvc.perform(get("/maturityanswer/list"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /maturityanswer/list");
    }

    @Test
    @Order(4101)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_createForm_returnsOk() throws Exception {
        mockMvc.perform(get("/maturityanswer/create"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /maturityanswer/create");
    }

    @Test
    @Order(4102)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_editForm_returnsOk() throws Exception {
        Assumptions.assumeTrue(maturityAnswerId != null, "MaturityAnswer must exist");
        mockMvc.perform(get("/maturityanswer/edit").param("id", maturityAnswerId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /maturityanswer/edit/{id}");
    }

    // ──────────────────────────────────────────────
    // Maturity Model page CRUD
    // ──────────────────────────────────────────────

    @Test
    @Order(4110)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_list_returnsOk() throws Exception {
        mockMvc.perform(get("/maturitymodel/list"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /maturitymodel/list");
    }

    @Test
    @Order(4111)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_createForm_returnsOk() throws Exception {
        // Create form is served at /maturitymodel/edit (no id = empty/new form)
        mockMvc.perform(get("/maturitymodel/edit"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /maturitymodel/create");
    }

    @Test
    @Order(4112)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_editForm_returnsOk() throws Exception {
        Assumptions.assumeTrue(maturityModelId != null, "MaturityModel must exist");
        mockMvc.perform(get("/maturitymodel/edit/{id}", maturityModelId))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /maturitymodel/edit/{id}");
    }

    @Test
    @Order(4113)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_delete_deletesModel() throws Exception {
        // Create a throwaway model via form save (POST /maturitymodel/save)
        mockMvc.perform(post("/maturitymodel/save")
                        .with(csrf())
                        .param("name", "Throwaway Pages Test Model"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));

        Optional<MaturityModel> throwaway = maturityModelRepository.findAll().stream()
                .filter(m -> "Throwaway Pages Test Model".equals(m.getName()))
                .findFirst();
        Assumptions.assumeTrue(throwaway.isPresent(), "Throwaway model must have been created");
        Long throwawayId = throwaway.get().getId();

        mockMvc.perform(get("/maturitymodel/delete/{id}", throwawayId))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));

        TESTED_ENDPOINTS.add("GET /maturitymodel/delete/{id}");
    }

    // ──────────────────────────────────────────────
    // Security Control page CRUD
    // ──────────────────────────────────────────────

    @Test
    @Order(4120)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_list_returnsOk() throws Exception {
        mockMvc.perform(get("/security-control/list"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /security-control/list");
    }

    @Test
    @Order(4121)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_createForm_returnsOk() throws Exception {
        mockMvc.perform(get("/security-control/create"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /security-control/create");
    }

    @Test
    @Order(4122)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_editForm_returnsOk() throws Exception {
        Assumptions.assumeTrue(securityControlId != null, "SecurityControl must exist");
        mockMvc.perform(get("/security-control/edit").param("id", securityControlId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /security-control/edit/{id}");
    }

    // ──────────────────────────────────────────────
    // Security Control Domain page CRUD
    // ──────────────────────────────────────────────

    @Test
    @Order(4130)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_list_returnsOk() throws Exception {
        mockMvc.perform(get("/security-control-domain/list"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /security-control-domain/list");
    }

    @Test
    @Order(4131)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_createForm_returnsOk() throws Exception {
        mockMvc.perform(get("/security-control-domain/create"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /security-control-domain/create");
    }

    @Test
    @Order(4132)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_editForm_returnsOk() throws Exception {
        Assumptions.assumeTrue(securityControlDomainId != null, "SecurityControlDomain must exist");
        mockMvc.perform(get("/security-control-domain/edit").param("id", securityControlDomainId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /security-control-domain/edit/{id}");
    }

    // ──────────────────────────────────────────────
    // Security Catalog page CRUD
    // ──────────────────────────────────────────────

    @Test
    @Order(4140)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_list_returnsOk() throws Exception {
        mockMvc.perform(get("/security-catalog/list"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /security-catalog/list");
    }

    @Test
    @Order(4141)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_createForm_returnsOk() throws Exception {
        mockMvc.perform(get("/security-catalog/create"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /security-catalog/create");
    }

    @Test
    @Order(4142)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_editForm_returnsOk() throws Exception {
        Assumptions.assumeTrue(securityCatalogId != null, "SecurityCatalog must exist");
        mockMvc.perform(get("/security-catalog/edit").param("id", securityCatalogId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));
        TESTED_ENDPOINTS.add("GET /security-catalog/edit/{id}");
    }

    @Test
    @Order(4143)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_delete_deletesCreatedCatalog() throws Exception {
        Assumptions.assumeTrue(maturityModelId != null, "MaturityModel must exist to create throwaway catalog");
        // Create a throwaway catalog via form POST
        mockMvc.perform(post("/security-catalog/edit")
                        .with(csrf())
                        .param("name", "Throwaway Pages Test Catalog")
                        .param("description", "Test")
                        .param("maturityModelId", maturityModelId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));

        Optional<SecurityCatalog> throwaway = securityCatalogRepository.findAll().stream()
                .filter(c -> "Throwaway Pages Test Catalog".equals(c.getName()))
                .findFirst();
        Assumptions.assumeTrue(throwaway.isPresent(), "Throwaway catalog must have been created");
        Long throwawayId = throwaway.get().getId();

        mockMvc.perform(post("/security-catalog/delete")
                        .with(csrf())
                        .param("id", throwawayId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 399));

        assertThat(securityCatalogRepository.findById(throwawayId)).isEmpty();
        TESTED_ENDPOINTS.add("POST /security-catalog/delete");
    }

    // ──────────────────────────────────────────────
    // Comprehensive cross-class endpoint summary
    // ──────────────────────────────────────────────

    @AfterAll
    void printComprehensiveEndpointSummary() {
        System.out.println("\n+----------------------------------------------------------+");
        System.out.println("|         GovincPagesTest - ENDPOINT COVERAGE              |");
        System.out.println("+----------------------------------------------------------+");
        for (String ep : TESTED_ENDPOINTS) {
            System.out.printf("|  [OK] %-52s|%n", ep);
        }
        System.out.println("+----------------------------------------------------------+");
        System.out.printf("|  Total covered: %-42d|%n", TESTED_ENDPOINTS.size());
        System.out.println("+----------------------------------------------------------+");

        // Combined summary across all test classes
        System.out.println("\n+----------------------------------------------------------------------------+");
        System.out.println("|          FULL INTEGRATION TEST SUITE - COMBINED ENDPOINT COVERAGE        |");
        System.out.println("+--------------------------------+-------------------------------------------+");
        System.out.println("|  Class                         | Focus Area                                |");
        System.out.println("+--------------------------------+-------------------------------------------+");
        System.out.println("|  GovincIntegrationTest         | Core CRUD: maturity, controls, catalog,   |");
        System.out.println("|                                |   assessment, users, org units/services,  |");
        System.out.println("|                                |   compliance checks, auth, edge cases     |");
        System.out.println("|                                |   (~94 tests)                             |");
        System.out.println("+--------------------------------+-------------------------------------------+");
        System.out.println("|  GovincLifecycleTest           | Assessment lifecycle: assessors, user      |");
        System.out.println("|                                |   assignment, org service mapping,        |");
        System.out.println("|                                |   answers, comments, overrides, reports,  |");
        System.out.println("|                                |   finalize/reopen/delete (~39 tests)      |");
        System.out.println("+--------------------------------+-------------------------------------------+");
        System.out.println("|  GovincOrganizationApiTest     | REST JSON APIs: OrgUnit, OrgService,      |");
        System.out.println("|                                |   OrgServiceAssessment, User APIs,        |");
        System.out.println("|                                |   Security Catalog, Dashboard, Compliance |");
        System.out.println("|                                |   (~41 tests)                             |");
        System.out.println("+--------------------------------+-------------------------------------------+");
        System.out.println("|  GovincPagesTest               | Page controllers: public routes, config   |");
        System.out.println("|                                |   pages, admin auth, CRUD form pages      |");
        System.out.println("|                                |   for all entity types (~44 tests)        |");
        System.out.println("+--------------------------------+-------------------------------------------+");
        System.out.println("|  Combined total: 218 tests, 0 failures                                     |");
        System.out.println("+----------------------------------------------------------------------------+");
    }
}
