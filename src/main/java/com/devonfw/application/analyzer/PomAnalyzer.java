package com.devonfw.application.analyzer;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
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
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
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
        try {
            SAXParserFactory saxParserFactory = SAXParserFactory.newDefaultInstance();
            SAXParser saxParser = saxParserFactory.newSAXParser();
            PomXmlJavaVersionHandler handler = new PomXmlJavaVersionHandler();
            File pomFile = new File(locationOfProjectPom);
            saxParser.parse(pomFile, handler);
            javaVersion = handler.getJavaVersion();
        } catch (IOException | ParserConfigurationException | SAXException e) {
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

    /**
     * Generates the dependency tree
     *
     * @param locationOfProjectPom Location of project POM
     * @param mavenRepoLocation    Location of the local maven repository
     * @return List with one DependencyNode per dependency in the project. Enriched with all children which have the scope compile
     */
    public static List<DependencyNode> generateDependencyTree(File locationOfProjectPom, File mavenRepoLocation) {

        List<DependencyNode> rootNodes = new ArrayList<>();

        MavenBridgeImpl mavenBridge = new MavenBridgeImpl(mavenRepoLocation);
        Model model = mavenBridge.readModel(locationOfProjectPom);

        for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            String version = dependency.getVersion();
            if (version == null) {
                //ToDo: Collect versions which are provided by a bom
                version = model.getParent().getVersion();
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
     * Generates a list of all artifacts of the given nodes
     *
     * @param rootNodes         Nodes to analyze
     * @param mavenRepoLocation Location of the local maven repository
     * @return List of all artifacts
     */
    public static List<Artifact> generateArtifactsList(List<DependencyNode> rootNodes, File mavenRepoLocation) {

        List<Artifact> allArtifactsOfProject = new ArrayList<>();
        for (DependencyNode rootNode : rootNodes) {
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
     */
    private static File tryFindJarInLocalMavenRepo(Artifact artifact, File mavenRepoLocation) throws ArtifactResolutionException {

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

    /**
     * Handler class to parse the pom.xml and get the java version
     */
    public static class PomXmlJavaVersionHandler extends DefaultHandler {

        private final StringBuilder elementValue = new StringBuilder();
        private String javaVersion = "undefined";
        private boolean javaVersionAvailable = false;

        public PomXmlJavaVersionHandler() {
        }

        @Override
        public void startElement(String uri, String lName, String qName, Attributes attr) {

            if (qName.equals("java.version")) {
                javaVersionAvailable = true;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            if (qName.equals("java.version")) {
                javaVersion = elementValue.toString();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {

            if (javaVersionAvailable) {
                elementValue.append(ch, start, length);
            }
        }

        public String getJavaVersion() {

            return javaVersion;
        }
    }
}