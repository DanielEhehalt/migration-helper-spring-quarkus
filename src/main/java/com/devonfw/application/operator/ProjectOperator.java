package com.devonfw.application.operator;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import com.devonfw.application.model.ProjectDependency;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaSource;
import net.sf.mmm.code.impl.java.JavaContext;
import net.sf.mmm.code.impl.java.source.maven.JavaSourceProviderUsingMaven;
import net.sf.mmm.code.impl.java.source.maven.MavenDependencyCollector;
import net.sf.mmm.code.java.maven.impl.MavenBridgeImpl;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Path;
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
        JavaContext context = JavaSourceProviderUsingMaven.createFromLocalMavenProject(inputProjectLocation, dependencyCollector);
        String fqnOfClass = getFQN(applicationEntryPointLocation.toPath());
        try {
            context.getClassLoader().loadClass(fqnOfClass);
        } catch (ClassNotFoundException e) {
            AnalysisFailureCollector.addAnalysisFailure(
                    new AnalysisFailureEntry(fqnOfClass, "Could not load class. ClassNotFoundException was thrown."));
            LOG.debug("Could not find class", e);
        }

        applicationStartupLibrariesOfProject = new ArrayList<>();
        URL[] urls = dependencyCollector.asUrls();
        for (URL url : urls) {
            String urlWithoutType = url.toString().substring(6);
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
     * This method is traversing parent folders until it reaches java folder in order to get the FQN
     *
     * @param inputFile Java input file to retrieve FQN (Full Qualified Name)
     * @return qualified name with full package
     */
    private String getFQN(Path inputFile) {

        String simpleName = inputFile.getFileName().toString().replaceAll("\\.(?i)java", "");
        String packageName = getPackageName(inputFile.getParent(), "");

        return packageName + "." + simpleName;
    }

    /**
     * This method traverse the folder in reverse order from child to parent
     *
     * @param folder      parent input file
     * @param packageName the package name
     * @return package name
     */
    private String getPackageName(Path folder, String packageName) {

        if (folder == null) {
            return null;
        }

        if (folder.getFileName().toString().toLowerCase().equals("java")) {
            String[] pkgs = packageName.split("\\.");

            packageName = pkgs[pkgs.length - 1];
            // Reverse order as we have traversed folders from child to parent
            for (int i = pkgs.length - 2; i > 0; i--) {
                packageName = packageName + "." + pkgs[i];
            }
            return packageName;
        }
        return getPackageName(folder.getParent(), packageName + "." + folder.getFileName().toString());
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

        dependencyTreeOperator.enhanceProjectDependencyWithPackagesAndClasses(dependencyBlacklist);
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
                mapAndCount(dependencyBlacklist, alreadyCountedForThisSource, importStatement);
            }
        }
    }

    private void mapAndCount(List<ProjectDependency> dependencyBlacklist, List<ProjectDependency> alreadyCountedForThisSource,
                                    String importStatement) {

        if (importStatement.endsWith("*")) {
            String packageOfImportStatement = importStatement.substring(0, importStatement.length() - 2);

            for (ProjectDependency projectDependency : dependencyBlacklist) {
                if (projectDependency.getAllPossiblePackagesIncludingDependencies().contains(packageOfImportStatement)) {
                    if (!alreadyCountedForThisSource.contains(projectDependency)) {
                        projectDependency.incrementOccurrenceInProjectClasses();
                        alreadyCountedForThisSource.add(projectDependency);
                    }
                }
            }
        } else {
            for (ProjectDependency projectDependency : dependencyBlacklist) {
                if (projectDependency.getAllPossibleClassesIncludingDependencies().contains(importStatement)) {
                    if (!alreadyCountedForThisSource.contains(projectDependency)) {
                        projectDependency.incrementOccurrenceInProjectClasses();
                        alreadyCountedForThisSource.add(projectDependency);
                    }
                }
            }
        }
    }

    public Integer getTotalJavaClassesScanned() {
        return totalJavaClassesScanned;
    }

    public List<Artifact> getApplicationStartupLibrariesOfProject() {
        return applicationStartupLibrariesOfProject;
    }
}