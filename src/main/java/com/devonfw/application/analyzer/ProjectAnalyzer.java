package com.devonfw.application.analyzer;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import com.devonfw.application.model.ProjectDependency;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaSource;
import org.eclipse.aether.artifact.Artifact;
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
     * This method creates a list of ProjectDependency objects based on the found artifacts.
     * The ProjectDependency objects are enriched with the included packages and classes
     *
     * @param allArtifactsAfterTreeBuilding Artifacts to discover packages and classes
     * @return All ProjectDependencies enhanced with all possible classes and packages
     */
    public static List<ProjectDependency> createProjectDependencyObjectFromArtifacts(
            List<Artifact> allArtifactsAfterTreeBuilding) {

        List<ProjectDependency> projectDependencies = new ArrayList<>();

        for (Artifact artifact : allArtifactsAfterTreeBuilding) {
            File dependencyLocation = artifact.getFile();
            ProjectDependency projectDependency =
                    new ProjectDependency(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), new ArrayList<>(), new ArrayList<>());
            if (dependencyLocation.exists()) {
                try {
                    ZipInputStream zip = new ZipInputStream(new FileInputStream(dependencyLocation));
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        String filepath = entry.getName();
                        String fqnOfClassWithFileExtension = filepath.replace('/', '.');
                        if (!entry.isDirectory() && filepath.endsWith(".class") && !filepath.contains("$")
                                && fqnOfClassWithFileExtension.contains(artifact.getGroupId())) {
                            String fqnOfClass = fqnOfClassWithFileExtension.substring(0, fqnOfClassWithFileExtension.length() - ".class".length());
                            projectDependency.getClasses().add(fqnOfClass);
                            String packageOfClass = fqnOfClass.substring(0, fqnOfClass.lastIndexOf("."));
                            if (!projectDependency.getPackages().contains(packageOfClass)) {
                                projectDependency.getPackages().add(packageOfClass);
                            }
                        }
                    }
                } catch (IOException e) {
                    AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(
                            artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(),
                            "Could not find jar file in local maven repository. Collecting classes and packages of this artifact is not possible."));
                    LOG.debug("Could not find jar file in local maven repository for artifact: " +
                            artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() +
                            ". Collecting classes and packages of this artifact is not possible.");
                }
            }
            projectDependencies.add(projectDependency);
        }
        return projectDependencies;
    }

    /**
     * This method collects all import statements recursively across all java files from an entrypoint and tries to assign them to the project
     * dependencies
     *
     * @param entryPoint          Directory as entrypoint
     * @param projectDependencies List of the project dependencies
     */
    public static Integer executeOccurrenceMeasurementOfProjectDependencies(File entryPoint,
                                                                            List<ProjectDependency> projectDependencies) {
        Integer totalFilesScanned = 0;
        File[] files = entryPoint.listFiles();
        if (files == null) {
            AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(entryPoint.toString(),
                    "Can not collect import statements from this entrypoint recursively. No files found"));
            LOG.error("Can not collect import statements from this entrypoint recursively. No files found in: " + entryPoint);
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".java")) {
                totalFilesScanned += collectImportStatementsFromClassAndMapThemToTheAssociatedDependency(file, projectDependencies);
            } else if (file.isDirectory()) {
                totalFilesScanned += executeOccurrenceMeasurementOfProjectDependencies(file, projectDependencies);
            }
        }
        return totalFilesScanned;
    }

    /**
     * This method searches for import statements in a source code file, tries to assign them to a project dependency and increments the occurrence
     * counter of the associated dependency
     *
     * @param file                Java source code file
     * @param projectDependencies List of the project dependencies
     */
    private static Integer collectImportStatementsFromClassAndMapThemToTheAssociatedDependency(File file,
                                                                                               List<ProjectDependency> projectDependencies) {

        Integer totalFilesScanned = 0;
        JavaProjectBuilder builder = new JavaProjectBuilder();
        try {
            builder.addSource(new FileReader(file));
        } catch (FileNotFoundException e) {
            AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(file.toString(),
                    "Can not collect import statements from file " + file + " FileNotFoundException was thrown."));
            LOG.debug("Can not collect import statements from file" + file, e);
        }
        Collection<JavaSource> sources = builder.getSources();
        for (JavaSource source : sources) {
            List<ProjectDependency> alreadyCountedForThisSource = new ArrayList<>();
            List<String> importStatements = source.getImports();
            totalFilesScanned++;
            for (String importStatement : importStatements) {
                if (importStatement.endsWith("*")) {
                    String packageOfImportStatement = importStatement.substring(0, importStatement.length() - 2);
                    Optional<ProjectDependency> optionalProjectDependency = projectDependencies.stream()
                            .filter(projectDependency -> projectDependency.getPackages()
                                    .contains(packageOfImportStatement)).findFirst();
                    countOccurrenceAndCheckForDuplicates(optionalProjectDependency, alreadyCountedForThisSource);
                } else {
                    Optional<ProjectDependency> optionalProjectDependency = projectDependencies.stream()
                            .filter(projectDependency -> projectDependency.getClasses().contains(importStatement))
                            .findFirst();
                    countOccurrenceAndCheckForDuplicates(optionalProjectDependency, alreadyCountedForThisSource);
                }
            }
        }
        return totalFilesScanned;
    }

    private static void countOccurrenceAndCheckForDuplicates(Optional<ProjectDependency> optionalProjectDependency,
                                                             List<ProjectDependency> alreadyCountedForThisSource) {

        if (optionalProjectDependency.isPresent()) {
            ProjectDependency projectDependency = optionalProjectDependency.get();
            if (!alreadyCountedForThisSource.contains(projectDependency)) {
                projectDependency.incrementOccurrenceInProjectClasses();
                alreadyCountedForThisSource.add(projectDependency);
            }
        }
    }
}
