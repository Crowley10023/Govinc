package com.govinc.user;

/**
 * User role enumeration for role-based access control (RBAC).
 * 
 * - ADMIN: Full access to all features
 * - INFORMATION_SECURITY_MANAGER: Full access except configuration
 * - ORGANISATION_TEAM_LEADER: Access to assessments and org units in their organization or children
 * - ASSESSMENT_DELEGATE: Only access to assessments where they are assigned
 */
public enum Role {
    ADMIN("Admin", "Full system access"),
    INFORMATION_SECURITY_MANAGER("Information Security Manager", "Full access except configuration"),
    ORGANISATION_TEAM_LEADER("Organisation Team Leader", "Access to organization units and their assessments"),
    ASSESSMENT_DELEGATE("Assessment Delegate", "Access only to assigned assessments");

    private final String displayName;
    private final String description;

    Role(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
