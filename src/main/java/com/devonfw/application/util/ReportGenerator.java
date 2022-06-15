package com.devonfw.application.util;

import com.devonfw.application.analyzer.PomAnalyzer;
import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.model.ReflectionUsageInDependencies;
import com.devonfw.application.model.ReflectionUsageInProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * Generates HTML report
 */
public class ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ReportGenerator.class);

    /**
     * Generates HTML report
     *
     * @param blacklist                     Blacklisted Dependencies
     * @param reflectionUsageInProject      List of reflection usage in the project
     * @param reflectionUsageInDependencies List of reflection usage in the project dependencies
     * @param dependencyNodes               List with the root nodes of the dependency tree
     * @param locationOfPom                 Location of project POM
     * @param resultPath                    Path to the directory where the report will be saved
     */
    public static void generateReport(List<BlacklistEntry> blacklist,
                                      List<ReflectionUsageInProject> reflectionUsageInProject,
                                      List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
                                      List<DependencyNode> dependencyNodes,
                                      String locationOfPom,
                                      String resultPath) {

        LOG.info("Generate report.html");

        //Configuration for template location under src/main/resources
        Properties properties = new Properties();
        properties.setProperty("resource.loaders", "class");
        properties.setProperty("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        //Init Velocity
        Velocity.init(properties);
        VelocityContext context = new VelocityContext();
        Template template = Velocity.getTemplate("report-template.vm");

        //Insert dynamic values
        context.put("projectName", PomAnalyzer.getNameAndVersionFromProject(locationOfPom));
        context.put("javaVersion", PomAnalyzer.getJavaVersionFromProject(locationOfPom));
        context.put("blacklist", blacklist.iterator());
        context.put("blacklistSize", blacklist.size());
        context.put("reflectionUsageInProjectList", reflectionUsageInProject.iterator());
        context.put("reflectionUsageInProjectListSize", reflectionUsageInProject.size());
        context.put("dependencyTree", printDependencyTree(dependencyNodes, reflectionUsageInDependencies).iterator());
        context.put("reflectionUsageInDependenciesListSize", reflectionUsageInDependencies.size());
        context.put("analysisFailuresList", AnalysisFailureCollector.getAnalysisFailures());
        context.put("analysisFailuresListSize", AnalysisFailureCollector.getAnalysisFailures().size());

        //Merge template
        StringWriter sw = new StringWriter();
        template.merge(context, sw);

        //Generate html
        try {
            FileWriter fw = new FileWriter(resultPath + "/report.html");
            fw.write(sw.toString());
            fw.close();
        } catch (IOException e) {
            LOG.error("Could not generate report.", e);
        }
    }

    /**
     * Builds the dependency tree as a string and extends it with the found reflection usages
     *
     * @param rootNodes                     Root nodes of the dependency tree
     * @param reflectionUsageInDependencies List with detected reflection usage in dependencies
     * @return Dependency tree root nodes
     */
    private static List<String> printDependencyTree(List<DependencyNode> rootNodes, List<ReflectionUsageInDependencies> reflectionUsageInDependencies) {

        List<String> branches = new ArrayList<>();
        List<String> alwaysAppended = new ArrayList<>();

        rootNodes.forEach(node -> {
            StringBuilder stringBuilder = new StringBuilder();
            Artifact artifact = node.getArtifact();
            String artifactAsString = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsageInDependencies.stream().filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(artifactAsString)).findAny();
            stringBuilder.append("<br>");
            if (optionalReflectionUsageInDependency.isEmpty()) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getVersion()).append(" (0)");
                alwaysAppended.add(artifactAsString);
            } else {
                stringBuilder.append(artifactAsString).append(" (").append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")");
                alwaysAppended.add(artifactAsString);
            }
            buildBranchAsString(node, reflectionUsageInDependencies, stringBuilder, 1, alwaysAppended);
            branches.add(stringBuilder.toString());
        });

        return branches;
    }

    /**
     * Builds a branch of the dependency tree as a string and extends it with the found reflection usages recursively. Libraries that appear more than once are displayed shortened
     *
     * @param node                          Start node to build the branch
     * @param reflectionUsageInDependencies List with detected reflection usage in dependencies
     * @param stringBuilder                 Collects result
     * @param level                         Level for setting the text indent
     * @param alreadyAppended               List with already attached dependencies
     */
    private static void buildBranchAsString(DependencyNode node, List<ReflectionUsageInDependencies> reflectionUsageInDependencies, StringBuilder stringBuilder, Integer level, List<String> alreadyAppended) {

        for (DependencyNode child : node.getChildren()) {
            Artifact artifact = child.getArtifact();
            String artifactAsString = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsageInDependencies.stream().filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(artifactAsString)).findAny();
            stringBuilder.append("<br>");
            stringBuilder.append("&emsp;".repeat(Math.max(0, level)));

            if (optionalReflectionUsageInDependency.isEmpty()) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getVersion()).append(" (0)");
                alreadyAppended.add(artifactAsString);
            } else if (alreadyAppended.contains(artifactAsString)) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getVersion()).append(" (").append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")").append("*");
                continue;
            } else {
                stringBuilder.append(artifactAsString).append(" (").append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")");
                alreadyAppended.add(artifactAsString);
            }
            buildBranchAsString(child, reflectionUsageInDependencies, stringBuilder, level + 1, alreadyAppended);
        }
    }
}