package com.devonfw.application.model;

import java.util.List;

/**
 * Model for project dependencies
 */
public class ProjectDependency {

    String groupId;
    String artifactId;
    String version;
    List<String> packages;
    List<String> classes;
    Integer occurrenceInProjectClasses;
    String descriptionIfBlacklisted;

    public ProjectDependency(String groupId, String artifactId, String version, List<String> packages,
                             List<String> classes) {

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packages = packages;
        this.classes = classes;
        this.occurrenceInProjectClasses = 0;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public Integer getOccurrenceInProjectClasses() {
        return occurrenceInProjectClasses;
    }

    public void setOccurrenceInProjectClasses(Integer occurrenceInProjectClasses) {
        this.occurrenceInProjectClasses = occurrenceInProjectClasses;
    }

    public void incrementOccurrenceInProjectClasses() {
        this.occurrenceInProjectClasses++;
    }

    public String getDescriptionIfBlacklisted() {
        return descriptionIfBlacklisted;
    }

    public void setDescriptionIfBlacklisted(String descriptionIfBlacklisted) {
        this.descriptionIfBlacklisted = descriptionIfBlacklisted;
    }
}
