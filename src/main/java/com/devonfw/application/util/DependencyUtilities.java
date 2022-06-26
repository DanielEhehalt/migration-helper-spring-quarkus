package com.devonfw.application.util;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import com.devonfw.application.model.ReflectionUsageInDependencies;
import org.eclipse.aether.artifact.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility class to operate on dependencies
 */
public class DependencyUtilities {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyUtilities.class);

    /**
     * This method replaces the jar file names with the full names of the artifacts
     *
     * @param reflectionUsageInDependencies List with the jar file names to replace
     * @param allArtifactsOfProject List with all artifacts of the project
     * @return List with replaced names
     */
    public static List<ReflectionUsageInDependencies> mapJarFilesToFullArtifactNames(
            List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
            List<Artifact> allArtifactsOfProject) {

        List<ReflectionUsageInDependencies> result = new ArrayList<>();

        reflectionUsageInDependencies.forEach(reflectionUsageInDependency -> {
            Optional<Artifact> optionalArtifact = allArtifactsOfProject.stream().filter(artifact -> {
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
}
