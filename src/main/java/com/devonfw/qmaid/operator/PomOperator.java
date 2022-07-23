package com.devonfw.qmaid.operator;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Operator class for analyzing the Maven project object model
 */
public class PomOperator {

    private static final Logger LOG = LoggerFactory.getLogger(PomOperator.class);

    public PomOperator() {}

    /**
     * This method collects the java version from a Maven POM
     *
     * @param projectPomLocation Location of project POM
     * @return Java version
     */
    public String getJavaVersionFromPomFile(File projectPomLocation) {

        String javaVersion = "undefined";
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader(projectPomLocation));
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
     * This method collects the artifact identifier and the version from a Maven POM
     *
     * @param projectPomLocation Location of project POM
     * @return Name and version as String
     */
    public String getProjectIdentifierFromPomFile(File projectPomLocation) {

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader(projectPomLocation));
            String groupId = model.getGroupId();
            if (groupId == null) {
                groupId = model.getParent().getGroupId();
            }
            String artifactId = model.getArtifactId();
            if (artifactId == null) {
                artifactId = model.getParent().getArtifactId();
            }
            String version = model.getVersion();
            if (version == null) {
                version = model.getParent().getVersion();
            }
            return groupId + ":" + artifactId + ":" + version;
        } catch (IOException | XmlPullParserException e) {
            LOG.error("Could not collect name and version of project", e);
        }
        return "Project name not available";
    }
}