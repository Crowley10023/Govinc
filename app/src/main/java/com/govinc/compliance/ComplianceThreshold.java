package com.govinc.compliance;

import jakarta.persistence.*;

@Entity
public class ComplianceThreshold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ruleDescription;
    private String type; // "ALL_ABOVE" or "AVERAGE_ABOVE" for UI extension
    private int value; // value as percentage (e.g. 50 for 50%)

    @ManyToOne
    @JoinColumn(name = "compliance_check_id")
    private ComplianceCheck complianceCheck;

    // Getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleDescription() { return ruleDescription; }
    public void setRuleDescription(String ruleDescription) { this.ruleDescription = ruleDescription; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public ComplianceCheck getComplianceCheck() { return complianceCheck; }
    public void setComplianceCheck(ComplianceCheck complianceCheck) { this.complianceCheck = complianceCheck; }
}