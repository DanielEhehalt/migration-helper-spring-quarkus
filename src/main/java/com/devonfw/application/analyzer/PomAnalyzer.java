package com.devonfw.application.analyzer;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import net.sf.mmm.code.impl.java.JavaContext;
import net.sf.mmm.code.impl.java.source.maven.JavaSourceProviderUsingMaven;
import net.sf.mmm.code.impl.java.source.maven.MavenDependencyCollector;
import net.sf.mmm.code.java.maven.impl.MavenBridgeImpl;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
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

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

/**
 * Utilities for analyzing the maven project object model
 */
public class PomAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(PomAnalyzer.class);

    /**
     * Collects java version from a maven project
     *
     * @param locationOfProjectPom Location of project POM
     * @return Java version
     */
    public static String getJavaVersionFromProject(String locationOfProjectPom) {

        String javaVersion = "undefined";
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader(locationOfProjectPom));
            String javaVersionProperty = model.getProperties().getProperty("java.version");
            if (javaVersionProperty != null) {
                javaVersion = javaVersionProperty;
            }
        } catch (IOException | XmlPullParserException e) {
            LOG.error("Could not find java version in pom.xml under project property java.version", e);
        }
        return javaVersion;
    }

    /**
     * Collects name and version from a maven project
     *
     * @param locationOfProjectPom Location of project POM
     * @return Name and version as String
     */
    public static String getNameAndVersionFromProject(String locationOfProjectPom) {

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader(locationOfProjectPom));
            return model.getName() + "-" + model.getVersion();
        } catch (IOException | XmlPullParserException e) {
            LOG.error("Could not collect name and version of project", e);
        }
        return "Project name not available";
    }

    public static void getParentVersions(String locationOfProjectPom) {

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader(locationOfProjectPom));
            System.out.println(model.getParent().getGroupId() + model.getParent().getVersion());

            for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
                System.out.println(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion() + ":" + dependency.getType());
                if (dependency.getType().equals("pom")) {
                    System.out.println(dependency.getArtifactId());
                }
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Generates the dependency tree
     *
     * @param allArtifactsOfProject List with all artifacts of the project
     * @param locationOfProjectPom Location of project POM
     * @param mavenRepoLocation    Location of the local maven repository
     * @return List with one DependencyNode per dependency in the project. Enriched with all children which have the scope compile
     */
    public static List<DependencyNode> generateDependencyTree(List<Artifact> allArtifactsOfProject, File locationOfProjectPom, File mavenRepoLocation) {

        List<DependencyNode> rootNodes = new ArrayList<>();

        MavenBridgeImpl mavenBridge = new MavenBridgeImpl(mavenRepoLocation);
        Model model = mavenBridge.readModel(locationOfProjectPom);

        for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            String version = dependency.getVersion();

            if (version == null) {
                Optional<Artifact> localArtifact = allArtifactsOfProject.stream().filter(artifact -> artifact.getGroupId().equals(dependency.getGroupId()) && artifact.getArtifactId().equals(dependency.getArtifactId())).findFirst();
                if (localArtifact.isPresent()) {
                    version = localArtifact.get().getVersion();
                } else {
                    AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(dependency.getGroupId() + ":" + dependency.getArtifactId(), "Cannot resolve artifact because the version cannot be found out. The remaining branch of the dependency tree cannot be built further for this dependency."));
                    LOG.debug("Cannot resolve artifact: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ". The version cannot be found out. The remaining branch of the dependency tree cannot be built further for this dependency.");
                    continue;
                }
            }
            DependencyNode rootNode;
            try {
                if (dependency.getScope() != null && dependency.getScope().equals("test")) {
                    continue;
                }
                rootNode = buildBranchesOfRootNode(new DefaultArtifact(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + version), mavenRepoLocation);
            } catch (DependencyCollectionException | ArtifactResolutionException e) {
                AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + version, "Could not resolve artifact. The remaining branch of the dependency tree cannot be built further for this dependency."));
                LOG.debug("Could not resolve artifact: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + version + ". The remaining branch of the dependency tree cannot be built further for this dependency.", e);
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
     * Enrichment with all children that have compile or runtime as scope
     *
     * @param artifact          The root dependency for enrichment
     * @param mavenRepoLocation Location of the local maven repository
     * @return DependencyNode enriched with all children which have compile or runtime as scope
     * @throws DependencyCollectionException If maven dependency is not available
     */
    private static DependencyNode buildBranchesOfRootNode(Artifact artifact, File mavenRepoLocation) throws DependencyCollectionException, ArtifactResolutionException {

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
     * Creates a java context based on the @SpringBootApplication class and returns the loaded dependencies
     *
     * @param springBootApp Path of the @SpringBootApplication class
     * @param inputProject  Location of the project
     * @param mavenRepo     Location of the local maven repository
     * @return List with dependencies
     */
    public static List<Artifact> collectAllApplicationStartupLibrariesOfProject(Path springBootApp, File inputProject, File mavenRepo) {

        List<Artifact> applicationStartupLibrariesOfProject = new ArrayList<>();

        MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(new MavenBridgeImpl(mavenRepo), false, true, null);
        JavaContext context = JavaSourceProviderUsingMaven.createFromLocalMavenProject(inputProject, dependencyCollector);
        String fqnOfClass = getFQN(springBootApp);
        try {
            context.getClassLoader().loadClass(fqnOfClass);
        } catch (ClassNotFoundException e) {
            AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(fqnOfClass, "Could not load class. ClassNotFoundException was thrown."));
            LOG.debug("Could not find class", e);
        }

        URL[] urls = dependencyCollector.asUrls();
        for (URL url : urls) {
            String urlWithoutType = url.toString().substring(6);
            if (urlWithoutType.endsWith(".jar")) {
                try {
                    Model model = new MavenBridgeImpl().readEffectiveModelFromLocation(new File(urlWithoutType), false);
                    Artifact artifact = new DefaultArtifact(model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion());
                    applicationStartupLibrariesOfProject.add(artifact);
                } catch (Exception e) {
                    AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(urlWithoutType, "Failed to resolve effective POM this artifact"));
                    LOG.debug("Failed to resolve effective POM this artifact: " + urlWithoutType, e);
                }
            }
        }
        return applicationStartupLibrariesOfProject;
    }

    /**
     * This method is traversing parent folders until it reaches java folder in order to get the FQN
     *
     * @param inputFile Java input file to retrieve FQN (Full Qualified Name)
     * @return qualified name with full package
     */
    public static String getFQN(Path inputFile) {

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
    private static String getPackageName(Path folder, String packageName) {

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
     * Generates a list of all artifacts of the given nodes
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
     * Generates a list of all artifacts of the given node recursively
     *
     * @param node                  Node to analyze
     * @param mavenRepoLocation     Location of the local maven repository
     * @param allArtifactsOfProject List of all artifacts
     */
    private static List<Artifact> findArtifactsOfNode(DependencyNode node, File mavenRepoLocation, List<Artifact> allArtifactsOfProject) {

        List<DependencyNode> childrenFromNode = node.getChildren();
        for (DependencyNode child : childrenFromNode) {
            Artifact artifact = child.getArtifact();
            boolean artifactAlreadyInList = allArtifactsOfProject
                    .stream()
                    .anyMatch(listEntry -> listEntry.getGroupId().equals(artifact.getGroupId()) && listEntry.getArtifactId().equals(artifact.getArtifactId()) && listEntry.getVersion().equals(artifact.getVersion()));
            if (!artifactAlreadyInList) {
                File file;
                try {
                    file = tryFindJarInLocalMavenRepo(artifact, mavenRepoLocation);
                } catch (ArtifactResolutionException e) {
                    AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(), "Could not find jar file in local maven repository. Reflection analysis of this artifact is not possible."));
                    LOG.debug("Could not find jar file in local maven repository for artifact: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + ". Reflection analysis of this artifact is not possible.");
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
     * Tries to find the java archive of the specified artifact in the local Maven repository and in the online repository.
     *
     * @param artifact          Artifact to find
     * @param mavenRepoLocation Location of the local maven repository
     * @return The searched jar file or null
     * @throws ArtifactResolutionException Throws Exception when artifact is not resolvable
     */
    public static File tryFindJarInLocalMavenRepo(Artifact artifact, File mavenRepoLocation) throws ArtifactResolutionException {

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
     * Tries to resolve the specified artifact in the online maven repository and store it in the local maven repository
     *
     * @param artifact          Artifact to resolve
     * @param mavenRepoLocation Location of the local maven repository
     * @return The successfully resolved artifact
     * @throws ArtifactResolutionException If the artifact is not resolvable
     */
    private static Artifact resolveArtifactFromMavenOnlineRepository(Artifact artifact, File mavenRepoLocation) throws ArtifactResolutionException {

        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = newRepositorySystemSession(system, mavenRepoLocation);

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(newRepositories());
        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact();
    }


    /**
     * Initiates the repository system
     *
     * @return repository system
     */
    private static RepositorySystem newRepositorySystem() {

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
     * Initiate the repository system session
     *
     * @return repository system session
     */
    private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, File mavenRepoLocation) {

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(mavenRepoLocation.toString());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    /**
     * Initiate remote repositories
     *
     * @return List of remote repositories
     */
    private static List<RemoteRepository> newRepositories() {

        return new ArrayList<>(Collections.singletonList(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()));
    }
}