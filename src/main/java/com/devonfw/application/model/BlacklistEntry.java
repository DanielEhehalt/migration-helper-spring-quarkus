package com.devonfw.application.model;

/**
 * Model for Blacklist entries
 */
public class BlacklistEntry {
    private String ruleId;
    private String description;
    private String nameOfPackage;

    public BlacklistEntry(String ruleId, String description) {
        this.ruleId = ruleId;
        this.description = description;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNameOfPackage() {
        return nameOfPackage;
    }

    public void setNameOfPackage(String nameOfPackage) {
        this.nameOfPackage = nameOfPackage;
    }
}