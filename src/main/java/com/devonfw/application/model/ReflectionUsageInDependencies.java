package com.devonfw.application.model;

import java.util.List;

/**
 * Model for entries of the reflection usage in dependencies list
 */
public class ReflectionUsageInDependencies {

   String jarFile;
   List<String> classes;

    public ReflectionUsageInDependencies(String jarFile, List<String> classes) {
        this.jarFile = jarFile;
        this.classes = classes;
    }

    public String getJarFile() {
        return jarFile;
    }

    public void setJarFile(String jarFile) {
        this.jarFile = jarFile;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }
}
