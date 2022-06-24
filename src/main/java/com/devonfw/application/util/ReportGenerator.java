package com.devonfw.application.util;

import com.devonfw.application.operator.PomOperator;
import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.MtaIssue;
import com.devonfw.application.model.ProjectDependency;
import com.devonfw.application.model.ReflectionUsageInDependencies;
import com.devonfw.application.model.ReflectionUsageInProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates HTML report
 */
public class ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ReportGenerator.class);

    /**
     * This method generates the HTML report
     *
     * @param dependencyBlacklist                  Blacklisted Dependencies
     * @param totalJavaClassesScanned              Total classes scanned by the occurrence measurement
     * @param reflectionUsageInProject             List of reflection usage in the project
     * @param mtaIssuesList                        Found issues of the MTA analysis
     * @param reflectionUsageInDependencies        List of reflection usage in the project dependencies
     * @param dependencyNodes                      List with the root nodes of the dependency tree
     * @param projectPomLocation                   Location of project POM
     * @param resultFolderLocation                 Path to the directory where the report will be saved
     * @param withoutReflectionUsageOfDependencies CLI option
     */
    public static void generateReport(List<ProjectDependency> dependencyBlacklist,
                                      Integer totalJavaClassesScanned,
                                      List<MtaIssue> mtaIssuesList,
                                      List<ReflectionUsageInProject> reflectionUsageInProject,
                                      List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
                                      List<DependencyNode> dependencyNodes,
                                      String projectPomLocation,
                                      String resultFolderLocation,
                                      Boolean withoutReflectionUsageOfDependencies) {

        LOG.info("Generate report.html");

        //Configuration for template location under src/main/resources
        Properties properties = new Properties();
        properties.setProperty("resource.loaders", "class");
        properties.setProperty("resource.loader.class.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        //Init Velocity
        Velocity.init(properties);
        VelocityContext context = new VelocityContext();
        Template template = Velocity.getTemplate("report-template.vm");

        //Insert dynamic values
        context.put("projectName", PomOperator.getProjectIdentifierFromPomFile(projectPomLocation));
        context.put("javaVersion", PomOperator.getJavaVersionFromPomFile(projectPomLocation));
        List<MtaIssue> generalIssues = mtaIssuesList.stream().filter(MtaIssue::getGeneralIssue).collect(Collectors.toUnmodifiableList());
        context.put("generalIssues", generalIssues.iterator());
        context.put("generalIssuesListSize", generalIssues.size());
        context.put("dependencyBlacklist", dependencyBlacklist.iterator());
        context.put("dependencyBlacklistSize", dependencyBlacklist.size());
        context.put("totalJavaClassesScanned", totalJavaClassesScanned);
        context.put("reflectionUsageInProjectList", reflectionUsageInProject.iterator());
        context.put("reflectionUsageInProjectListSize", reflectionUsageInProject.size());
        context.put("withoutReflectionUsageOfDependencies", withoutReflectionUsageOfDependencies);
        context.put("dependencyTree", printDependencyTree(dependencyNodes, reflectionUsageInDependencies).iterator());
        context.put("reflectionUsageInDependenciesListSize", reflectionUsageInDependencies.size());
        context.put("analysisFailuresList", AnalysisFailureCollector.getAnalysisFailures());
        context.put("analysisFailuresListSize", AnalysisFailureCollector.getAnalysisFailures().size());

        //Merge template
        StringWriter sw = new StringWriter();
        template.merge(context, sw);

        //Generate html
        try {
            FileWriter fw = new FileWriter(resultFolderLocation + File.separator + "report.html");
            fw.write(sw.toString());
            fw.close();
        } catch (IOException e) {
            LOG.error("Could not generate report.", e);
        }
    }

    /**
     * This method builds the dependency tree as a string and extends it with the found reflection usages
     *
     * @param rootNodes                     Root nodes of the dependency tree
     * @param reflectionUsageInDependencies List with detected reflection usage in dependencies
     * @return Dependency tree root nodes
     */
    private static List<String> printDependencyTree(List<DependencyNode> rootNodes,
                                                    List<ReflectionUsageInDependencies> reflectionUsageInDependencies) {

        List<String> branches = new ArrayList<>();
        List<String> alwaysAppended = new ArrayList<>();

        rootNodes.forEach(node -> {
            StringBuilder stringBuilder = new StringBuilder();
            Artifact artifact = node.getArtifact();
            String artifactAsString = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsageInDependencies.stream()
                    .filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(artifactAsString)).findAny();
            stringBuilder.append("<br>");
            if (optionalReflectionUsageInDependency.isEmpty()) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":")
                        .append(artifact.getVersion()).append(" (0)");
                alwaysAppended.add(artifactAsString);
            } else {
                stringBuilder.append(artifactAsString).append(" (")
                        .append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")");
                alwaysAppended.add(artifactAsString);
            }
            buildBranchAsString(node, reflectionUsageInDependencies, stringBuilder, 1, alwaysAppended);
            branches.add(stringBuilder.toString());
        });

        return branches;
    }

    /**
     * This method builds a branch of the dependency tree as a string and extends it with the found reflection usages recursively. Libraries that
     * appear more than once are displayed shortened
     *
     * @param node                          Start node to build the branch
     * @param reflectionUsageInDependencies List with detected reflection usage in dependencies
     * @param stringBuilder                 Collects result
     * @param level                         Level for setting the text indent
     * @param alreadyAppended               List with already attached dependencies
     */
    private static void buildBranchAsString(DependencyNode node, List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
                                            StringBuilder stringBuilder, Integer level, List<String> alreadyAppended) {

        for (DependencyNode child : node.getChildren()) {
            Artifact artifact = child.getArtifact();
            String artifactAsString = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsageInDependencies.stream()
                    .filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(artifactAsString)).findAny();
            stringBuilder.append("<br>");
            stringBuilder.append("|&emsp;".repeat(Math.max(0, level)));

            if (optionalReflectionUsageInDependency.isEmpty()) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":")
                        .append(artifact.getVersion()).append(" (0)");
                alreadyAppended.add(artifactAsString);
            } else if (alreadyAppended.contains(artifactAsString)) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":")
                        .append(artifact.getVersion()).append(" (")
                        .append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")").append("*");
                continue;
            } else {
                stringBuilder.append(artifactAsString).append(" (")
                        .append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")");
                alreadyAppended.add(artifactAsString);
            }
            buildBranchAsString(child, reflectionUsageInDependencies, stringBuilder, level + 1, alreadyAppended);
        }
    }
}
