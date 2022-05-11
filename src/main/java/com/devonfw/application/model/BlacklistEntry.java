package com.devonfw.application.model;

/**
 * Model for Blacklist entries
 */
public class BlacklistEntry {
    private String name;
    private String description;

    public BlacklistEntry(String name, String description) {
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