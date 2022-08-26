package com.devonfw.qmaid.model;

import java.util.ArrayList;
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
    List<String> allPossiblePackagesIncludingDependencies;
    List<String> allPossibleClassesIncludingDependencies;
    List<String> occurrenceInProjectClasses;
    Boolean isBlacklisted;
    String descriptionIfBlacklisted;

    public ProjectDependency(String groupId, String artifactId, String version, List<String> packages, List<String> classes) {

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packages = packages;
        this.classes = classes;
        this.allPossiblePackagesIncludingDependencies = new ArrayList<>();
        this.allPossibleClassesIncludingDependencies = new ArrayList<>();
        this.isBlacklisted = false;
        this.occurrenceInProjectClasses = new ArrayList<>();
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

    public List<String> getAllPossiblePackagesIncludingDependencies() {
        return allPossiblePackagesIncludingDependencies;
    }

    public void setAllPossiblePackagesIncludingDependencies(List<String> allPossiblePackagesIncludingDependencies) {
        this.allPossiblePackagesIncludingDependencies = allPossiblePackagesIncludingDependencies;
    }

    public List<String> getAllPossibleClassesIncludingDependencies() {
        return allPossibleClassesIncludingDependencies;
    }

    public void setAllPossibleClassesIncludingDependencies(List<String> allPossibleClassesIncludingDependencies) {
        this.allPossibleClassesIncludingDependencies = allPossibleClassesIncludingDependencies;
    }

    public List<String> getOccurrenceInProjectClasses() {
        return occurrenceInProjectClasses;
    }

    public void setOccurrenceInProjectClasses(List<String> occurrenceInProjectClasses) {
        this.occurrenceInProjectClasses = occurrenceInProjectClasses;
    }

    public Boolean getBlacklisted() {
        return isBlacklisted;
    }

    public void setBlacklisted(Boolean blacklisted) {
        isBlacklisted = blacklisted;
    }

    public String getDescriptionIfBlacklisted() {
        return descriptionIfBlacklisted;
    }

    public void setDescriptionIfBlacklisted(String descriptionIfBlacklisted) {
        this.descriptionIfBlacklisted = descriptionIfBlacklisted;
    }
}
