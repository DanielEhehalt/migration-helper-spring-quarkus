package com.devonfw.application.model;

/**
 * Model for Blacklist entries
 */
public class BlacklistEntry {
    private String id;
    private String description;
    private String blacklistedPackage;

    public BlacklistEntry(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBlacklistedPackage() {
        return blacklistedPackage;
    }

    public void setBlacklistedPackage(String blacklistedPackage) {
        this.blacklistedPackage = blacklistedPackage;
    }
}