package com.devonfw.application.analyzer;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for analyzing java projects
 */
public class ProjectAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectAnalyzer.class);

    /**
     * Generates HashMap with the location of the library as key and all classes of the dependency as value
     *
     * @param libraries List of the library locations
     * @return HashMap with the location of the library as key and all classes of the dependency as value
     */
    public static HashMap<String, List<String>> getAllClassesOfAllLibraries(List<String> libraries) {

        HashMap<String, List<String>> classesOfLibraries = new HashMap<>();
        for (String library : libraries) {
            try {
                if (library.endsWith(".jar")) {
                    ZipInputStream zip = new ZipInputStream(new FileInputStream(library));
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                            String className = entry.getName().replace('/', '.'); // including ".class"
                            String substringWithoutClass = className.substring(0, className.length() - ".class".length());
                            if (classesOfLibraries.containsKey(library)) {
                                List<String> classes = classesOfLibraries.get(library);
                                classes.add(substringWithoutClass);
                                classesOfLibraries.replace(library, classes);
                            } else {
                                List<String> classes = new ArrayList<>();
                                classes.add(substringWithoutClass);
                                classesOfLibraries.put(library, classes);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(library, "Could not find jar archive. IOException was thrown"));
                LOG.debug("Could not find jar archive", e);
            }
        }
        return classesOfLibraries;
    }

    /**
     * Creates a HashMap with the import statements as keys and the paths of all libraries that could provide the import as values
     *
     * @param imports            Import Statements as for searching
     * @param classesOfLibraries Classes of all libraries for comparison
     * @return HashMap with the import statements as keys and the paths of all libraries that could provide the import as values
     */
    public static HashMap<String, List<String>> mapImportStatementsToLibraries(List<String> imports, HashMap<String, List<String>> classesOfLibraries) {

        HashMap<String, List<String>> importToDependency = new HashMap<>();
        for (String importsEntry : imports) {
            for (Map.Entry<String, List<String>> classes : classesOfLibraries.entrySet()) {
                classes.getValue().forEach(classEntry -> {
                    if (importToDependency.containsKey(importsEntry) && !importToDependency.get(importsEntry).contains(classes.getKey())) {
                        List<String> dependenciesList = importToDependency.get(importsEntry);
                        dependenciesList.add(classes.getKey());
                        importToDependency.replace(importsEntry, dependenciesList);
                    } else if (classEntry.equals(importsEntry)) {
                        List<String> dependenciesList = new ArrayList<>();
                        dependenciesList.add(classes.getKey());
                        importToDependency.put(importsEntry, dependenciesList);
                    }
                });
            }
        }
        return importToDependency;
    }

    /**
     * Collect and count all import statements recursively across all java files from an entrypoint
     *
     * @param entryPoint Directory as entrypoint
     * @param results    HashMap with import statement as key and total number of occurrences
     * @return HashMap with import statement as key and total number of occurrences
     */
    public static HashMap<String, Integer> collectImportStatementsFromAllClasses(File entryPoint, HashMap<String, Integer> results) {

        File[] files = entryPoint.listFiles();
        if (files == null) {
            LOG.info("Can not collect import statements. No files to analyze found in: " + entryPoint);
            return results;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".java")) {
                scanForImportStatementsInClass(file, results);
            } else if (file.isDirectory()) {
                collectImportStatementsFromAllClasses(file, results);
            }
        }
        return results;
    }

    /**
     * Searches for import statements in a source code file.
     *
     * @param file    Java source code file
     * @param results HashMap with import statements as key and the found total occurrence as value
     */
    private static void scanForImportStatementsInClass(File file, HashMap<String, Integer> results) {

        JavaProjectBuilder builder = new JavaProjectBuilder();
        try {
            builder.addSource(new FileReader(file));
        } catch (FileNotFoundException e) {
            AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(file.toString(), "Can not collect import statements from file " + file + " FileNotFoundException was thrown."));
            LOG.debug("Can not collect import statements from file" + file, e);
        }
        Collection<JavaSource> sources = builder.getSources();
        for (JavaSource source : sources) {
            List<String> importStatements = source.getImports();
            for (String importStatement : importStatements) {
                if (results.get(importStatement) != null) {
                    int quantity = results.get(importStatement);
                    results.replace(importStatement, quantity + 1);
                } else {
                    results.put(importStatement, 1);
                }
            }
        }
    }
}