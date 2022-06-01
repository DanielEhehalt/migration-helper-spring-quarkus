package com.devonfw.application.model;

/**
 * Model for entries of the analysation failure list
 */
public class AnalysisFailureEntry {

    private String filename;
    private String path;
    private String description;

    public AnalysisFailureEntry(String filename, String path, String description) {
        this.filename = filename;
        this.path = path;
        this.description = description;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
