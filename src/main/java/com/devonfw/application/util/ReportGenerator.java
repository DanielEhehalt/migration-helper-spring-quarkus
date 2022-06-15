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
        context.put("dependencyTree", printTree(dependencyNodes, reflectionUsageInDependencies).iterator());
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

    private static List<String> printTree(List<DependencyNode> rootNodes, List<ReflectionUsageInDependencies> reflectionUsageInDependencies) {

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
            buildTreeAsString(node, reflectionUsageInDependencies, stringBuilder, 1, alwaysAppended);
            branches.add(stringBuilder.toString());
        });

        return branches;
    }

    private static void buildTreeAsString(DependencyNode node, List<ReflectionUsageInDependencies> reflectionUsageInDependencies, StringBuilder stringBuilder, Integer level, List<String> alwaysAppended) {

        for (DependencyNode child : node.getChildren()) {
            Artifact artifact = child.getArtifact();
            String artifactAsString = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsageInDependencies.stream().filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(artifactAsString)).findAny();
            stringBuilder.append("<br>");
            stringBuilder.append("&emsp;".repeat(Math.max(0, level)));

            if (optionalReflectionUsageInDependency.isEmpty()) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getVersion()).append(" (0)");
                alwaysAppended.add(artifactAsString);
            } else if (alwaysAppended.contains(artifactAsString)) {
                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getVersion()).append(" (").append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")").append("*");
                continue;
            } else {
                stringBuilder.append(artifactAsString).append(" (").append(optionalReflectionUsageInDependency.get().getClasses().size()).append(")");
                alwaysAppended.add(artifactAsString);
            }
            buildTreeAsString(child, reflectionUsageInDependencies, stringBuilder, level + 1, alwaysAppended);
        }
    }

//    private static void buildTreeAsString(DependencyNode node, List<ReflectionUsageInDependencies> reflectionUsageInDependencies, StringBuilder stringBuilder, Integer level, List<String> alwaysAppended) {
//
//        for (DependencyNode child : node.getChildren()) {
//            Artifact artifact = child.getArtifact();
//            String artifactAsString = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
//            Optional<Artifact> optionalArtifact = allArtifactsOfProjectAndTheirReflectionUse.keySet().stream().filter(key -> key.getGroupId().equals(artifact.getGroupId()) && key.getArtifactId().equals(artifact.getArtifactId()) && key.getVersion().equals(artifact.getVersion())).findFirst();
//            stringBuilder.append("<br>");
//            stringBuilder.append("&emsp;".repeat(Math.max(0, level)));
//
//            if (optionalArtifact.isEmpty()) {
//                stringBuilder.append("*").append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getVersion()).append(" (x)");
//                alwaysAppended.add(artifactAsString);
//            } else if (alwaysAppended.contains(artifactAsString)) {
//                Artifact artifactKey = optionalArtifact.get();
//                stringBuilder.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getVersion()).append(" (").append(allArtifactsOfProjectAndTheirReflectionUse.get(artifactKey)).append(")").append("*");
//                continue;
//            } else {
//                Artifact artifactKey = optionalArtifact.get();
//                stringBuilder.append(artifactAsString).append(" (").append(allArtifactsOfProjectAndTheirReflectionUse.get(artifactKey)).append(")");
//                alwaysAppended.add(artifactAsString);
//            }
//            buildTreeAsString(child, allArtifactsOfProjectAndTheirReflectionUse, stringBuilder, level + 1, alwaysAppended);
//        }
//    }
}
