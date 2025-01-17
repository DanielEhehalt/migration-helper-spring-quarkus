package com.devonfw.qmaid.operator;

import com.devonfw.qmaid.collector.AnalysisFailureCollector;
import com.devonfw.qmaid.model.AnalysisFailureEntry;
import com.devonfw.qmaid.model.ProjectDependency;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Operator class for analyzing the dependency tree
 */
public class DependencyTreeOperator {

    List<DependencyNode> dependencyTreeRootNodes;
    List<Artifact> allArtifactsOfProject;
    List<ProjectDependency> projectDependencies;

    public DependencyTreeOperator(File projectPomLocation, File mavenRepoLocation,
                                  List<Artifact> applicationStartupLibrariesOfProject) {

        generateDependencyTree(projectPomLocation, mavenRepoLocation, applicationStartupLibrariesOfProject);
        generateArtifactsList(mavenRepoLocation);
        createProjectDependencyObjectsFromArtifacts(allArtifactsOfProject);
    }

    private static final Logger LOG = LoggerFactory.getLogger(DependencyTreeOperator.class);

    /**
     * This method generates the dependency tree of a project
     */
    private void generateDependencyTree(File projectPomLocation, File mavenRepoLocation, List<Artifact> applicationStartupLibrariesOfProject) {

        dependencyTreeRootNodes = new ArrayList<>();

        MavenBridgeImpl mavenBridge = new MavenBridgeImpl(mavenRepoLocation);
        Model model = mavenBridge.readModel(projectPomLocation);

        for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            if (dependency.getScope() != null && dependency.getScope().equals("test")) {
                continue;
            }
            String version = dependency.getVersion();

            if (version == null) {
                Optional<Artifact> localArtifact = applicationStartupLibrariesOfProject.stream().filter(artifact -> artifact.getGroupId()
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
            dependencyTreeRootNodes.add(rootNode);
        }
    }

    /**
     * This method enhances an artifact with all children that have compile or runtime as scope
     *
     * @param artifact          The root dependency for enrichment
     * @param mavenRepoLocation Location of the local maven repository
     * @return DependencyNode enriched with all children which have compile or runtime as scope
     * @throws DependencyCollectionException If maven dependency is not available
     */
    private DependencyNode buildBranchesOfRootNode(Artifact artifact, File mavenRepoLocation)
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
    private RepositorySystem newRepositorySystem() {

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
    private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system,
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
    private List<RemoteRepository> newRepositories() {

        return new ArrayList<>(Collections.singletonList(
                new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()));
    }

    /**
     * This method generates a list of all artifacts of the given nodes
     *
     * @return List of all artifacts
     */
    private void generateArtifactsList(File mavenRepoLocation) {

        allArtifactsOfProject = new ArrayList<>();
        for (DependencyNode rootNode : dependencyTreeRootNodes) {
            allArtifactsOfProject.add(rootNode.getArtifact());
            findArtifactsOfNode(rootNode, mavenRepoLocation, allArtifactsOfProject);
        }
    }

    /**
     * This method generates a list of all artifacts of the given node recursively
     *
     * @param node                  Node to analyze
     * @param mavenRepoLocation     Location of the local maven repository
     * @param allArtifactsOfProject List of all artifacts
     */
    private List<Artifact> findArtifactsOfNode(DependencyNode node, File mavenRepoLocation,
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
    private File tryFindJarInLocalMavenRepo(Artifact artifact, File mavenRepoLocation) throws ArtifactResolutionException {

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
    private Artifact resolveArtifactFromMavenOnlineRepository(Artifact artifact, File mavenRepoLocation) throws ArtifactResolutionException {

        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = newRepositorySystemSession(system, mavenRepoLocation);

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(newRepositories());
        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact();
    }

    /**
     * This method enhances the dependencies with packages and classes
     *
     * @param dependencyBlacklist List of blacklisted dependencies
     */
    public void enhanceDirectDependencyWithPackagesAndClassesFromTransitiveDependencies(List<ProjectDependency> dependencyBlacklist) {

        for (ProjectDependency blacklistEntry : dependencyBlacklist) {

            List<String> allPossiblePackagesOfBlacklistEntry = new ArrayList<>(blacklistEntry.getPackages());
            List<String> allPossibleClassesOfBlacklistEntry = new ArrayList<>(blacklistEntry.getClasses());

            DependencyNode dependencyNode = findDependencyInDependencyTreeRootNodes(dependencyTreeRootNodes, blacklistEntry);
            boolean rootNodeDependency = true;

            if (dependencyNode == null) {
                dependencyNode = findDependencyInDependencyTree(dependencyTreeRootNodes, blacklistEntry);
                rootNodeDependency = false;
            }

            if (dependencyNode != null && rootNodeDependency) {
                collectPackagesAndClassesFromChildren(dependencyNode, allPossiblePackagesOfBlacklistEntry,
                        allPossibleClassesOfBlacklistEntry, projectDependencies);
            }
            blacklistEntry.setAllPossiblePackagesIncludingDependencies(allPossiblePackagesOfBlacklistEntry);
            blacklistEntry.setAllPossibleClassesIncludingDependencies(allPossibleClassesOfBlacklistEntry);
        }
    }

    /**
     * This method collects all packages and classes from the dependencies of a node of the dependency tree
     *
     * @param node                                The given node
     * @param allPossiblePackagesOfBlacklistEntry List to save the found packages
     * @param allPossibleClassesOfBlacklistEntry  List to save the found classes
     * @param projectDependencies                 List with all project dependencies
     */
    private void collectPackagesAndClassesFromChildren(DependencyNode node, List<String> allPossiblePackagesOfBlacklistEntry,
                                                       List<String> allPossibleClassesOfBlacklistEntry,
                                                       List<ProjectDependency> projectDependencies) {

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
        }
    }

    /**
     * This method searches a dependency in the dependency tree root nodes
     *
     * @param dependencyTreeRootNodes Dependency tree
     * @param dependency              The searched dependency
     * @return Found node of the tree
     */
    private DependencyNode findDependencyInDependencyTreeRootNodes(List<DependencyNode> dependencyTreeRootNodes,
                                                                   ProjectDependency dependency) {

        for (DependencyNode dependencyTreeRootNode : dependencyTreeRootNodes) {
            Artifact artifact = dependencyTreeRootNode.getArtifact();
            if (artifact.getGroupId().equals(dependency.getGroupId()) && artifact.getArtifactId().equals(dependency.getArtifactId()) &&
                    artifact.getVersion().equals(dependency.getVersion())) {
                return dependencyTreeRootNode;
            }
        }
        return null;
    }

    /**
     * This method searches a dependency in the dependency tree
     *
     * @param dependencyTreeRootNodes Dependency tree
     * @param dependency              The searched dependency
     * @return Found node of the tree
     */
    private DependencyNode findDependencyInDependencyTree(List<DependencyNode> dependencyTreeRootNodes,
                                                                   ProjectDependency dependency) {

        for (DependencyNode dependencyTreeRootNode : dependencyTreeRootNodes) {
            DependencyNode dependencyNode = checkBranches(dependencyTreeRootNode, dependency);
            if (dependencyNode != null) {
                return dependencyNode;
            }
        }
        return null;
    }

    private DependencyNode checkBranches(DependencyNode node, ProjectDependency dependency) {

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

    /**
     * This method creates a list of ProjectDependency objects based on the found artifacts. The ProjectDependency objects are enriched with the
     * included packages and classes
     */
    private void createProjectDependencyObjectsFromArtifacts(List<Artifact> allArtifactsOfProject) {

        projectDependencies = new ArrayList<>();

        for (Artifact artifact : allArtifactsOfProject) {
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
    }

    public List<DependencyNode> getDependencyTreeRootNodes() {
        return dependencyTreeRootNodes;
    }

    public List<ProjectDependency> getProjectDependencies() {
        return projectDependencies;
    }

    public List<Artifact> getAllArtifactsOfProject() {
        return allArtifactsOfProject;
    }
}
