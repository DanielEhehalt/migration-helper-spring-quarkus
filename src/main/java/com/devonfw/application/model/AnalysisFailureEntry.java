package com.devonfw.application.model;

/**
 * Model for entries of the analysation failure list
 */
public class AnalysisFailureEntry {

    private String name;
    private String description;

    public AnalysisFailureEntry(String name, String description) {

        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
