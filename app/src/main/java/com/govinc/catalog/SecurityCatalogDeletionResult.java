package com.govinc.catalog;

import java.util.ArrayList;
import java.util.List;

public class SecurityCatalogDeletionResult {
    private boolean success;
    private String message;
    private List<String> controlsDeleted;
    private List<String> controlsSkipped;
    private List<String> warnings;

    public SecurityCatalogDeletionResult() {
        this.controlsDeleted = new ArrayList<>();
        this.controlsSkipped = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public SecurityCatalogDeletionResult(boolean success, String message) {
        this();
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getControlsDeleted() {
        return controlsDeleted;
    }

    public void addControlDeleted(String controlName) {
        this.controlsDeleted.add(controlName);
    }

    public List<String> getControlsSkipped() {
        return controlsSkipped;
    }

    public void addControlSkipped(String controlName) {
        this.controlsSkipped.add(controlName);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder(message);
        
        if (!controlsDeleted.isEmpty()) {
            sb.append("\n\nDeleted controls: ").append(String.join(", ", controlsDeleted));
        }
        
        if (!controlsSkipped.isEmpty()) {
            sb.append("\n\nControls not deleted (used by other catalogs): ");
            if (controlsSkipped.size() <= 3) {
                sb.append(String.join(", ", controlsSkipped));
            } else {
                sb.append(String.join(", ", controlsSkipped.subList(0, 3)))
                  .append(" and ").append(controlsSkipped.size() - 3).append(" more");
            }
        }
        
        if (!warnings.isEmpty()) {
            sb.append("\n\nWarnings: ").append(String.join("; ", warnings));
        }
        
        return sb.toString();
    }
}