package com.devonfw.application.collector;

import com.devonfw.application.model.MtaIssue;
import com.devonfw.application.model.ProjectDependency;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generates the dependency blacklist
 */
public class DependencyBlacklistCollector {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyBlacklistCollector.class);

    List<ProjectDependency> dependencyBlacklist;

    public DependencyBlacklistCollector(List<MtaIssue> mtaIssuesList, List<ProjectDependency> projectDependencies,
                                        List<DependencyNode> dependencyTreeRootNodes) {

        generateDependencyBlacklistFromMtaIssuesList(mtaIssuesList, projectDependencies, dependencyTreeRootNodes);
    }

    /**
     * This method generates the dependency blacklist from the found MTA issues
     *
     * @param mtaIssuesList           Found issues of MTA
     * @param projectDependencies  List with all project dependencies
     * @param dependencyTreeRootNodes Dependency tree
     */
    private void generateDependencyBlacklistFromMtaIssuesList(List<MtaIssue> mtaIssuesList, List<ProjectDependency> projectDependencies,
                                                                                       List<DependencyNode> dependencyTreeRootNodes) {

        List<ProjectDependency> rootDependencies = new ArrayList<>();
        dependencyTreeRootNodes.forEach(node -> {
            Optional<ProjectDependency> optionalProjectDependency = projectDependencies.stream()
                    .filter(dependency -> node.getArtifact().getGroupId().equals(dependency.getGroupId()) &&
                            node.getArtifact().getArtifactId().equals(dependency.getArtifactId()) &&
                            node.getArtifact().getVersion().equals(dependency.getVersion())).findAny();
            optionalProjectDependency.ifPresent(rootDependencies::add);
        });

        dependencyBlacklist = new ArrayList<>();

        for (MtaIssue mtaIssue : mtaIssuesList) {
            boolean mtaIssueIsGeneralIssue = true;

            for (MtaIssue.MavenIdentifier mavenIdentifier : mtaIssue.getMavenIdentifiers()) {
                Optional<ProjectDependency> optionalProjectDependency = rootDependencies.stream()
                        .filter(projectDependency -> projectDependency.getGroupId().equals(mavenIdentifier.getGroupId()) &&
                                projectDependency.getArtifactId().equals(mavenIdentifier.getArtifactId())).findFirst();
                mtaIssueIsGeneralIssue = enhanceBlacklistIfPossible(dependencyBlacklist, mtaIssue, mtaIssueIsGeneralIssue, optionalProjectDependency);
            }
            for (String javaPackage : mtaIssue.getPackages()) {
                Optional<ProjectDependency> optionalProjectDependency = projectDependencies.stream()
                        .filter(projectDependency -> projectDependency.getClasses().contains(javaPackage) ||
                                projectDependency.getPackages().contains(javaPackage)).findFirst();
                mtaIssueIsGeneralIssue = enhanceBlacklistIfPossible(dependencyBlacklist, mtaIssue, mtaIssueIsGeneralIssue, optionalProjectDependency);
            }
            if (mtaIssueIsGeneralIssue) {
                mtaIssue.setGeneralIssue(true);
            }
        }
    }

    private boolean enhanceBlacklistIfPossible(List<ProjectDependency> dependencyBlacklist, MtaIssue mtaIssue, boolean mtaIssueIsGeneralIssue,
                                                      Optional<ProjectDependency> optionalProjectDependency) {


        if (optionalProjectDependency.isPresent()) {
            ProjectDependency projectDependency = optionalProjectDependency.get();
            projectDependency.setBlacklisted(true);
            projectDependency.setDescriptionIfBlacklisted(mtaIssue.getDescription());
            if (!dependencyBlacklist.contains(projectDependency)) {
                dependencyBlacklist.add(projectDependency);
            }
            mtaIssueIsGeneralIssue = false;
        }
        return mtaIssueIsGeneralIssue;
    }

    public List<ProjectDependency> getDependencyBlacklist() {
        return dependencyBlacklist;
    }
}
