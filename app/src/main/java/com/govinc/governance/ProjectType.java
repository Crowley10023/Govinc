package com.govinc.governance;

public enum ProjectType {
    DEVIATION_MANAGEMENT("Deviation Management", "Project type to allow tracking of changes in the maturity."),
    CHANGE_MANAGEMENT("Change Management", "Allows changes to Security Controls to be tracked with this project."),
    OTHER("Other", "Free-form project that collects tasks.");

    private final String displayName;
    private final String description;

    ProjectType(String displayName, String description) {
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
