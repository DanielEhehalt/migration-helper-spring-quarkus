package com.devonfw.application.model;

/**
 * Model for entries of the reflection usage list
 */
public class ReflectionUsageEntry {

    private String nameOfClass;
    private String nameOfPackage;

    public ReflectionUsageEntry(String nameOfClass, String nameOfPackage) {
        this.nameOfClass = nameOfClass;
        this.nameOfPackage = nameOfPackage;
    }

    public String getNameOfClass() {
        return nameOfClass;
    }

    public void setNameOfClass(String nameOfClass) {
        this.nameOfClass = nameOfClass;
    }

    public String getNameOfPackage() {
        return nameOfPackage;
    }

    public void setNameOfPackage(String nameOfPackage) {
        this.nameOfPackage = nameOfPackage;
    }
}
