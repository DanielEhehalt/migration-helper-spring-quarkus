package com.devonfw.qmaid.operator;

import com.devonfw.qmaid.collector.AnalysisFailureCollector;
import com.devonfw.qmaid.model.AnalysisFailureEntry;
import com.devonfw.qmaid.model.ProjectDependency;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaSource;
import net.sf.mmm.code.impl.java.JavaContext;
import net.sf.mmm.code.impl.java.source.maven.JavaSourceProviderUsingMaven;
import net.sf.mmm.code.impl.java.source.maven.MavenDependencyCollector;
import net.sf.mmm.code.java.maven.impl.MavenBridgeImpl;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Operator class for project analysis
 */
public class ProjectOperator {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectOperator.class);

    List<Artifact> applicationStartupLibrariesOfProject;
    Integer totalJavaClassesScanned;

    public ProjectOperator(File inputProjectLocation, File mavenRepoLocation, File applicationEntryPointLocation) {

        totalJavaClassesScanned = 0;
        collectAllApplicationStartupLibrariesOfProject(inputProjectLocation, mavenRepoLocation, applicationEntryPointLocation);
    }

    /**
     * This method creates a java context based on the @SpringBootApplication class and returns the loaded dependencies
     *
     */
    private void collectAllApplicationStartupLibrariesOfProject(File inputProjectLocation, File mavenRepoLocation, File applicationEntryPointLocation) {

        MavenDependencyCollector dependencyCollector =
                new MavenDependencyCollector(new MavenBridgeImpl(mavenRepoLocation), false, true, null);
        JavaContext context;
        try {
            context = JavaSourceProviderUsingMaven.createFromLocalMavenProject(inputProjectLocation, dependencyCollector);
        } catch (Exception e) {
            LOG.error("Failed to read effective model for this project. Please build the project with mvn package load the dependencies in your local maven repository", e);
            System.exit(5);
            return;
        }

        applicationStartupLibrariesOfProject = new ArrayList<>();
        URL[] urls = dependencyCollector.asUrls();
        for (URL url : urls) {
            String urlWithoutType;
            if(SystemUtils.IS_OS_WINDOWS) {
                // cutting Windows file urls: file://
                urlWithoutType = url.toString().substring(6);
            } else {
                // cutting Linux file urls: file:/
                urlWithoutType = url.toString().substring(5);
            }
            if (urlWithoutType.endsWith(".jar")) {
                try {
                    Model model = new MavenBridgeImpl().readEffectiveModelFromLocation(new File(urlWithoutType), false);
                    Artifact artifact = new DefaultArtifact(model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion());
                    applicationStartupLibrariesOfProject.add(artifact);
                } catch (Exception e) {
                    AnalysisFailureCollector.addAnalysisFailure(
                            new AnalysisFailureEntry(urlWithoutType, "Failed to resolve effective POM of this artifact"));
                    LOG.debug("Failed to resolve effective POM this artifact: " + urlWithoutType, e);
                }
            }
        }
    }

    /**
     * This method collects all import statements recursively across all java files from an entrypoint and tries to assign them to the project
     * dependencies
     *
     * @param entry                   Folder to scan java classes
     * @param dependencyBlacklist     List with the blacklisted dependencies
     * @param dependencyTreeOperator  DependencyTreeOperator to get all project dependencies
     */
    public void occurrenceMeasurement(File entry, List<ProjectDependency> dependencyBlacklist, DependencyTreeOperator dependencyTreeOperator) {

        // Occurrence measurement can be enhanced with transitive dependencies of the blacklist items
        dependencyTreeOperator.enhanceDirectDependencyWithPackagesAndClassesFromTransitiveDependencies(dependencyBlacklist);

        File[] files = entry.listFiles();
        if (files == null) {
            AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(entry.getParent(),
                    "Can not collect import statements from this entrypoint recursively. No files found"));
            LOG.error("Can not collect import statements from this entrypoint recursively. No files found in: " + entry.getParent());
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".java")) {
                count(file, dependencyBlacklist);
            } else if (file.isDirectory()) {
                occurrenceMeasurement(file, dependencyBlacklist, dependencyTreeOperator);
            }
        }
    }

    /**
     * This method searches for import statements in a source code file, tries to assign them to a project dependency and increments the occurrence
     * counter of the associated dependency
     *
     * @param file                Java source code file
     * @param dependencyBlacklist List of the project dependencies
     */
    private void count(File file, List<ProjectDependency> dependencyBlacklist) {

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
            totalJavaClassesScanned = totalJavaClassesScanned + 1;
            for (String importStatement : importStatements) {
                mapAndCount(dependencyBlacklist, alreadyCountedForThisSource, importStatement, file);
            }
        }
    }

    private void mapAndCount(List<ProjectDependency> dependencyBlacklist, List<ProjectDependency> alreadyCountedForThisSource,
                                    String importStatement, File file) {

        if (importStatement.endsWith("*")) {
            String packageOfImportStatement = importStatement.substring(0, importStatement.length() - 2);

            for (ProjectDependency projectDependency : dependencyBlacklist) {
                if (projectDependency.getAllPossiblePackagesIncludingDependencies().contains(packageOfImportStatement)) {
                    if (!alreadyCountedForThisSource.contains(projectDependency)) {
                        projectDependency.getOccurrenceInProjectClasses().add(generateReason(file, importStatement));
                        alreadyCountedForThisSource.add(projectDependency);
                    }
                }
            }
        } else {
            for (ProjectDependency projectDependency : dependencyBlacklist) {
                if (projectDependency.getAllPossibleClassesIncludingDependencies().contains(importStatement)) {
                    if (!alreadyCountedForThisSource.contains(projectDependency)) {
                        projectDependency.getOccurrenceInProjectClasses().add(generateReason(file, importStatement));
                        alreadyCountedForThisSource.add(projectDependency);
                    } else {
                        projectDependency.setOccurrenceInProjectClasses(enhanceReason(projectDependency.getOccurrenceInProjectClasses(), importStatement));
                    }
                }
            }
        }
    }

    private String generateReason(File file, String importStatement) {

        String filename = file.toString();
        String reason;

        int indexOfLastSlash = filename.lastIndexOf("/");
        if (indexOfLastSlash == -1) {
            int indexOfLastBackslash = filename.lastIndexOf("\\");
            reason = "<strong>" + filename.substring(indexOfLastBackslash + 1) + ":</strong> " + importStatement;
        } else {
            reason = "<strong>" + filename.substring(indexOfLastSlash + 1) + ":</strong> " + importStatement;
        }

        return reason;
    }

    private List<String> enhanceReason(List<String> occurrenceInProjectClasses, String importStatement) {

        List<String> enhancedOccurrenceInProjectClasses = new ArrayList<>();
        occurrenceInProjectClasses.forEach(occurrence -> {
            if (occurrence.contains(importStatement)) {
                enhancedOccurrenceInProjectClasses.add(occurrence);
            } else {
                enhancedOccurrenceInProjectClasses.add(occurrence + ", " + importStatement);
            }
        });
        return enhancedOccurrenceInProjectClasses;
    }

    public Integer getTotalJavaClassesScanned() {
        return totalJavaClassesScanned;
    }

    public List<Artifact> getApplicationStartupLibrariesOfProject() {
        return applicationStartupLibrariesOfProject;
    }
}