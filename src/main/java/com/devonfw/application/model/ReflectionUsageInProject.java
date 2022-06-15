package com.devonfw.application.model;

/**
 * Model for entries of the reflection usage in project list
 */
public class ReflectionUsageInProject {

    private String className;
    private String path;

    public ReflectionUsageInProject(String className, String path) {
        this.className = className;
        this.path = path;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
