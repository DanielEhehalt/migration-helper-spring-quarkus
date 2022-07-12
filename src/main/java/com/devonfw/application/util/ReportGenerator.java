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

    public ReportGenerator(List<ProjectDependency> dependencyBlacklist, Integer totalJavaClassesScanned, List<MtaIssue> mtaIssuesList,
                           List<ReflectionUsageInProject> reflectionUsageInProject, List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
                           List<DependencyNode> dependencyTreeRootNodes, File projectPomLocation, File resultFolderLocation,
                           Boolean withoutReflectionUsageOfDependencies) {

        generateReport(dependencyBlacklist, totalJavaClassesScanned, mtaIssuesList, reflectionUsageInProject, reflectionUsageInDependencies,
                dependencyTreeRootNodes, projectPomLocation, resultFolderLocation, withoutReflectionUsageOfDependencies);
    }

    /**
     * This method generates the HTML report
     *
     * @param dependencyBlacklist           Blacklisted Dependencies
     * @param totalJavaClassesScanned       Total classes scanned by the occurrence measurement
     * @param reflectionUsageInProject      List of reflection usage in the project
     * @param mtaIssuesList                 Found issues of the MTA analysis
     * @param reflectionUsageInDependencies List of reflection usage in the project dependencies
     * @param dependencyTreeRootNodes       List with the root nodes of the dependency tree
     * @param projectPomLocation            Location of project POM
     * @param resultFolderLocation          Path to the directory where the report will be saved
     * @param withoutDependencyAnalysis     CLI option
     */
    private void generateReport(List<ProjectDependency> dependencyBlacklist,
                                Integer totalJavaClassesScanned,
                                List<MtaIssue> mtaIssuesList,
                                List<ReflectionUsageInProject> reflectionUsageInProject,
                                List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
                                List<DependencyNode> dependencyTreeRootNodes,
                                File projectPomLocation,
                                File resultFolderLocation,
                                Boolean withoutDependencyAnalysis) {

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
        PomOperator pomOperator = new PomOperator();

        //Insert dynamic values
        context.put("projectName", pomOperator.getProjectIdentifierFromPomFile(projectPomLocation));
        context.put("javaVersion", pomOperator.getJavaVersionFromPomFile(projectPomLocation));

        context.put("descriptionOfGeneralIssues", getDescriptionOfGeneralIssues());
        List<MtaIssue> generalIssues = mtaIssuesList.stream().filter(MtaIssue::getGeneralIssue).collect(Collectors.toUnmodifiableList());
        context.put("generalIssues", generalIssues.iterator());
        context.put("generalIssuesListSize", generalIssues.size());

        context.put("descriptionOfBlacklistedDependencies", getDescriptionOfBlacklistedDependencies());
        context.put("descriptionOfOccurrenceMeasurement", getDescriptionOfOccurrenceMeasurement());
        context.put("dependencyBlacklist", dependencyBlacklist.iterator());
        context.put("dependencyBlacklistSize", dependencyBlacklist.size());
        context.put("totalJavaClassesScanned", totalJavaClassesScanned);

        context.put("descriptionOfReflectionUsageInProject", getDescriptionOfReflectionUsageInProject());
        context.put("reflectionUsageInProjectList", reflectionUsageInProject.iterator());
        context.put("reflectionUsageInProjectListSize", reflectionUsageInProject.size());

        context.put("descriptionOfReflectionUsageInDependencies", getDescriptionOfReflectionUsageInDependencies());
        context.put("withoutDependencyAnalysis", withoutDependencyAnalysis);
        context.put("dependencyTree", printDependencyTree(dependencyTreeRootNodes, reflectionUsageInDependencies).iterator());
        context.put("reflectionUsageInDependenciesListSize", reflectionUsageInDependencies.size());

        context.put("descriptionOfAnalysisFailures", getDescriptionOfAnalysisFailures());
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
    private List<String> printDependencyTree(List<DependencyNode> rootNodes,
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
                stringBuilder.append(artifactAsString).append(" (0)");
                alwaysAppended.add(artifactAsString);
            } else {
                List<String> classes = optionalReflectionUsageInDependency.get().getClasses();
                stringBuilder.append(artifactAsString).append(" (").append(classes.size()).append(")")
                        .append(appendToggleableReflectionClasses(artifactAsString, classes));
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
    private void buildBranchAsString(DependencyNode node, List<ReflectionUsageInDependencies> reflectionUsageInDependencies,
                                     StringBuilder stringBuilder, Integer level, List<String> alreadyAppended) {

        for (DependencyNode child : node.getChildren()) {
            Artifact artifact = child.getArtifact();
            String artifactAsString = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsageInDependencies.stream()
                    .filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(artifactAsString)).findAny();
            stringBuilder.append("<br>");
            stringBuilder.append("|&emsp;".repeat(Math.max(0, level)));

            if (optionalReflectionUsageInDependency.isEmpty()) {
                stringBuilder.append(artifactAsString).append(" (0)");
                alreadyAppended.add(artifactAsString);
            } else if (alreadyAppended.contains(artifactAsString)) {
                List<String> classes = optionalReflectionUsageInDependency.get().getClasses();
                stringBuilder.append(artifactAsString).append(" (").append(classes.size()).append(")").append("*");
                continue;
            } else {
                List<String> classes = optionalReflectionUsageInDependency.get().getClasses();
                stringBuilder.append(artifactAsString).append(" (").append(classes.size()).append(")")
                        .append(appendToggleableReflectionClasses(artifactAsString, classes));
                alreadyAppended.add(artifactAsString);
            }
            buildBranchAsString(child, reflectionUsageInDependencies, stringBuilder, level + 1, alreadyAppended);
        }
    }

    private String appendToggleableReflectionClasses(String artifact, List<String> classes) {

        Collections.sort(classes);
        return " <button onclick=\"toggleDisplay('" + artifact + "')\">+</button>" +
                "<div style=\"display:none\" id=\"" + artifact + "\"><br>" + String.join(", ", classes) + "</div>";
    }

    private String getDescriptionOfGeneralIssues() {

        return "The issues listed below are general issues and are not assignable to a specific dependency. " +
                "The rule-based analysis also detects configuration issues or parent POMs. " +
                "In these cases, only the description of the triggered rule is displayed";
    }

    private String getDescriptionOfBlacklistedDependencies() {

        return "The blacklisted dependencies list contains all Maven dependencies that have been identified as incompatible. " +
                "The incompatibility is based on the Quarkus ruleset of the Migration Toolkit for Applications and on extended user-defined rules, e.g. for devon4j. " +
                "Based on the dependencies of the project, transitive dependencies are also analyzed. " +
                "Since dependencies are only recognized if rules exist for them, this list may be incomplete.";
    }

    private String getDescriptionOfOccurrenceMeasurement() {

        return "The occurrence measurement indicates the strength of the bonding between the dependency and the code. " +
                "For this purpose, all import statements of all Java classes of the project are analyzed. " +
                "For the assignment all possible packages and classes of a dependency are collected. " +
                "If a dependency has a transitive dependency with a name that contains \"starter\", the packages and classes contained in this dependency are also assigned." +
                "The number of all scanned Java classes will be displayed for a better estimation";
    }

    private String getDescriptionOfReflectionUsageInProject() {

        return "For determining the reflection usage in the code of the project, all import statements are checked for the occurrence of the java.lang.reflect package. " +
                "If a class is found that imports the reflection API, it is displayed below.";
    }

    private String getDescriptionOfReflectionUsageInDependencies() {

        return "For determining the reflection usage in the dependencies, all import statements of a dependency are checked for the occurrence of the java.lang.reflect package. " +
                "The number of classes that imports the reflection API are shown in the brackets. The names of the identified classes can be displayed using the button next to it. " +
                "To shorten the tree, duplicate entries are marked with an asterisk and are not further executed.";
    }

    private String getDescriptionOfAnalysisFailures() {

        return "To ensure the stability of the execution of the analysis, exceptions are just collected and displayed below. " +
                "For a more detailed failure analysis the use of the --verbose argument is recommended.";
    }
}
