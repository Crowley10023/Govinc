package com.govinc.assessment;

import java.time.LocalDate;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.compliance.ComplianceCheck;
import com.govinc.user.User;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgService; // add this import
import java.util.Set;
import java.util.HashSet;
import jakarta.persistence.*;

@Entity
@Table(name = "assessments")
public class Assessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "security_catalog_id")
    private SecurityCatalog securityCatalog;

    // --- New ManyToOne Relationship to OrgUnit ---
    @ManyToOne(optional = true)
    @JoinColumn(name = "orgunit_id")
    private OrgUnit orgUnit;
    // ------------------------------------------------

    private LocalDate creationDate;
    private LocalDate closeDate;

    private String name;

    @Enumerated(EnumType.STRING)
    private AssessmentStatus status = AssessmentStatus.OPEN;

    // Removed assessmentUrl field

    @OneToOne(mappedBy = "assessment", cascade = CascadeType.ALL)
    private AssessmentUrls assessmentUrls;

    // NEW: Predecessor assessment reference
    @ManyToOne
    @JoinColumn(name = "predecessor_id")
    private Assessment predecessor;

    // Compliance check selected for this assessment
    @ManyToOne(optional = true)
    @JoinColumn(name = "compliance_check_id")
    private ComplianceCheck complianceCheck;

    // AI-generated management summary
    @Column(length = 10000)
    private String managementSummary;

    @ManyToMany(cascade = { CascadeType.MERGE })
    @JoinTable(name = "assessment_orgservice", joinColumns = @JoinColumn(name = "assessment_id"), inverseJoinColumns = @JoinColumn(name = "orgservice_id"))
    private Set<OrgService> orgServices = new HashSet<>();

    public void setOrgServices(Set<OrgService> orgServices) {
        this.orgServices = orgServices;
    }

    public Assessment() {
        this.status = AssessmentStatus.OPEN;
    }

    public Assessment(SecurityCatalog securityCatalog, LocalDate creationDate, String name, AssessmentStatus status) {
        this.securityCatalog = securityCatalog;
        this.creationDate = creationDate;
        this.name = name;
        this.status = status != null ? status : AssessmentStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SecurityCatalog getSecurityCatalog() {
        return securityCatalog;
    }

    public void setSecurityCatalog(SecurityCatalog securityCatalog) {
        this.securityCatalog = securityCatalog;
    }

    // --- OrgUnit Getter & Setter ---
    public OrgUnit getOrgUnit() {
        return orgUnit;
    }

    public void setOrgUnit(OrgUnit orgUnit) {
        this.orgUnit = orgUnit;
    }
    // -------------------------------

    public LocalDate getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }

    public LocalDate getCloseDate() {
        return closeDate;
    }

    public void setCloseDate(LocalDate closeDate) {
        this.closeDate = closeDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AssessmentStatus getStatus() {
        if (status == null) {
            status = AssessmentStatus.OPEN;
        }
        return status;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
    }

    public boolean isClosed() {
        return AssessmentStatus.CLOSED.equals(this.status);
    }

    public boolean isOpen() {
        return AssessmentStatus.OPEN.equals(this.status);
    }

    // Removed getAssessmentUrl and setAssessmentUrl methods

    public AssessmentUrls getAssessmentUrls() {
        return assessmentUrls;
    }

    public void setAssessmentUrls(AssessmentUrls assessmentUrls) {
        this.assessmentUrls = assessmentUrls;
    }

    @ManyToMany
    @JoinTable(name = "assessment_users", joinColumns = @JoinColumn(name = "assessment_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> users = new HashSet<>();

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    // CREATED BY: single user reference
    @ManyToOne
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    // NEW: Getter and Setter for predecessor
    public Assessment getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(Assessment predecessor) {
        this.predecessor = predecessor;
    }

    public ComplianceCheck getComplianceCheck() {
        return complianceCheck;
    }

    public void setComplianceCheck(ComplianceCheck complianceCheck) {
        this.complianceCheck = complianceCheck;
    }

    public String getManagementSummary() {
        return managementSummary;
    }

    public void setManagementSummary(String managementSummary) {
        this.managementSummary = managementSummary;
    }

    public Set<OrgService> getOrgServices() {
        return orgServices;
    }

    // Whether the AI answering guide should be shown in the assessment-direct (public URL) view
    // Nullable so existing rows (NULL) default to false without a migration
    @Column(name = "guide_visible_in_direct")
    private Boolean guideVisibleInDirect;

    public boolean isGuideVisibleInDirect() {
        return Boolean.TRUE.equals(guideVisibleInDirect);
    }

    public void setGuideVisibleInDirect(boolean guideVisibleInDirect) {
        this.guideVisibleInDirect = guideVisibleInDirect;
    }

    // Absolute expiration date for the assessment's direct URL (replaces day-decrement counter)
    @Column(name = "url_expiration_date")
    private LocalDate urlExpirationDate;

    public LocalDate getUrlExpirationDate() {
        return urlExpirationDate;
    }

    public void setUrlExpirationDate(LocalDate urlExpirationDate) {
        this.urlExpirationDate = urlExpirationDate;
    }

    /**
     * Returns the number of days until the direct URL expires.
     * Negative values mean the URL has already expired.
     */
    public long getDaysUntilUrlExpiration() {
        if (urlExpirationDate == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), urlExpirationDate);
    }

    }
