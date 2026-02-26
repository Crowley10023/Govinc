package com.govinc.statistics;

import java.util.List;

public class DashboardResponse {
    private long assessmentsCount;
    private long maturityAnswers; // answers given in assessments (count of AssessmentControlAnswer with maturityAnswer)
    private List<CatalogDistribution> distribution;
    private List<RecentAssessment> recentAssessments;
    private List<MonthStat> monthlyCreatedAssessments;

    public DashboardResponse() {}

    public DashboardResponse(long assessmentsCount, long maturityAnswers, List<CatalogDistribution> distribution, List<RecentAssessment> recentAssessments, List<MonthStat> monthlyCreatedAssessments) {
        this.assessmentsCount = assessmentsCount;
        this.maturityAnswers = maturityAnswers;
        this.distribution = distribution;
        this.recentAssessments = recentAssessments;
        this.monthlyCreatedAssessments = monthlyCreatedAssessments;
    }

    public long getAssessmentsCount() {
        return assessmentsCount;
    }

    public void setAssessmentsCount(long assessmentsCount) {
        this.assessmentsCount = assessmentsCount;
    }

    public long getMaturityAnswers() {
        return maturityAnswers;
    }

    public void setMaturityAnswers(long maturityAnswers) {
        this.maturityAnswers = maturityAnswers;
    }

    public List<CatalogDistribution> getDistribution() {
        return distribution;
    }

    public void setDistribution(List<CatalogDistribution> distribution) {
        this.distribution = distribution;
    }

    public List<RecentAssessment> getRecentAssessments() {
        return recentAssessments;
    }

    public void setRecentAssessments(List<RecentAssessment> recentAssessments) {
        this.recentAssessments = recentAssessments;
    }

    public List<MonthStat> getMonthlyCreatedAssessments() {
        return monthlyCreatedAssessments;
    }

    public void setMonthlyCreatedAssessments(List<MonthStat> monthlyCreatedAssessments) {
        this.monthlyCreatedAssessments = monthlyCreatedAssessments;
    }

    public static class CatalogDistribution {
        private Long catalogId;
        private String catalogName;
        private long count;

        public CatalogDistribution() {}

        public CatalogDistribution(Long catalogId, String catalogName, long count) {
            this.catalogId = catalogId;
            this.catalogName = catalogName;
            this.count = count;
        }

        public Long getCatalogId() { return catalogId; }
        public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }

        public String getCatalogName() { return catalogName; }
        public void setCatalogName(String catalogName) { this.catalogName = catalogName; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    public static class RecentAssessment {
        private Long id;
        private String name;
        private String creationDate;
        private String catalogName;
        private String status;

        public RecentAssessment() {}

        public RecentAssessment(Long id, String name, String creationDate, String catalogName, String status) {
            this.id = id;
            this.name = name;
            this.creationDate = creationDate;
            this.catalogName = catalogName;
            this.status = status;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCreationDate() { return creationDate; }
        public void setCreationDate(String creationDate) { this.creationDate = creationDate; }

        public String getCatalogName() { return catalogName; }
        public void setCatalogName(String catalogName) { this.catalogName = catalogName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class MonthStat {
        private String label;
        private long count;

        public MonthStat() {}

        public MonthStat(String label, long count) {
            this.label = label;
            this.count = count;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
