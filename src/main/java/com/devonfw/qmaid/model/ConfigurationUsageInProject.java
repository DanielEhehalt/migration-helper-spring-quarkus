package com.devonfw.qmaid.model;

/**
 * Model for Spring configuration usage entries
 */
public class ConfigurationUsageInProject {

    private String className;
    private String path;

    public ConfigurationUsageInProject(String className, String path) {

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
