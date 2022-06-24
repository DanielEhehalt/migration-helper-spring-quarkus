package com.devonfw.application.operator;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import com.devonfw.application.model.ProjectDependency;
import net.sf.mmm.code.java.maven.impl.MavenBridgeImpl;
import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Operator class for analyzing the dependency tree
 */
public class DependencyTreeOperator {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyTreeOperator.class);


    /**
     * This method generates the dependency tree of a project
     *
     * @param allArtifactsOfProject List with all artifacts of the project
     * @param locationOfProjectPom  Location of project POM
     * @param mavenRepoLocation     Location of the local maven repository
     * @return List with one DependencyNode per dependency in the project. Enriched with all children which have the scope compile
     */
    public static List<DependencyNode> generateDependencyTree(List<Artifact> allArtifactsOfProject,
                                                              File locationOfProjectPom, File mavenRepoLocation) {

        List<DependencyNode> rootNodes = new ArrayList<>();

        MavenBridgeImpl mavenBridge = new MavenBridgeImpl(mavenRepoLocation);
        Model model = mavenBridge.readModel(locationOfProjectPom);

        for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            if (dependency.getScope() != null && dependency.getScope().equals("test")) {
                continue;
            }
            String version = dependency.getVersion();

            if (version == null) {
                Optional<Artifact> localArtifact = allArtifactsOfProject.stream().filter(artifact -> artifact.getGroupId()
                        .equals(dependency.getGroupId()) && artifact.getArtifactId()
                        .equals(dependency.getArtifactId())).findFirst();
                if (localArtifact.isPresent()) {
                    version = localArtifact.get().getVersion();
                } else {
                    AnalysisFailureCollector.addAnalysisFailure(
                            new AnalysisFailureEntry(dependency.getGroupId() + ":" + dependency.getArtifactId(),
                                    "Cannot resolve artifact because the version cannot be found out. " +
                                            "The remaining branch of the dependency tree cannot be built further for this dependency."));
                    LOG.debug("Cannot resolve artifact: " +
                            dependency.getGroupId() + ":" + dependency.getArtifactId() +
                            ". The version cannot be found out. The remaining branch of the dependency tree cannot be built further for this dependency.");
                    continue;
                }
            }

            DependencyNode rootNode;
            try {
                if (dependency.getScope() != null && dependency.getScope().equals("test")) {
                    continue;
                }
                rootNode = buildBranchesOfRootNode(
                        new DefaultArtifact(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + version), mavenRepoLocation);
            } catch (DependencyCollectionException | ArtifactResolutionException e) {
                AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(
                        dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + version,
                        "Could not resolve artifact. The remaining branch of the dependency tree cannot be built further for this dependency."));
                LOG.debug("Could not resolve artifact: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + version +
                        ". The remaining branch of the dependency tree cannot be built further for this dependency.", e);
                continue;
            }

            if (rootNode == null) {
                continue;
            }
            rootNodes.add(rootNode);
        }
        return rootNodes;
    }

    /**
     * This method enhances an artifact with all children that have compile or runtime as scope
     *
     * @param artifact          The root dependency for enrichment
     * @param mavenRepoLocation Location of the local maven repository
     * @return DependencyNode enriched with all children which have compile or runtime as scope
     * @throws DependencyCollectionException If maven dependency is not available
     */
    private static DependencyNode buildBranchesOfRootNode(Artifact artifact, File mavenRepoLocation)
            throws DependencyCollectionException, ArtifactResolutionException {

        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = newRepositorySystemSession(system, mavenRepoLocation);

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(newRepositories());
        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifactResult.getArtifact());

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifactResult.getArtifact(), ""));
        collectRequest.setRepositories(newRepositories());

        CollectResult collectResult = system.collectDependencies(session, collectRequest);
        return collectResult.getRoot();
    }

    /**
     * This method initiates the repository system
     *
     * @return repository system
     */
    public static RepositorySystem newRepositorySystem() {

        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                LOG.error("Service creation failed for {} with implementation {}",
                        type, impl, exception);
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    /**
     * This method initiates the repository system session
     *
     * @return repository system session
     */
    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system,
                                                                            File mavenRepoLocation) {

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(mavenRepoLocation.toString());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    /**
     * This method initiates the remote repositories
     *
     * @return List of remote repositories
     */
    public static List<RemoteRepository> newRepositories() {

        return new ArrayList<>(Collections.singletonList(
                new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()));
    }

    /**
     * This method generates a list of all artifacts of the given nodes
     *
     * @param rootNodes         Nodes to analyze
     * @param mavenRepoLocation Location of the local maven repository
     * @return List of all artifacts
     */
    public static List<Artifact> generateArtifactsList(List<DependencyNode> rootNodes, File mavenRepoLocation) {

        List<Artifact> allArtifactsOfProject = new ArrayList<>();
        for (DependencyNode rootNode : rootNodes) {
            allArtifactsOfProject.add(rootNode.getArtifact());
            findArtifactsOfNode(rootNode, mavenRepoLocation, allArtifactsOfProject);
        }
        return allArtifactsOfProject;
    }

    /**
     * This method generates a list of all artifacts of the given node recursively
     *
     * @param node                  Node to analyze
     * @param mavenRepoLocation     Location of the local maven repository
     * @param allArtifactsOfProject List of all artifacts
     */
    private static List<Artifact> findArtifactsOfNode(DependencyNode node, File mavenRepoLocation,
                                                      List<Artifact> allArtifactsOfProject) {

        List<DependencyNode> childrenFromNode = node.getChildren();
        for (DependencyNode child : childrenFromNode) {
            Artifact artifact = child.getArtifact();
            boolean artifactAlreadyInList = allArtifactsOfProject
                    .stream()
                    .anyMatch(listEntry -> listEntry.getGroupId().equals(artifact.getGroupId()) &&
                            listEntry.getArtifactId().equals(artifact.getArtifactId()) &&
                            listEntry.getVersion().equals(artifact.getVersion()));
            if (!artifactAlreadyInList) {
                File file;
                try {
                    file = tryFindJarInLocalMavenRepo(artifact, mavenRepoLocation);
                } catch (ArtifactResolutionException e) {
                    AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(
                            artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(),
                            "Could not find jar file in local maven repository. Reflection analysis of this artifact is not possible."));
                    LOG.debug("Could not find jar file in local maven repository for artifact: " +
                            artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() +
                            ". Reflection analysis of this artifact is not possible.");
                    continue;
                }
                Artifact artifactWithFile = artifact.setFile(file);
                allArtifactsOfProject.add(artifactWithFile);
                findArtifactsOfNode(child, mavenRepoLocation, allArtifactsOfProject);
            }
        }
        return allArtifactsOfProject;
    }

    /**
     * This method tries to find the java archive of the specified artifact in the local Maven repository and in the online repository
     *
     * @param artifact          Artifact to find
     * @param mavenRepoLocation Location of the local maven repository
     * @return The searched jar file or null
     * @throws ArtifactResolutionException Throws Exception when artifact is not resolvable
     */
    public static File tryFindJarInLocalMavenRepo(Artifact artifact, File mavenRepoLocation)
            throws ArtifactResolutionException {

        File groupFolder = new File(mavenRepoLocation, artifact.getGroupId().replace('.', '/'));
        File artifactFolder = new File(groupFolder, artifact.getArtifactId());
        File versionFolder = new File(artifactFolder, artifact.getVersion());
        StringBuilder pomFilename = new StringBuilder(artifact.getArtifactId());
        pomFilename.append('-');
        pomFilename.append(artifact.getVersion());
        String classifier = artifact.getClassifier();
        if (classifier != null && !classifier.equals("")) {
            pomFilename.append("-");
            pomFilename.append(classifier);
        }
        pomFilename.append(".jar");
        File jarFile = new File(versionFolder, pomFilename.toString());

        if (!jarFile.exists()) {
            jarFile = resolveArtifactFromMavenOnlineRepository(artifact, mavenRepoLocation).getFile();
            if (!jarFile.exists()) {
                throw new ArtifactResolutionException(new ArrayList<>());
            }
        }
        return jarFile;
    }

    /**
     * This method tries to resolve the specified artifact in the online maven repository and stores it in the local maven repository
     *
     * @param artifact          Artifact to resolve
     * @param mavenRepoLocation Location of the local maven repository
     * @return The successfully resolved artifact
     * @throws ArtifactResolutionException If the artifact is not resolvable
     */
    private static Artifact resolveArtifactFromMavenOnlineRepository(Artifact artifact, File mavenRepoLocation)
            throws ArtifactResolutionException {

        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = newRepositorySystemSession(system, mavenRepoLocation);

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(newRepositories());
        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact();
    }

    /**
     * This method collects all packages and classes from the dependencies of a node of the dependency tree
     *
     * @param node                                The given node
     * @param allPossiblePackagesOfBlacklistEntry List to save the found packages
     * @param allPossibleClassesOfBlacklistEntry  List to save the found classes
     * @param projectDependencies                 List with all project dependencies
     * @param searchDepth                         Search depth
     */
    public static void collectPackagesAndClassesFromChildren(DependencyNode node, List<String> allPossiblePackagesOfBlacklistEntry,
                                                             List<String> allPossibleClassesOfBlacklistEntry,
                                                             List<ProjectDependency> projectDependencies,
                                                             Integer searchDepth) {

        List<DependencyNode> childrenFromNode = node.getChildren();
        for (DependencyNode child : childrenFromNode) {
            Artifact artifact = child.getArtifact();

            Optional<ProjectDependency> projectDependency = projectDependencies.stream()
                    .filter(dependency -> dependency.getGroupId().equals(artifact.getGroupId()) &&
                            dependency.getArtifactId().equals(artifact.getArtifactId()) &&
                            dependency.getVersion().equals(artifact.getVersion())).findAny();
            if (projectDependency.isPresent()) {
                allPossiblePackagesOfBlacklistEntry.addAll(projectDependency.get().getPackages());
                allPossibleClassesOfBlacklistEntry.addAll(projectDependency.get().getClasses());
            }
            if (searchDepth > 0 || child.getArtifact().getArtifactId().contains("starter")) {
                collectPackagesAndClassesFromChildren(child, allPossiblePackagesOfBlacklistEntry, allPossibleClassesOfBlacklistEntry,
                        projectDependencies, searchDepth - 1);
            }
        }
    }

    /**
     * This method searches a dependency in the dependency tree
     *
     * @param dependencyTreeRootNodes Dependency tree
     * @param dependency              The searched dependency
     * @return Found node of the tree
     */
    public static DependencyNode findDependencyInDependencyTree(List<DependencyNode> dependencyTreeRootNodes,
                                                                ProjectDependency dependency) {

        for (DependencyNode dependencyTreeRootNode : dependencyTreeRootNodes) {
            Artifact artifact = dependencyTreeRootNode.getArtifact();
            if (artifact.getGroupId().equals(dependency.getGroupId()) && artifact.getArtifactId().equals(dependency.getArtifactId()) &&
                    artifact.getVersion().equals(dependency.getVersion())) {
                return dependencyTreeRootNode;
            } else {
                DependencyNode dependencyNode = checkBranches(dependencyTreeRootNode, dependency);
                if (dependencyNode != null) {
                    return dependencyNode;
                }
            }
        }
        return null;
    }

    private static DependencyNode checkBranches(DependencyNode node, ProjectDependency dependency) {

        List<DependencyNode> childrenFromNode = node.getChildren();
        for (DependencyNode child : childrenFromNode) {
            Artifact artifact = child.getArtifact();
            if (artifact.getGroupId().equals(dependency.getGroupId()) && artifact.getArtifactId().equals(dependency.getArtifactId()) &&
                    artifact.getVersion().equals(dependency.getVersion())) {
                return child;
            }
            checkBranches(child, dependency);
        }
        return null;
    }
}
