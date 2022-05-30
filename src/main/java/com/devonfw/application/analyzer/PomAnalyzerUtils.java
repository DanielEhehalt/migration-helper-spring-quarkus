package com.devonfw.application.analyzer;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Utilities for analyzing the maven project object model
 */
public class PomAnalyzerUtils {

    /**
     * Collects java version from a maven project
     *
     * @param inputProject Path of project
     * @return Java version
     */
    public static String getJavaVersionFromProject(Path inputProject) {

        String javaVersion = "undefined";
        try {
            SAXParserFactory saxParserFactory = SAXParserFactory.newDefaultInstance();
            SAXParser saxParser = saxParserFactory.newSAXParser();
            PomXmlJavaVersionHandler handler = new PomXmlJavaVersionHandler();
            saxParser.parse(new File(inputProject.toString() + "/pom.xml"), handler);
            javaVersion = handler.getJavaVersion();
        } catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
        return javaVersion;
    }

    /**
     * Collects dependencies from an java project
     *
     * @param inputProject Path of project
     * @return List of dependencies
     */
    public static List<Dependency> getDependenciesFromProject(Path inputProject) {

        List<Dependency> dependencies = new ArrayList<>();
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(inputProject.toString() + "/pom.xml"));
            dependencies = model.getDependencies();
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
        return dependencies;
    }

    /**
     * Collects name and version from a maven project
     *
     * @param inputProject Path of project
     * @return Name and version as String
     */
    public static String getNameAndVersionFromProject(Path inputProject) {

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader(inputProject.toString() + "/pom.xml"));
            return model.getName() + "-" + model.getVersion();
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        }
        return "Project name not available";
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