package com.devonfw.application.util;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import com.devonfw.application.model.ProjectDependency;
import com.devonfw.application.model.ReflectionUsageInDependencies;
import com.devonfw.application.operator.DependencyTreeOperator;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class to operate on dependencies
 */
public class DependencyUtilities {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyUtilities.class);

    /**
     * This method creates a list of ProjectDependency objects based on the found artifacts. The ProjectDependency objects are enriched with the
     * included packages and classes
     *
     * @param allArtifactsAfterTreeBuilding Artifacts to discover packages and classes
     * @return All ProjectDependencies enhanced with all possible classes and packages
     */
    public static List<ProjectDependency> createProjectDependencyObjectsFromArtifacts(List<Artifact> allArtifactsAfterTreeBuilding) {

        List<ProjectDependency> projectDependencies = new ArrayList<>();

        for (Artifact artifact : allArtifactsAfterTreeBuilding) {
            File dependencyLocation = artifact.getFile();
            ProjectDependency projectDependency = new ProjectDependency(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                    new ArrayList<>(), new ArrayList<>());
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
     * This method replaces the jar file names with the full names of the artifacts
     *
     * @param reflectionUsageInDependencies List with the jar file names to replace
     * @param allArtifactsAfterTreeBuilding List with all artifacts of the project
     * @return List with replaced names
     */
    public static List<ReflectionUsageInDependencies> mapJarFilesToFullArtifactNames(
            List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
            List<Artifact> allArtifactsAfterTreeBuilding) {

        List<ReflectionUsageInDependencies> result = new ArrayList<>();

        reflectionUsageInDependencies.forEach(reflectionUsageInDependency -> {
            Optional<Artifact> optionalArtifact = allArtifactsAfterTreeBuilding.stream().filter(artifact -> {
                StringBuilder jarFile = new StringBuilder(artifact.getArtifactId());
                jarFile.append("-");
                jarFile.append(artifact.getVersion());
                String classifier = artifact.getClassifier();
                if (classifier != null && !classifier.equals("")) {
                    jarFile.append("-");
                    jarFile.append(classifier);
                }
                jarFile.append(".jar");
                return jarFile.toString().equals(reflectionUsageInDependency.getJarFile());
            }).findAny();
            if (optionalArtifact.isPresent()) {
                Artifact artifact = optionalArtifact.get();
                result.add(new ReflectionUsageInDependencies(
                        artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(),
                        reflectionUsageInDependency.getClasses()));
            } else {
                AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(reflectionUsageInDependency.getJarFile(),
                        "Reflection usage found for this artifact, but it could not be assigned to any dependency in the dependency tree."));
            }
        });
        return result;
    }

    /**
     * This method enhances the project dependencies with all packages and classes, including the packages and classes of their dependencies
     *
     * @param dependencyBlacklist     List of blacklisted dependencies
     * @param projectDependencies     List of all project dependencies
     * @param dependencyTreeRootNodes Dependency tree
     */
    public static void enhanceProjectDependencyWithPackagesAndClasses(List<ProjectDependency> dependencyBlacklist,
                                                                      List<ProjectDependency> projectDependencies,
                                                                      List<DependencyNode> dependencyTreeRootNodes) {

        for (ProjectDependency blacklistEntry : dependencyBlacklist) {

            List<String> allPossiblePackagesOfBlacklistEntry = new ArrayList<>(blacklistEntry.getPackages());
            List<String> allPossibleClassesOfBlacklistEntry = new ArrayList<>(blacklistEntry.getClasses());

            DependencyNode dependencyNode = DependencyTreeOperator.findDependencyInDependencyTree(dependencyTreeRootNodes, blacklistEntry);

            if (dependencyNode != null) {
                DependencyTreeOperator.collectPackagesAndClassesFromChildren(dependencyNode, allPossiblePackagesOfBlacklistEntry,
                        allPossibleClassesOfBlacklistEntry, projectDependencies, 1);
            }
            blacklistEntry.setAllPossiblePackagesIncludingDependencies(allPossiblePackagesOfBlacklistEntry);
            blacklistEntry.setAllPossibleClassesIncludingDependencies(allPossibleClassesOfBlacklistEntry);
        }
    }
}
