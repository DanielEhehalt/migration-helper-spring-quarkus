package com.devonfw.application.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for found MTA issues
 */
public class MtaIssue {

    private String mtaRuleId;
    private List<MavenIdentifier> mavenIdentifiers;
    private List<String> packages;
    private String description;
    private Boolean generalIssue;

    public MtaIssue(String mtaRuleId, String description) {

        this.mtaRuleId = mtaRuleId;
        this.description = description;
        this.mavenIdentifiers = new ArrayList<>();
        this.packages = new ArrayList<>();
        this.generalIssue = false;
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

    public Boolean getGeneralIssue() {
        return generalIssue;
    }

    public void setGeneralIssue(Boolean generalIssue) {
        this.generalIssue = generalIssue;
    }

    public static class MavenIdentifier {

        private String groupId;
        private String artifactId;

        public MavenIdentifier(String groupId, String artifactId) {

            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }
    }
}