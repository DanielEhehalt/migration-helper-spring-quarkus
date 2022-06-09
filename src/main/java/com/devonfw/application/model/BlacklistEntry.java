package com.devonfw.application.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for Blacklist entries
 */
public class BlacklistEntry {

    private String mtaRuleId;
    private List<MavenIdentifier> mavenIdentifiers;
    private List<String> packages;
    private String description;

    public BlacklistEntry(String mtaRuleId, String description) {

        this.mtaRuleId = mtaRuleId;
        this.description = description;
        this.mavenIdentifiers = new ArrayList<>();
        this.packages = new ArrayList<>();
    }

    public String getMtaRuleId() {
        return mtaRuleId;
    }

    public void setMtaRuleId(String mtaRuleId) {
        this.mtaRuleId = mtaRuleId;
    }

    public List<MavenIdentifier> getMavenIdentifiers() {
        return mavenIdentifiers;
    }

    public void setMavenIdentifiers(List<MavenIdentifier> mavenIdentifiers) {
        this.mavenIdentifiers = mavenIdentifiers;
    }

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}