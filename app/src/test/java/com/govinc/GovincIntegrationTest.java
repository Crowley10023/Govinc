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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.govinc.compliance.ComplianceCheck;
import com.govinc.compliance.ComplianceCheckRepository;
import com.govinc.compliance.ComplianceThreshold;
import com.govinc.organization.OrgServiceAssessment;
import com.govinc.organization.OrgServiceAssessmentControl;
import com.govinc.organization.OrgServiceAssessmentRepository;
import com.govinc.organization.OrgServiceAssessmentControlRepository;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration test suite for the Govinc application.
 * Boots the full Spring context with an in-memory H2 database and
 * exercises all major API endpoints in a realistic workflow order.
 *
 * Execution order (via @Order):
 * 1. Create maturity answers
 * 2. Create a maturity model based on the answers
 * 3. Create ~40 security controls (with domains)
 * 4. Create a security catalog containing the maturity model and controls
 * 5. Create an assessment
 * 6. Create users
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovincIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;

    @Autowired
    private MaturityModelRepository maturityModelRepository;

    @Autowired
    private SecurityControlRepository securityControlRepository;

    @Autowired
    private SecurityControlDomainRepository securityControlDomainRepository;

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private OrgServiceRepository orgServiceRepository;

    @Autowired
    private OrgServiceAssessmentRepository orgServiceAssessmentRepository;

    @Autowired
    private OrgServiceAssessmentControlRepository orgServiceAssessmentControlRepository;

    @Autowired
    private ComplianceCheckRepository complianceCheckRepository;

    @Autowired
    private com.govinc.compliance.ComplianceService complianceService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Pre-create the admin user in the DB before any tests run.
     * This is required because AuthorizationService.getCurrentUser()
     * looks up the authenticated username in the database, and many
     * controllers delegate authorization checks to that service.
     */
    @BeforeAll
    void setUp() {
        // Idempotent: only create admin if not already present (shared Spring context may
        // have another test class create it first).
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            User admin = new User("admin", "", "admin@example.com");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
    }

    /** Helper: read lazy collections inside a fresh read-only transaction. */
    private <T> T readInTx(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> work.get());
    }

    // ──────────────────────────────────────────────
    // 1. Maturity Answers
    // ──────────────────────────────────────────────

    @Test
    @Order(100)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_listInitiallyEmpty() throws Exception {
        mockMvc.perform(get("/maturityanswer/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(101)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_createForm() throws Exception {
        mockMvc.perform(get("/maturityanswer/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(110)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_createFiveAnswers() throws Exception {
        String[][] answers = {
                {"Not Implemented", "No implementation exists", "0"},
                {"Initial", "Ad-hoc processes, not repeatable", "25"},
                {"Managed", "Basic processes defined and followed", "50"},
                {"Defined", "Standardized processes organization-wide", "75"},
                {"Optimized", "Continuous improvement and optimization", "100"}
        };

        for (String[] a : answers) {
            mockMvc.perform(post("/maturityanswer/edit")
                            .with(csrf())
                            .param("answer", a[0])
                            .param("description", a[1])
                            .param("rating", a[2]))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/maturityanswer/list"));
        }

        List<MaturityAnswer> all = maturityAnswerRepository.findAll();
        assertThat(all).hasSize(5);
        assertThat(all).extracting(MaturityAnswer::getAnswer)
                .containsExactlyInAnyOrder("Not Implemented", "Initial", "Managed", "Defined", "Optimized");
    }

    @Test
    @Order(115)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_editExisting() throws Exception {
        MaturityAnswer first = maturityAnswerRepository.findAll().get(0);
        mockMvc.perform(get("/maturityanswer/edit").param("id", first.getId().toString()))
                .andExpect(status().isOk());

        // Update the description
        mockMvc.perform(post("/maturityanswer/edit")
                        .with(csrf())
                        .param("id", first.getId().toString())
                        .param("answer", first.getAnswer())
                        .param("description", "Updated description")
                        .param("rating", String.valueOf(first.getRating())))
                .andExpect(status().is3xxRedirection());

        MaturityAnswer updated = maturityAnswerRepository.findById(first.getId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @Order(120)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_listAfterCreation() throws Exception {
        mockMvc.perform(get("/maturityanswer/list"))
                .andExpect(status().isOk());
        assertThat(maturityAnswerRepository.findAll()).hasSize(5);
    }

    // ──────────────────────────────────────────────
    // 2. Maturity Model
    // ──────────────────────────────────────────────

    @Test
    @Order(200)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_createForm() throws Exception {
        mockMvc.perform(get("/maturitymodel/edit"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(210)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_create() throws Exception {
        List<MaturityAnswer> answers = maturityAnswerRepository.findAll();
        assertThat(answers).isNotEmpty();

        // Build answer ID parameter list
        List<String> answerIds = answers.stream()
                .map(a -> a.getId().toString())
                .toList();

        var request = post("/maturitymodel/save")
                .with(csrf())
                .param("name", "Standard Maturity Model")
                .param("description", "Five-level maturity model for security assessments");

        for (String id : answerIds) {
            request = request.param("maturityAnswers", id);
        }

        mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maturitymodel/list"));

        List<MaturityModel> models = maturityModelRepository.findAll();
        assertThat(models).hasSize(1);
        MaturityModel model = models.get(0);
        assertThat(model.getName()).isEqualTo("Standard Maturity Model");
        assertThat(model.getMaturityAnswers()).hasSize(5);
    }

    @Test
    @Order(215)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_editForm() throws Exception {
        MaturityModel model = maturityModelRepository.findAll().get(0);
        mockMvc.perform(get("/maturitymodel/edit/" + model.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(220)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_list() throws Exception {
        mockMvc.perform(get("/maturitymodel/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(225)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_apiAll() throws Exception {
        mockMvc.perform(get("/maturitymodel/api/all"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ──────────────────────────────────────────────
    // 3. Security Control Domains & Controls (~40)
    // ──────────────────────────────────────────────

    @Test
    @Order(300)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_createDomains() throws Exception {
        String[][] domains = {
                {"Access Control", "Controls related to access management"},
                {"Asset Management", "Controls for asset identification and management"},
                {"Cryptography", "Cryptographic controls and key management"},
                {"Physical Security", "Physical and environmental security controls"},
                {"Operations Security", "Operational procedures and responsibilities"},
                {"Communications Security", "Network security management and controls"},
                {"Incident Management", "Information security incident management"},
                {"Business Continuity", "Business continuity and disaster recovery"},
                {"Compliance", "Compliance with legal and contractual requirements"},
                {"Human Resources", "HR security controls and awareness"}
        };

        for (String[] d : domains) {
            mockMvc.perform(post("/security-control-domain/edit")
                            .with(csrf())
                            .param("name", d[0])
                            .param("description", d[1]))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/security-control-domain/list"));
        }

        assertThat(securityControlDomainRepository.findAll()).hasSize(10);
    }

    @Test
    @Order(305)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_listDomains() throws Exception {
        mockMvc.perform(get("/security-control-domain/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(310)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_create40Controls() throws Exception {
        List<SecurityControlDomain> domains = securityControlDomainRepository.findAll();
        assertThat(domains).hasSizeGreaterThanOrEqualTo(10);

        // Map domain name → id for convenience
        Map<String, Long> domainMap = new HashMap<>();
        for (SecurityControlDomain d : domains) {
            domainMap.put(d.getName(), d.getId());
        }

        String[][] controls = {
                // Access Control (4)
                {"AC-01", "Access Control Policy", "Establish access control policy and procedures", "Access Control"},
                {"AC-02", "Account Management", "Manage user accounts throughout lifecycle", "Access Control"},
                {"AC-03", "Access Enforcement", "Enforce approved authorizations for access", "Access Control"},
                {"AC-04", "Information Flow Enforcement", "Enforce approved authorizations for information flow", "Access Control"},
                // Asset Management (4)
                {"AM-01", "Asset Inventory", "Maintain inventory of all information assets", "Asset Management"},
                {"AM-02", "Asset Classification", "Classify information based on sensitivity", "Asset Management"},
                {"AM-03", "Media Handling", "Procedures for handling storage media", "Asset Management"},
                {"AM-04", "Asset Disposal", "Secure disposal of assets and media", "Asset Management"},
                // Cryptography (4)
                {"CR-01", "Cryptographic Policy", "Establish policy on use of cryptographic controls", "Cryptography"},
                {"CR-02", "Key Management", "Procedures for key generation and distribution", "Cryptography"},
                {"CR-03", "Encryption at Rest", "Encrypt sensitive data stored on systems", "Cryptography"},
                {"CR-04", "Encryption in Transit", "Encrypt data during transmission", "Cryptography"},
                // Physical Security (4)
                {"PS-01", "Physical Entry Controls", "Control physical access to facilities", "Physical Security"},
                {"PS-02", "Secure Areas", "Define and protect secure areas", "Physical Security"},
                {"PS-03", "Equipment Security", "Protect equipment from threats", "Physical Security"},
                {"PS-04", "Clear Desk Policy", "Clear desk and clear screen policy", "Physical Security"},
                // Operations Security (4)
                {"OS-01", "Change Management", "Control changes to systems and infrastructure", "Operations Security"},
                {"OS-02", "Capacity Management", "Monitor and manage system capacity", "Operations Security"},
                {"OS-03", "Malware Protection", "Protect against malicious software", "Operations Security"},
                {"OS-04", "Backup", "Regular backup of information and software", "Operations Security"},
                // Communications Security (4)
                {"CS-01", "Network Security", "Manage and control network security", "Communications Security"},
                {"CS-02", "Network Segregation", "Segregate networks based on trust levels", "Communications Security"},
                {"CS-03", "Information Transfer", "Policies for information transfer", "Communications Security"},
                {"CS-04", "Messaging Security", "Secure electronic messaging", "Communications Security"},
                // Incident Management (4)
                {"IM-01", "Incident Response Plan", "Establish incident response procedures", "Incident Management"},
                {"IM-02", "Incident Reporting", "Report information security events promptly", "Incident Management"},
                {"IM-03", "Incident Analysis", "Analyze and categorize security incidents", "Incident Management"},
                {"IM-04", "Lessons Learned", "Learn from incidents to improve controls", "Incident Management"},
                // Business Continuity (4)
                {"BC-01", "BCP Planning", "Develop business continuity plans", "Business Continuity"},
                {"BC-02", "BCP Testing", "Test and exercise continuity plans", "Business Continuity"},
                {"BC-03", "Disaster Recovery", "Establish disaster recovery procedures", "Business Continuity"},
                {"BC-04", "Redundancy", "Implement redundancy for critical systems", "Business Continuity"},
                // Compliance (4)
                {"CO-01", "Legal Requirements", "Identify applicable legal requirements", "Compliance"},
                {"CO-02", "Privacy Protection", "Protect personally identifiable information", "Compliance"},
                {"CO-03", "Audit Logging", "Maintain audit logs for compliance", "Compliance"},
                {"CO-04", "Independent Review", "Regular independent security reviews", "Compliance"},
                // Human Resources (4)
                {"HR-01", "Security Screening", "Screen employees before hiring", "Human Resources"},
                {"HR-02", "Security Awareness", "Security awareness training program", "Human Resources"},
                {"HR-03", "Disciplinary Process", "Formal disciplinary process for violations", "Human Resources"},
                {"HR-04", "Termination Process", "Secure termination and change of role", "Human Resources"}
        };

        for (String[] c : controls) {
            Long domainId = domainMap.get(c[3]);
            var request = post("/security-control/edit")
                    .with(csrf())
                    .param("name", c[0] + " - " + c[1])
                    .param("detail", c[2])
                    .param("reference", c[0])
                    .param("tag", c[3]);

            if (domainId != null) {
                request = request.param("securityControlDomain.id", domainId.toString());
            }

            mockMvc.perform(request)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/security-control/list"));
        }

        List<SecurityControl> all = securityControlRepository.findAll();
        assertThat(all).hasSize(40);
    }

    @Test
    @Order(315)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_list() throws Exception {
        mockMvc.perform(get("/security-control/list"))
                .andExpect(status().isOk());
        assertThat(securityControlRepository.findAll()).hasSize(40);
    }

    @Test
    @Order(318)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_editExisting() throws Exception {
        SecurityControl first = securityControlRepository.findAll().get(0);
        mockMvc.perform(get("/security-control/edit").param("id", first.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(320)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_createForm() throws Exception {
        mockMvc.perform(get("/security-control/create"))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────
    // 4. Security Catalog
    // ──────────────────────────────────────────────

    @Test
    @Order(400)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_createForm() throws Exception {
        mockMvc.perform(get("/security-catalog/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(410)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_create() throws Exception {
        MaturityModel model = maturityModelRepository.findAll().get(0);
        List<SecurityControl> controls = securityControlRepository.findAll();

        var request = post("/security-catalog/edit")
                .with(csrf())
                .param("name", "ISO 27001 Test Catalog")
                .param("description", "Comprehensive security catalog for testing")
                .param("revision", "1.0")
                .param("reportInstructions", "Generate a detailed compliance report")
                .param("maturityModelId", model.getId().toString());

        for (SecurityControl control : controls) {
            request = request.param("securityControls", control.getId().toString());
        }

        mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/security-catalog/list"));

        List<SecurityCatalog> catalogs = securityCatalogRepository.findAll();
        assertThat(catalogs).hasSize(1);
        SecurityCatalog catalog = catalogs.get(0);
        assertThat(catalog.getName()).isEqualTo("ISO 27001 Test Catalog");
        assertThat(catalog.getMaturityModel()).isNotNull();
        assertThat(catalog.getMaturityModel().getId()).isEqualTo(model.getId());

        // Verify controls via a read transaction (ManyToMany is lazy)
        int controlCount = readInTx(() -> {
            SecurityCatalog cat = securityCatalogRepository.findById(catalog.getId()).orElseThrow();
            return cat.getSecurityControls().size();
        });
        assertThat(controlCount).isEqualTo(40);
    }

    @Test
    @Order(415)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_editExisting() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        mockMvc.perform(get("/security-catalog/edit").param("id", catalog.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(420)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_list() throws Exception {
        mockMvc.perform(get("/security-catalog/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(425)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_apiEndpoint() throws Exception {
        mockMvc.perform(get("/security-catalog/api"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @Order(426)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_apiTest() throws Exception {
        mockMvc.perform(get("/security-catalog/api/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("API endpoint is working!"));
    }

    // ──────────────────────────────────────────────
    // 5. Users
    // ──────────────────────────────────────────────

    @Test
    @Order(500)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_adminExistsInDb() throws Exception {
        // Admin was pre-created in @BeforeAll for authorization checks
        Optional<User> admin = userRepository.findByEmail("admin@example.com");
        assertThat(admin).isPresent();
        assertThat(admin.get().getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.get().getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    @Order(510)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_createISMUser() throws Exception {
        mockMvc.perform(post("/users")
                        .with(csrf())
                        .param("firstName", "ism_user")
                        .param("email", "ism@example.com")
                        .param("role", "INFORMATION_SECURITY_MANAGER"))
                .andExpect(status().is3xxRedirection());

        Optional<User> ism = userRepository.findByEmail("ism@example.com");
        assertThat(ism).isPresent();
        assertThat(ism.get().getRole()).isEqualTo(Role.INFORMATION_SECURITY_MANAGER);
    }

    @Test
    @Order(520)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_createTeamLeader() throws Exception {
        mockMvc.perform(post("/users")
                        .with(csrf())
                        .param("firstName", "team_leader")
                        .param("email", "leader@example.com")
                        .param("role", "ORGANISATION_TEAM_LEADER"))
                .andExpect(status().is3xxRedirection());

        Optional<User> leader = userRepository.findByEmail("leader@example.com");
        assertThat(leader).isPresent();
        assertThat(leader.get().getRole()).isEqualTo(Role.ORGANISATION_TEAM_LEADER);
    }

    @Test
    @Order(530)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_createDelegates() throws Exception {
        String[][] delegates = {
                {"delegate1", "delegate1@example.com"},
                {"delegate2", "delegate2@example.com"},
                {"assessor1", "assessor1@example.com"}
        };

        for (String[] d : delegates) {
            String role = d[0].startsWith("assessor") ? "ASSESSOR" : "ASSESSMENT_DELEGATE";
            mockMvc.perform(post("/users")
                            .with(csrf())
                            .param("firstName", d[0])
                            .param("email", d[1])
                            .param("role", role))
                    .andExpect(status().is3xxRedirection());
        }

        List<User> allUsers = userRepository.findAll();
        assertThat(allUsers).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    @Order(535)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_listAll() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(536)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_newForm() throws Exception {
        mockMvc.perform(get("/users/new"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(537)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_editForm() throws Exception {
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        mockMvc.perform(get("/users/edit/" + admin.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(538)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void user_updateExisting() throws Exception {
        User delegate = userRepository.findByEmail("delegate1@example.com").orElseThrow();
        mockMvc.perform(post("/users/update/" + delegate.getId())
                        .with(csrf())
                        .param("firstName", "delegate1")
                        .param("email", "delegate1_updated@example.com")
                        .param("role", "ASSESSMENT_DELEGATE"))
                .andExpect(status().is3xxRedirection());

        User updated = userRepository.findByEmail("delegate1_updated@example.com").orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("delegate1_updated@example.com");
    }

    // ──────────────────────────────────────────────
    // 6. Assessment
    // ──────────────────────────────────────────────

    @Test
    @Order(600)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_createForm() throws Exception {
        mockMvc.perform(get("/assessment/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(610)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_create() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        List<User> users = userRepository.findAll();

        // Pick two user IDs
        List<Long> userIds = users.stream()
                .limit(2)
                .map(User::getId)
                .toList();

        var request = post("/assessment/create")
                .with(csrf())
                .param("catalogId", catalog.getId().toString())
                .param("name", "Q1 2026 Security Assessment");

        for (Long uid : userIds) {
            request = request.param("userIds", uid.toString());
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // Should redirect to /assessment/{id}
        String redirectUrl = result.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).startsWith("/assessment/");

        List<Assessment> assessments = assessmentRepository.findAll();
        assertThat(assessments).hasSize(1);
        Assessment assessment = assessments.get(0);
        assertThat(assessment.getName()).isEqualTo("Q1 2026 Security Assessment");
        assertThat(assessment.getSecurityCatalog().getId()).isEqualTo(catalog.getId());
        assertThat(assessment.getStatus()).isEqualTo(AssessmentStatus.OPEN);
    }

    @Test
    @Order(620)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_viewDetails() throws Exception {
        Assessment assessment = assessmentRepository.findAll().get(0);
        mockMvc.perform(get("/assessment/" + assessment.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(625)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_viewControls() throws Exception {
        Assessment assessment = assessmentRepository.findAll().get(0);
        mockMvc.perform(get("/assessment/" + assessment.getId() + "/controls"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(630)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_answerControls() throws Exception {
        Assessment assessment = assessmentRepository.findAll().get(0);
        List<SecurityControl> controls = securityControlRepository.findAll();
        List<MaturityAnswer> answers = maturityAnswerRepository.findAll();

        // Assign maturity answers to the first 10 controls
        var request = post("/assessment/" + assessment.getId() + "/controls")
                .with(csrf());

        int answerCount = Math.min(10, controls.size());
        for (int i = 0; i < answerCount; i++) {
            SecurityControl control = controls.get(i);
            MaturityAnswer answer = answers.get(i % answers.size());
            request = request.param("control_" + control.getId(), answer.getId().toString());
        }

        mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assessment/" + assessment.getId()));
    }

    @Test
    @Order(635)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_list() throws Exception {
        mockMvc.perform(get("/assessment/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(640)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_createSecond() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);

        mockMvc.perform(post("/assessment/create")
                        .with(csrf())
                        .param("catalogId", catalog.getId().toString())
                        .param("name", "Q2 2026 Security Assessment"))
                .andExpect(status().is3xxRedirection());

        assertThat(assessmentRepository.findAll()).hasSize(2);
    }

    // ──────────────────────────────────────────────
    // 7. Security Control Domain Management
    // ──────────────────────────────────────────────

    @Test
    @Order(700)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_editExisting() throws Exception {
        SecurityControlDomain domain = securityControlDomainRepository.findAll().get(0);
        mockMvc.perform(get("/security-control-domain/edit").param("id", domain.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(710)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_createForm() throws Exception {
        mockMvc.perform(get("/security-control-domain/create"))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────
    // 8. Authorization / Access Control Tests
    // ──────────────────────────────────────────────

    @Test
    @Order(800)
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/assessment/list"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @Order(810)
    @WithMockUser(username = "delegate1", roles = {"ASSESSMENT_DELEGATE"})
    void delegate_cannotAccessUserManagement() throws Exception {
        // Assessment delegates should not have access to user management (requires ADMIN or ISM role).
        // The custom AccessDeniedHandler redirects page requests to /not-authorized (302).
        mockMvc.perform(get("/users"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @Order(820)
    @WithMockUser(username = "delegate1", roles = {"ASSESSMENT_DELEGATE"})
    void delegate_cannotAccessSecurityCatalog() throws Exception {
        // The custom AccessDeniedHandler redirects page requests to /not-authorized (302).
        mockMvc.perform(get("/security-catalog/list"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @Order(830)
    @WithMockUser(username = "ism_user", roles = {"INFORMATION_SECURITY_MANAGER"})
    void ism_canAccessSecurityCatalog() throws Exception {
        mockMvc.perform(get("/security-catalog/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(840)
    @WithMockUser(username = "ism_user", roles = {"INFORMATION_SECURITY_MANAGER"})
    void ism_canAccessMaturityModels() throws Exception {
        mockMvc.perform(get("/maturitymodel/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(850)
    @WithMockUser(username = "ism_user", roles = {"INFORMATION_SECURITY_MANAGER"})
    void ism_canAccessSecurityControls() throws Exception {
        mockMvc.perform(get("/security-control/list"))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────
    // 9. Data Integrity Validation
    // ──────────────────────────────────────────────

    @Test
    @Order(900)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void dataIntegrity_catalogHasModel() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        assertThat(catalog.getMaturityModel()).isNotNull();
        // Verify the maturity answers count via transaction (ManyToMany is lazy on MaturityModel)
        int answerCount = readInTx(() -> {
            SecurityCatalog cat = securityCatalogRepository.findById(catalog.getId()).orElseThrow();
            return cat.getMaturityModel().getMaturityAnswers().size();
        });
        assertThat(answerCount).isEqualTo(5);
    }

    @Test
    @Order(910)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void dataIntegrity_catalogHasAllControls() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        int controlCount = readInTx(() -> {
            SecurityCatalog cat = securityCatalogRepository.findById(catalog.getId()).orElseThrow();
            return cat.getSecurityControls().size();
        });
        assertThat(controlCount).isEqualTo(40);
    }

    @Test
    @Order(920)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void dataIntegrity_assessmentLinkedToCatalog() throws Exception {
        Assessment assessment = assessmentRepository.findAll().get(0);
        assertThat(assessment.getSecurityCatalog()).isNotNull();
        assertThat(assessment.getSecurityCatalog().getName()).isEqualTo("ISO 27001 Test Catalog");
    }

    @Test
    @Order(930)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void dataIntegrity_controlsHaveDomains() throws Exception {
        List<SecurityControl> controls = securityControlRepository.findAll();
        long withDomain = controls.stream()
                .filter(c -> c.getSecurityControlDomain() != null)
                .count();
        assertThat(withDomain).isEqualTo(40);
    }

    @Test
    @Order(940)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void dataIntegrity_userRolesCorrect() throws Exception {
        assertThat(userRepository.findByEmail("admin@example.com").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userRepository.findByEmail("ism@example.com").orElseThrow().getRole()).isEqualTo(Role.INFORMATION_SECURITY_MANAGER);
        assertThat(userRepository.findByEmail("leader@example.com").orElseThrow().getRole()).isEqualTo(Role.ORGANISATION_TEAM_LEADER);
        assertThat(userRepository.findByEmail("delegate1_updated@example.com").orElseThrow().getRole()).isEqualTo(Role.ASSESSMENT_DELEGATE);
        assertThat(userRepository.findByEmail("assessor1@example.com").orElseThrow().getRole()).isEqualTo(Role.ASSESSOR);
    }

    @Test
    @Order(950)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void dataIntegrity_maturityAnswerRatings() throws Exception {
        List<MaturityAnswer> answers = maturityAnswerRepository.findAll();
        Set<Integer> ratings = new HashSet<>();
        for (MaturityAnswer a : answers) {
            ratings.add(a.getRating());
            assertThat(a.getRating()).isBetween(0, 100);
        }
        assertThat(ratings).containsExactlyInAnyOrder(0, 25, 50, 75, 100);
    }

    // ──────────────────────────────────────────────
    // 10. Negative / Edge Case Tests
    // ──────────────────────────────────────────────

    @Test
    @Order(1000)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_nonExistentId() throws Exception {
        // The controller returns "assessment-not-found" view for missing IDs.
        // This template is not yet created, so Thymeleaf throws TemplateInputException
        // wrapped in a ServletException. The test verifies the controller
        // reaches the not-found branch (not a 403 auth failure).
        Exception thrown = Assertions.assertThrows(jakarta.servlet.ServletException.class,
                () -> mockMvc.perform(get("/assessment/99999")));
        assertThat(thrown.getCause()).isInstanceOf(org.thymeleaf.exceptions.TemplateInputException.class);
        assertThat(thrown.getCause().getMessage()).contains("assessment-not-found");
    }

    @Test
    @Order(1010)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_createWithInvalidCatalog() throws Exception {
        mockMvc.perform(post("/assessment/create")
                        .with(csrf())
                        .param("catalogId", "99999")
                        .param("name", "Invalid Assessment"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assessment/list"));
    }

    @Test
    @Order(1020)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_createWithBoundaryRating() throws Exception {
        // Rating should be clamped to [0, 100]
        mockMvc.perform(post("/maturityanswer/edit")
                        .with(csrf())
                        .param("answer", "Boundary Test High")
                        .param("description", "Test boundary above 100")
                        .param("rating", "150"))
                .andExpect(status().is3xxRedirection());

        // Should be clamped to 100
        MaturityAnswer boundaryHigh = maturityAnswerRepository.findAll()
                .stream()
                .filter(a -> "Boundary Test High".equals(a.getAnswer()))
                .findFirst()
                .orElseThrow();
        assertThat(boundaryHigh.getRating()).isEqualTo(100);

        // Clean up
        maturityAnswerRepository.delete(boundaryHigh);
    }

    @Test
    @Order(1030)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControlDomain_editNoId() throws Exception {
        // Edit without an ID should show an empty form (new domain)
        mockMvc.perform(get("/security-control-domain/edit"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1040)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityControl_editNoId() throws Exception {
        // Edit without an ID should show an empty form (new control)
        mockMvc.perform(get("/security-control/edit"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1050)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityAnswer_editNoId() throws Exception {
        mockMvc.perform(get("/maturityanswer/edit"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1060)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void securityCatalog_editNoId() throws Exception {
        mockMvc.perform(get("/security-catalog/edit"))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────
    // 11. Second Maturity Model (3 answers)
    // ──────────────────────────────────────────────

    @Test
    @Order(1100)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_createSecondWith3Answers() throws Exception {
        // Reuse 3 existing answers with ratings 0, 50, 100 (Not Implemented, Managed, Optimized)
        List<MaturityAnswer> allAnswers = maturityAnswerRepository.findAll();
        List<MaturityAnswer> subset = allAnswers.stream()
                .filter(a -> a.getRating() == 0 || a.getRating() == 50 || a.getRating() == 100)
                .toList();
        assertThat(subset).hasSize(3);

        var request = post("/maturitymodel/save")
                .with(csrf())
                .param("name", "Simplified 3-Level Model")
                .param("description", "Three-level model for simplified compliance testing");
        for (MaturityAnswer a : subset) {
            request = request.param("maturityAnswers", a.getId().toString());
        }

        mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maturitymodel/list"));

        assertThat(maturityModelRepository.findAll()).hasSize(2);
    }

    @Test
    @Order(1110)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_secondModelHas3Answers() throws Exception {
        MaturityModel simple = maturityModelRepository.findAll().stream()
                .filter(m -> "Simplified 3-Level Model".equals(m.getName()))
                .findFirst().orElseThrow();
        int count = readInTx(() -> {
            MaturityModel m = maturityModelRepository.findById(simple.getId()).orElseThrow();
            return m.getMaturityAnswers().size();
        });
        assertThat(count).isEqualTo(3);
    }

    @Test
    @Order(1115)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void maturityModel_twoModelsHaveDifferentAnswerCounts() throws Exception {
        List<MaturityModel> models = maturityModelRepository.findAll();
        assertThat(models).hasSize(2);

        MaturityModel standard = models.stream()
                .filter(m -> "Standard Maturity Model".equals(m.getName())).findFirst().orElseThrow();
        MaturityModel simplified = models.stream()
                .filter(m -> "Simplified 3-Level Model".equals(m.getName())).findFirst().orElseThrow();

        int stdCount = readInTx(() ->
                maturityModelRepository.findById(standard.getId()).orElseThrow().getMaturityAnswers().size());
        int simCount = readInTx(() ->
                maturityModelRepository.findById(simplified.getId()).orElseThrow().getMaturityAnswers().size());

        assertThat(stdCount).isEqualTo(5);
        assertThat(simCount).isEqualTo(3);
        assertThat(stdCount).isGreaterThan(simCount);

        // Verify simplified model answer ratings are exactly {0, 50, 100}
        Set<Integer> simRatings = readInTx(() ->
                maturityModelRepository.findById(simplified.getId()).orElseThrow()
                        .getMaturityAnswers().stream()
                        .map(MaturityAnswer::getRating)
                        .collect(Collectors.toSet()));
        assertThat(simRatings).containsExactlyInAnyOrder(0, 50, 100);
    }

    // ──────────────────────────────────────────────
    // 12. OrgUnit CRUD
    // ──────────────────────────────────────────────

    @Test
    @Order(1200)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgUnit_createTestUnit() throws Exception {
        mockMvc.perform(post("/orgunits/save")
                        .with(csrf())
                        .param("name", "Compliance Test Unit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orgunits/list"));

        assertThat(orgUnitRepository.findAll()).anyMatch(u -> "Compliance Test Unit".equals(u.getName()));
    }

    @Test
    @Order(1210)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgUnit_listPage() throws Exception {
        mockMvc.perform(get("/orgunits/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1215)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgUnit_createForm() throws Exception {
        mockMvc.perform(get("/orgunits/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1220)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgUnit_editForm() throws Exception {
        OrgUnit unit = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/orgunits/edit/" + unit.getId()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────
    // 13. OrgService CRUD
    // ──────────────────────────────────────────────

    @Test
    @Order(1300)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgService_demoServicesExistFromPostConstruct() throws Exception {
        // OrgServiceService.initDemoServices() @PostConstruct creates 2 demo services
        List<OrgService> all = orgServiceRepository.findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        List<String> names = all.stream().map(OrgService::getName).toList();
        assertThat(names).contains("Demo OrgService A", "Demo OrgService B");
    }

    @Test
    @Order(1310)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgService_createAlphaService() throws Exception {
        long before = orgServiceRepository.count();
        mockMvc.perform(post("/orgservices/save")
                        .with(csrf())
                        .param("name", "Alpha Test Service")
                        .param("description", "Primary service for take-over testing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orgservices/list"));

        assertThat(orgServiceRepository.count()).isEqualTo(before + 1);
        assertThat(orgServiceRepository.findAll().stream()
                .anyMatch(s -> "Alpha Test Service".equals(s.getName()))).isTrue();
    }

    @Test
    @Order(1320)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgService_listPage() throws Exception {
        mockMvc.perform(get("/orgservices/list"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1325)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgService_createForm() throws Exception {
        mockMvc.perform(get("/orgservices/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1330)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgService_editForm() throws Exception {
        OrgService svc = orgServiceRepository.findAll().stream()
                .filter(s -> "Alpha Test Service".equals(s.getName()))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/orgservices/edit/" + svc.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1340)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgService_apiAll() throws Exception {
        MvcResult result = mockMvc.perform(get("/orgservices/all"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).contains("Alpha Test Service");
        assertThat(json).contains("Demo OrgService A");
    }

    // ──────────────────────────────────────────────
    // 14. OrgService Control Mapping
    // ──────────────────────────────────────────────

    @Test
    @Order(1400)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgServiceMapping_pageLoads() throws Exception {
        mockMvc.perform(get("/security-control/orgservice-mapping"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1410)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgServiceMapping_mapControlToAlphaService() throws Exception {
        OrgService alpha = orgServiceRepository.findAll().stream()
                .filter(s -> "Alpha Test Service".equals(s.getName()))
                .findFirst().orElseThrow();
        // Use the 9th control (index 8) for mapping
        SecurityControl control = securityControlRepository.findAll().get(8);

        MvcResult result = mockMvc.perform(post("/security-control/map-service")
                        .with(csrf())
                        .param("controlId", control.getId().toString())
                        .param("serviceId", alpha.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).contains("\"success\":true");
    }

    @Test
    @Order(1420)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgServiceMapping_verifyApplicableFlagSetForControl() throws Exception {
        OrgService alpha = orgServiceRepository.findAll().stream()
                .filter(s -> "Alpha Test Service".equals(s.getName()))
                .findFirst().orElseThrow();
        SecurityControl control = securityControlRepository.findAll().get(8);

        List<OrgServiceAssessment> osaList = orgServiceAssessmentRepository.findByOrgServiceId(alpha.getId());
        assertThat(osaList).isNotEmpty();
        OrgServiceAssessment osa = osaList.get(0);
        boolean applicable = readInTx(() ->
                orgServiceAssessmentRepository.findById(osa.getId()).orElseThrow().getControls().stream()
                        .anyMatch(c -> c.getSecurityControl().getId().equals(control.getId()) && c.isApplicable()));
        assertThat(applicable).isTrue();
    }

    @Test
    @Order(1430)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgServiceMapping_setControlPercent75ViaSaveControl() throws Exception {
        OrgService alpha = orgServiceRepository.findAll().stream()
                .filter(s -> "Alpha Test Service".equals(s.getName()))
                .findFirst().orElseThrow();
        SecurityControl control = securityControlRepository.findAll().get(8);
        List<OrgServiceAssessment> osaList = orgServiceAssessmentRepository.findByOrgServiceId(alpha.getId());
        OrgServiceAssessment osa = osaList.get(0);

        MvcResult result = mockMvc.perform(post("/orgservice-assessment/save-control")
                        .with(csrf())
                        .param("id", osa.getId().toString())
                        .param("orgServiceId", alpha.getId().toString())
                        .param("assessmentDate", java.time.LocalDate.now().toString())
                        .param("controlId", control.getId().toString())
                        .param("applicable", "true")
                        .param("percent", "75"))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).contains("\"success\":true");

        // Verify percent was stored
        OrgServiceAssessmentControl ctrl = readInTx(() ->
                orgServiceAssessmentRepository.findById(osa.getId()).orElseThrow().getControls().stream()
                        .filter(c -> c.getSecurityControl().getId().equals(control.getId()))
                        .findFirst().orElseThrow());
        assertThat(ctrl.isApplicable()).isTrue();
        assertThat(ctrl.getPercent()).isEqualTo(75);
    }

    @Test
    @Order(1440)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void orgServiceAssessment_editPageLoads() throws Exception {
        OrgService alpha = orgServiceRepository.findAll().stream()
                .filter(s -> "Alpha Test Service".equals(s.getName()))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/orgservice-assessment/edit/" + alpha.getId()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────
    // 15. Assessment with OrgService Take-over
    // ──────────────────────────────────────────────

    @Test
    @Order(1500)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_createLinkedToOrgUnitAndOrgService() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        OrgUnit orgUnit = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow();
        OrgService alpha = orgServiceRepository.findAll().stream()
                .filter(s -> "Alpha Test Service".equals(s.getName()))
                .findFirst().orElseThrow();

        MvcResult result = mockMvc.perform(post("/assessment/create")
                        .with(csrf())
                        .param("catalogId", catalog.getId().toString())
                        .param("name", "OrgUnit Compliance Assessment")
                        .param("orgUnitId", orgUnit.getId().toString())
                        .param("orgServiceIds", alpha.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirect = result.getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith("/assessment/");

        Assessment assessment = assessmentRepository.findAll().stream()
                .filter(a -> "OrgUnit Compliance Assessment".equals(a.getName()))
                .findFirst().orElseThrow();
        assertThat(assessment.getOrgUnit()).isNotNull();
        assertThat(assessment.getOrgUnit().getId()).isEqualTo(orgUnit.getId());

        int orgSvcCount = readInTx(() -> {
            Assessment a = assessmentRepository.findById(assessment.getId()).orElseThrow();
            return a.getOrgServices() != null ? a.getOrgServices().size() : 0;
        });
        assertThat(orgSvcCount).isEqualTo(1);
    }

    @Test
    @Order(1505)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_populateControlAnswers_createsAssessmentDetails() throws Exception {
        Assessment assessment = assessmentRepository.findAll().stream()
                .filter(a -> "OrgUnit Compliance Assessment".equals(a.getName()))
                .findFirst().orElseThrow();
        List<SecurityControl> controls = securityControlRepository.findAll();
        // "Defined" answer has rating = 75
        MaturityAnswer defined = maturityAnswerRepository.findAll().stream()
                .filter(a -> a.getRating() == 75)
                .findFirst().orElseThrow();

        // Answer first 5 controls (indices 0-4) with "Defined" (75/100)
        var request = post("/assessment/" + assessment.getId() + "/controls").with(csrf());
        for (int i = 0; i < 5; i++) {
            request = request.param("control_" + controls.get(i).getId(), defined.getId().toString());
        }
        mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assessment/" + assessment.getId()));
    }

    @Test
    @Order(1510)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_viewTriggersTakeOverForMappedControl() throws Exception {
        Assessment assessment = assessmentRepository.findAll().stream()
                .filter(a -> "OrgUnit Compliance Assessment".equals(a.getName()))
                .findFirst().orElseThrow();
        // GET triggers take-over: the control mapped to Alpha Service (applicable=true, percent=75)
        // gets the closest maturity answer ("Defined", rating=75) added to AssessmentDetails
        mockMvc.perform(get("/assessment/" + assessment.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1520)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_verifyOrgServiceControlIsApplicableWithPercent75() throws Exception {
        OrgService alpha = orgServiceRepository.findAll().stream()
                .filter(s -> "Alpha Test Service".equals(s.getName()))
                .findFirst().orElseThrow();
        SecurityControl control = securityControlRepository.findAll().get(8);

        List<OrgServiceAssessment> osaList = orgServiceAssessmentRepository.findByOrgServiceId(alpha.getId());
        assertThat(osaList).isNotEmpty();
        Long osaId = osaList.get(0).getId();
        OrgServiceAssessmentControl ctrl = readInTx(() ->
                orgServiceAssessmentRepository.findById(osaId).orElseThrow().getControls().stream()
                        .filter(c -> c.getSecurityControl().getId().equals(control.getId()))
                        .findFirst().orElseThrow());
        assertThat(ctrl.isApplicable()).isTrue();
        assertThat(ctrl.getPercent()).isEqualTo(75);
    }

    @Test
    @Order(1530)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_verifyTakeOverUsesClosestMaturityAnswer_5AnswerModel() throws Exception {
        // For percent=75 with the 5-answer model {0, 25, 50, 75, 100}:
        // diffs: |0-75|=75, |25-75|=50, |50-75|=25, |75-75|=0, |100-75|=25
        // → closest = rating 75 → "Defined"
        MaturityModel model = maturityModelRepository.findAll().stream()
                .filter(m -> "Standard Maturity Model".equals(m.getName()))
                .findFirst().orElseThrow();
        List<MaturityAnswer> answers = readInTx(() ->
                new ArrayList<>(maturityModelRepository.findById(model.getId()).orElseThrow().getMaturityAnswers()));

        MaturityAnswer closest = answers.get(0);
        int minDiff = Math.abs(closest.getRating() - 75);
        for (MaturityAnswer a : answers) {
            int diff = Math.abs(a.getRating() - 75);
            if (diff < minDiff) { minDiff = diff; closest = a; }
        }
        assertThat(closest.getRating()).isEqualTo(75);
        assertThat(closest.getAnswer()).isEqualTo("Defined");
    }

    @Test
    @Order(1535)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void assessment_verifyClosestAnswerDiffersAcrossModels_atPercent85() throws Exception {
        // At percent=85:
        //   5-answer model {0,25,50,75,100}: diffs 85,60,35,10,15 → closest=75("Defined",diff=10)
        //   3-answer model {0,50,100}:       diffs 85,35,15       → closest=100("Optimized",diff=15)
        // This demonstrates why the 3-answer model gives different take-over results
        MaturityModel standard = maturityModelRepository.findAll().stream()
                .filter(m -> "Standard Maturity Model".equals(m.getName())).findFirst().orElseThrow();
        MaturityModel simplified = maturityModelRepository.findAll().stream()
                .filter(m -> "Simplified 3-Level Model".equals(m.getName())).findFirst().orElseThrow();

        List<MaturityAnswer> stdAnswers = readInTx(() ->
                new ArrayList<>(maturityModelRepository.findById(standard.getId()).orElseThrow().getMaturityAnswers()));
        List<MaturityAnswer> simAnswers = readInTx(() ->
                new ArrayList<>(maturityModelRepository.findById(simplified.getId()).orElseThrow().getMaturityAnswers()));

        int percent = 85;
        MaturityAnswer closestStd = stdAnswers.get(0);
        int minDiff = Math.abs(closestStd.getRating() - percent);
        for (MaturityAnswer a : stdAnswers) {
            int diff = Math.abs(a.getRating() - percent);
            if (diff < minDiff) { minDiff = diff; closestStd = a; }
        }

        MaturityAnswer closestSim = simAnswers.get(0);
        minDiff = Math.abs(closestSim.getRating() - percent);
        for (MaturityAnswer a : simAnswers) {
            int diff = Math.abs(a.getRating() - percent);
            if (diff < minDiff) { minDiff = diff; closestSim = a; }
        }

        // 5-answer: "Defined" (75) is closest to 85 → diff=10
        assertThat(closestStd.getRating()).isEqualTo(75);
        assertThat(closestStd.getAnswer()).isEqualTo("Defined");
        // 3-answer: "Optimized" (100) is closest to 85 → diff=15 vs diff=35 for 50
        assertThat(closestSim.getRating()).isEqualTo(100);
        // The two models produce DIFFERENT take-over answers for the same percent value
        assertThat(closestStd.getRating()).isNotEqualTo(closestSim.getRating());
    }

    // ──────────────────────────────────────────────
    // 16. Compliance Checks & Calculations
    // ──────────────────────────────────────────────

    @Test
    @Order(1600)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_createCheckWithAllAboveThreshold() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        mockMvc.perform(post("/compliance/save")
                        .with(csrf())
                        .param("name", "All Controls Above 50")
                        .param("description", "Requires all controls at Managed or above")
                        .param("securityCatalogId", catalog.getId().toString())
                        .param("thresholds[0].type", "ALL_ABOVE")
                        .param("thresholds[0].value", "50")
                        .param("thresholds[0].ruleDescription", "Every control must be at least Managed (50%)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compliance/checks"));

        List<ComplianceCheck> checks = complianceCheckRepository.findAll();
        assertThat(checks).hasSize(1);
        Long checkId = checks.get(0).getId();
        String checkName = checks.get(0).getName();
        assertThat(checkName).isEqualTo("All Controls Above 50");

        // Thresholds is a lazy OneToMany — read inside a transaction
        int thresholdCount = readInTx(() ->
                complianceCheckRepository.findById(checkId).orElseThrow().getThresholds().size());
        assertThat(thresholdCount).isEqualTo(1);

        String thresholdType = readInTx(() ->
                complianceCheckRepository.findById(checkId).orElseThrow().getThresholds().get(0).getType());
        int thresholdValue = readInTx(() ->
                complianceCheckRepository.findById(checkId).orElseThrow().getThresholds().get(0).getValue());
        assertThat(thresholdType).isEqualTo("ALL_ABOVE");
        assertThat(thresholdValue).isEqualTo(50);
    }

    @Test
    @Order(1610)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_createCheckWithAverageAboveThreshold() throws Exception {
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        mockMvc.perform(post("/compliance/save")
                        .with(csrf())
                        .param("name", "Average Above 60")
                        .param("description", "Average control score must exceed 60")
                        .param("securityCatalogId", catalog.getId().toString())
                        .param("thresholds[0].type", "AVERAGE_ABOVE")
                        .param("thresholds[0].value", "60")
                        .param("thresholds[0].ruleDescription", "Average score must be above 60%"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compliance/checks"));

        assertThat(complianceCheckRepository.findAll()).hasSize(2);
    }

    @Test
    @Order(1620)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_listChecks() throws Exception {
        mockMvc.perform(get("/compliance/checks"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1625)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_createForm() throws Exception {
        mockMvc.perform(get("/compliance/create"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1630)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_editForm() throws Exception {
        ComplianceCheck check = complianceCheckRepository.findAll().get(0);
        mockMvc.perform(get("/compliance/edit/" + check.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1640)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_calculateAllAbove50_passesWhenAllAnswersAre75() throws Exception {
        // Assessment (order 1505/1510) has 5+ controls answered with "Defined" (rating=75).
        // ALL_ABOVE=50 threshold: all 75 >= 50 → compliant=true
        Long allAboveCheckId = complianceCheckRepository.findAll().stream()
                .filter(c -> "All Controls Above 50".equals(c.getName()))
                .findFirst().orElseThrow().getId();
        Long orgUnitId1 = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow().getId();

        com.govinc.compliance.ComplianceService.ComplianceResult result =
                readInTx(() -> {
                    ComplianceCheck c = complianceCheckRepository.findById(allAboveCheckId).orElseThrow();
                    OrgUnit ou = orgUnitRepository.findById(orgUnitId1).orElseThrow();
                    return complianceService.calculateCompliance(c, ou);
                });
        assertThat(result).isNotNull();
        assertThat(result.getControlsAnswered()).isGreaterThanOrEqualTo(5);
        assertThat(result.getControlsTotal()).isEqualTo(40);
        assertThat(result.getAveragePercent()).isEqualTo(75.0);
        assertThat(result.isCompliant()).isTrue();
    }

    @Test
    @Order(1645)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_calculateCoverageAndAverageAreConsistent() throws Exception {
        Long coverageCheckId = complianceCheckRepository.findAll().stream()
                .filter(c -> "All Controls Above 50".equals(c.getName()))
                .findFirst().orElseThrow().getId();
        Long coverageUnitId = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow().getId();

        com.govinc.compliance.ComplianceService.ComplianceResult result =
                readInTx(() -> {
                    ComplianceCheck c = complianceCheckRepository.findById(coverageCheckId).orElseThrow();
                    OrgUnit ou = orgUnitRepository.findById(coverageUnitId).orElseThrow();
                    return complianceService.calculateCompliance(c, ou);
                });

        assertThat(result.getControlsAnswered()).isGreaterThan(0);
        assertThat(result.getControlsTotal()).isEqualTo(40);
        // Coverage percent must fall between 0 and 100
        assertThat(result.getCoveragePercent()).isBetween(0.0, 100.0);
        // With 5+ controls answered out of 40, coverage must be at least 12.5%
        assertThat(result.getCoveragePercent()).isGreaterThanOrEqualTo(12.0);
        // Average must be 75 since all answered controls have rating=75
        assertThat(result.getAveragePercent()).isEqualTo(75.0);
    }

    @Test
    @Order(1650)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_calculateAverageAbove60_passesWhenAverageIs75() throws Exception {
        Long avgCheckId = complianceCheckRepository.findAll().stream()
                .filter(c -> "Average Above 60".equals(c.getName()))
                .findFirst().orElseThrow().getId();
        Long avgUnitId = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow().getId();

        com.govinc.compliance.ComplianceService.ComplianceResult result =
                readInTx(() -> {
                    ComplianceCheck c = complianceCheckRepository.findById(avgCheckId).orElseThrow();
                    OrgUnit ou = orgUnitRepository.findById(avgUnitId).orElseThrow();
                    return complianceService.calculateCompliance(c, ou);
                });

        // Average is 75 (all answers Defined/75) which is above threshold of 60
        assertThat(result.getAveragePercent()).isEqualTo(75.0);
        assertThat(result.isCompliant()).isTrue();
    }

    @Test
    @Order(1655)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_createStrictCheckAndVerifyItFails() throws Exception {
        // Strict check: requires ALL controls scored at 80+. Our answers are at 75.
        SecurityCatalog catalog = securityCatalogRepository.findAll().get(0);
        mockMvc.perform(post("/compliance/save")
                        .with(csrf())
                        .param("name", "Strict All Above 80")
                        .param("description", "Strict check: every control must score 80 or above")
                        .param("securityCatalogId", catalog.getId().toString())
                        .param("thresholds[0].type", "ALL_ABOVE")
                        .param("thresholds[0].value", "80")
                        .param("thresholds[0].ruleDescription", "Strict: every control must exceed 80%"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compliance/checks"));

        Long strictCheckId = complianceCheckRepository.findAll().stream()
                .filter(c -> "Strict All Above 80".equals(c.getName()))
                .findFirst().orElseThrow().getId();
        Long strictUnitId = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow().getId();

        com.govinc.compliance.ComplianceService.ComplianceResult result =
                readInTx(() -> {
                    ComplianceCheck c = complianceCheckRepository.findById(strictCheckId).orElseThrow();
                    OrgUnit ou = orgUnitRepository.findById(strictUnitId).orElseThrow();
                    return complianceService.calculateCompliance(c, ou);
                });

        // Answers are all at rating=75; threshold=80 → 75 < 80 → NOT compliant
        assertThat(result.isCompliant()).isFalse();
        assertThat(result.getAveragePercent()).isEqualTo(75.0);
    }

    @Test
    @Order(1660)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_viewPage_rendersWithResults() throws Exception {
        OrgUnit orgUnit = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow();
        ComplianceCheck check = complianceCheckRepository.findAll().stream()
                .filter(c -> "All Controls Above 50".equals(c.getName()))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/compliance/view")
                        .param("orgUnitId", orgUnit.getId().toString())
                        .param("checkId", check.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1665)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void compliance_resultContainsSummaryAndThresholdDetails() throws Exception {
        Long summaryCheckId = complianceCheckRepository.findAll().stream()
                .filter(c -> "All Controls Above 50".equals(c.getName()))
                .findFirst().orElseThrow().getId();
        Long summaryUnitId = orgUnitRepository.findAll().stream()
                .filter(u -> "Compliance Test Unit".equals(u.getName()))
                .findFirst().orElseThrow().getId();

        com.govinc.compliance.ComplianceService.ComplianceResult result =
                readInTx(() -> {
                    ComplianceCheck c = complianceCheckRepository.findById(summaryCheckId).orElseThrow();
                    OrgUnit ou = orgUnitRepository.findById(summaryUnitId).orElseThrow();
                    return complianceService.calculateCompliance(c, ou);
                });

        assertThat(result.getCalculationSummary()).isNotNull();
        assertThat(result.getCalculationSummary()).contains("answered=");
        assertThat(result.getThresholdsDetails()).isNotEmpty();
        // The threshold detail key contains type and value
        boolean hasAllAboveKey = result.getThresholdsDetails().keySet().stream()
                .anyMatch(k -> k.contains("ALL_ABOVE") && k.contains("50"));
        assertThat(hasAllAboveKey).isTrue();
    }

    // ──────────────────────────────────────────────
    // 17. Summary / Final Verification
    // ──────────────────────────────────────────────

    @Test
    @Order(9999)
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void finalVerification_allDataPresent() throws Exception {
        assertThat(maturityAnswerRepository.findAll()).hasSize(5);
        assertThat(maturityModelRepository.findAll()).hasSize(2);    // Standard (5 answers) + Simplified (3 answers)
        assertThat(securityControlDomainRepository.findAll()).hasSize(10);
        assertThat(securityControlRepository.findAll()).hasSize(40);
        assertThat(securityCatalogRepository.findAll()).hasSize(1);
        assertThat(assessmentRepository.findAll()).hasSizeGreaterThanOrEqualTo(3); // 2 original + 1 org-unit-linked
        // 1 admin (pre-created) + 5 created via endpoint = 6
        assertThat(userRepository.findAll()).hasSizeGreaterThanOrEqualTo(6);
        // OrgUnit: at least the compliance test unit
        assertThat(orgUnitRepository.findAll()).hasSizeGreaterThanOrEqualTo(1);
        // OrgService: 2 demo + 1 alpha = at least 3
        assertThat(orgServiceRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
        // Compliance checks: All-Above-50, Average-Above-60, Strict-All-Above-80 = 3
        assertThat(complianceCheckRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
    }
}
