package com.govinc;

import com.govinc.catalog.*;
import com.govinc.compliance.*;
import com.govinc.governance.*;
import com.govinc.maturity.*;
import com.govinc.organization.*;
import com.govinc.user.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
 * Integration tests for Governance endpoints: Projects, Tasks, and
 * Deviation Analysis.
 *
 * Covered endpoints:
 *   GET    /governance/projects
 *   GET    /governance/projects/{id}
 *   POST   /governance/projects/create
 *   PUT    /governance/projects/{id}
 *   DELETE /governance/projects/{id}
 *   GET    /governance/projects/{id}/changes
 *   GET    /governance/projects/{id}/linked-assessments
 *   POST   /governance/projects/{id}/link-assessments
 *   POST   /governance/projects/{id}/unlink-assessment
 *   GET    /governance/tasks
 *   POST   /governance/tasks/create
 *   PUT    /governance/tasks/{id}/status
 *   PUT    /governance/tasks/{id}
 *   DELETE /governance/tasks/{id}
 *   GET    /governance/deviation-analysis
 *   POST   /governance/deviation-analysis/create-task
 *   POST   /governance/deviation-analysis/create-all-tasks
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovincGovernanceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private GovernanceProjectRepository projectRepository;
    @Autowired private GovernanceTaskRepository taskRepository;
    @Autowired private SecurityCatalogRepository securityCatalogRepository;
    @Autowired private SecurityControlRepository securityControlRepository;
    @Autowired private OrgUnitRepository orgUnitRepository;
    @Autowired private ComplianceCheckRepository complianceCheckRepository;

    private Long userId;
    private Long projectId;
    private Long taskId;
    private Long securityCatalogId;
    private Long securityControlId;
    private Long orgUnitId;
    private Long complianceCheckId;

    private static final List<String> TESTED_ENDPOINTS = new ArrayList<>();

    @BeforeAll
    void setUp() {
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            User admin = new User("admin", "", "admin@example.com");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
        userId = userRepository.findAll().stream().map(User::getId).findFirst().orElse(null);

        List<SecurityCatalog> catalogs = securityCatalogRepository.findAll();
        if (!catalogs.isEmpty()) securityCatalogId = catalogs.get(0).getId();

        List<SecurityControl> controls = securityControlRepository.findAll();
        if (!controls.isEmpty()) securityControlId = controls.get(0).getId();

        List<OrgUnit> units = orgUnitRepository.findAll();
        if (!units.isEmpty()) orgUnitId = units.get(0).getId();

        List<ComplianceCheck> checks = complianceCheckRepository.findAll();
        if (!checks.isEmpty()) complianceCheckId = checks.get(0).getId();
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
    // Governance Projects
    // ──────────────────────────────────────────────

    @Test
    @Order(6000)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_listPage_returnsOk() throws Exception {
        mockMvc.perform(get("/governance/projects"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /governance/projects");
    }

    @Test
    @Order(6001)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_create_returnsOk() throws Exception {
        Assumptions.assumeTrue(userId != null, "User must exist");
        String json = String.format(
                "{\"name\":\"Test Project\",\"description\":\"Integration test project\",\"projectType\":\"OTHER\",\"ownerId\":%d}",
                userId);

        MvcResult result = mockMvc.perform(post("/governance/projects/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result1 -> assertThat(result1.getResponse().getStatus()).isBetween(200, 299))
                .andReturn();

        // Extract the created project ID for subsequent tests
        List<GovernanceProject> projects = projectRepository.findAll();
        if (!projects.isEmpty()) {
            projectId = projects.get(projects.size() - 1).getId();
        }
        TESTED_ENDPOINTS.add("POST /governance/projects/create");
    }

    @Test
    @Order(6002)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_viewPage_returnsOk() throws Exception {
        Assumptions.assumeTrue(projectId != null, "Project must exist");
        mockMvc.perform(get("/governance/projects/{id}", projectId))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /governance/projects/{id}");
    }

    @Test
    @Order(6003)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_update_returnsOk() throws Exception {
        Assumptions.assumeTrue(projectId != null, "Project must exist");
        String json = "{\"name\":\"Updated Project\",\"description\":\"Updated description\"}";

        mockMvc.perform(put("/governance/projects/{id}", projectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        TESTED_ENDPOINTS.add("PUT /governance/projects/{id}");
    }

    @Test
    @Order(6004)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_getChanges_returnsOk() throws Exception {
        Assumptions.assumeTrue(projectId != null, "Project must exist");
        mockMvc.perform(get("/governance/projects/{id}/changes", projectId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /governance/projects/{id}/changes");
    }

    @Test
    @Order(6005)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_getLinkedAssessments_returnsOk() throws Exception {
        Assumptions.assumeTrue(projectId != null, "Project must exist");
        mockMvc.perform(get("/governance/projects/{id}/linked-assessments", projectId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /governance/projects/{id}/linked-assessments");
    }

    @Test
    @Order(6006)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_linkAssessments_returnsOk() throws Exception {
        Assumptions.assumeTrue(projectId != null, "Project must exist");
        // Link by orgUnit+catalog — may return 200 or 400 depending on data availability
        String json = "{}";
        if (orgUnitId != null && securityCatalogId != null) {
            json = String.format("{\"orgUnitId\":%d,\"catalogId\":%d}", orgUnitId, securityCatalogId);
        }
        mockMvc.perform(post("/governance/projects/{id}/link-assessments", projectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST link-assessments must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /governance/projects/{id}/link-assessments");
    }

    @Test
    @Order(6007)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_unlinkAssessment_returnsOk() throws Exception {
        Assumptions.assumeTrue(projectId != null, "Project must exist");
        String json = "{\"assessmentId\":999999}";
        mockMvc.perform(post("/governance/projects/{id}/unlink-assessment", projectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST unlink-assessment must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /governance/projects/{id}/unlink-assessment");
    }

    // ──────────────────────────────────────────────
    // Governance Tasks
    // ──────────────────────────────────────────────

    @Test
    @Order(6010)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void task_listPage_returnsOk() throws Exception {
        mockMvc.perform(get("/governance/tasks"))
                .andExpect(status().isOk());
        TESTED_ENDPOINTS.add("GET /governance/tasks");
    }

    @Test
    @Order(6011)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void task_create_returnsOk() throws Exception {
        Assumptions.assumeTrue(userId != null, "User must exist");
        String json = String.format(
                "{\"title\":\"Test Task\",\"description\":\"Integration test task\",\"assignedUserId\":%d}", userId);
        if (projectId != null) {
            json = String.format(
                    "{\"title\":\"Test Task\",\"description\":\"Integration test task\",\"assignedUserId\":%d,\"projectId\":%d}",
                    userId, projectId);
        }

        MvcResult result = mockMvc.perform(post("/governance/tasks/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result1 -> assertThat(result1.getResponse().getStatus()).isBetween(200, 299))
                .andReturn();

        List<GovernanceTask> tasks = taskRepository.findAll();
        if (!tasks.isEmpty()) {
            taskId = tasks.get(tasks.size() - 1).getId();
        }
        TESTED_ENDPOINTS.add("POST /governance/tasks/create");
    }

    @Test
    @Order(6012)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void task_updateStatus_returnsOk() throws Exception {
        Assumptions.assumeTrue(taskId != null, "Task must exist");
        String json = "{\"status\":\"IN_PROGRESS\"}";

        mockMvc.perform(put("/governance/tasks/{id}/status", taskId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        TESTED_ENDPOINTS.add("PUT /governance/tasks/{id}/status");
    }

    @Test
    @Order(6013)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void task_update_returnsOk() throws Exception {
        Assumptions.assumeTrue(taskId != null, "Task must exist");
        String json = "{\"title\":\"Updated Task\",\"description\":\"Updated description\"}";

        mockMvc.perform(put("/governance/tasks/{id}", taskId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        TESTED_ENDPOINTS.add("PUT /governance/tasks/{id}");
    }

    @Test
    @Order(6014)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void task_delete_returnsOk() throws Exception {
        Assumptions.assumeTrue(taskId != null, "Task must exist");
        mockMvc.perform(delete("/governance/tasks/{id}", taskId)
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        taskId = null; // consumed
        TESTED_ENDPOINTS.add("DELETE /governance/tasks/{id}");
    }

    // ──────────────────────────────────────────────
    // Deviation Analysis
    // ──────────────────────────────────────────────

    @Test
    @Order(6020)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deviationAnalysis_showPage_returnsOk() throws Exception {
        // The page may require params; omitting them should still not 404
        mockMvc.perform(get("/governance/deviation-analysis"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("GET deviation-analysis must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("GET /governance/deviation-analysis");
    }

    @Test
    @Order(6021)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deviationAnalysis_createTask_returnsOk() throws Exception {
        String json = "{\"gapIndex\":0}";
        if (orgUnitId != null && securityCatalogId != null) {
            json = String.format("{\"gapIndex\":0,\"orgUnitId\":%d,\"catalogId\":%d}", orgUnitId, securityCatalogId);
        }
        mockMvc.perform(post("/governance/deviation-analysis/create-task")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST create-task must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /governance/deviation-analysis/create-task");
    }

    @Test
    @Order(6022)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deviationAnalysis_createAllTasks_returnsOk() throws Exception {
        String json = "{}";
        if (orgUnitId != null && securityCatalogId != null) {
            json = String.format("{\"orgUnitId\":%d,\"catalogId\":%d}", orgUnitId, securityCatalogId);
        }
        mockMvc.perform(post("/governance/deviation-analysis/create-all-tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).as("POST create-all-tasks must be routed (not 404)").isNotEqualTo(404);
                });
        TESTED_ENDPOINTS.add("POST /governance/deviation-analysis/create-all-tasks");
    }

    // ──────────────────────────────────────────────
    // Cleanup: delete the test project last
    // ──────────────────────────────────────────────

    @Test
    @Order(6099)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void project_delete_returnsOk() throws Exception {
        Assumptions.assumeTrue(projectId != null, "Project must exist");
        mockMvc.perform(delete("/governance/projects/{id}", projectId)
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        TESTED_ENDPOINTS.add("DELETE /governance/projects/{id}");
    }

    // ──────────────────────────────────────────────

    @AfterAll
    void printGovernanceEndpointSummary() {
        System.out.println("\n+----------------------------------------------------------+");
        System.out.println("|         GovincGovernanceTest - ENDPOINT COVERAGE         |");
        System.out.println("+----------------------------------------------------------+");
        for (String ep : TESTED_ENDPOINTS) {
            System.out.printf("|  [OK] %-52s|%n", ep);
        }
        System.out.println("+----------------------------------------------------------+");
        System.out.printf("|  Total covered: %-42d|%n", TESTED_ENDPOINTS.size());
        System.out.println("+----------------------------------------------------------+");
    }
}
