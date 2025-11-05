package com.govinc.assessment;

/**
 * Simple data class to track the progress of report generation
 */
public class ReportProgress {
    private int percent;
    private String status;

    public ReportProgress(int percent, String status) {
        this.percent = percent;
        this.status = status;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
