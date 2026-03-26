package com.govinc.governance;

public enum TaskStatus {
    IDENTIFIED("Identified"),
    IN_PROGRESS("In Progress"),
    READY_FOR_APPROVAL("Ready for Approval"),
    DONE("Done");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
