package com.devonfw.application.model;

import java.util.List;

/**
 * Model for entries of the reflection usage list
 */
public class ReflectionUsageEntry {

    private String path;
    private List<String> classes;

    public ReflectionUsageEntry(String path, List<String> classes) {
        this.path = path;
        this.classes = classes;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }
}
