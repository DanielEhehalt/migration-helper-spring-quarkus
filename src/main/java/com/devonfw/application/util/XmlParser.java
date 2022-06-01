package com.devonfw.application.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * Utility class to parse XML files
 */
public class XmlParser {

    private static final Logger LOG = LoggerFactory.getLogger(XmlParser.class);

    /**
     * Parses the rules (xml-files) stored in the MTA and resolves the packages that belong to the rules.
     *
     * @return Map with the rule ids and the corresponding packages
     */
    public static HashMap<String, String> resolvePackagesFromRules() {

        HashMap<String, String> packagesOfRules = new HashMap<>();

        File[] directories = new File(System.getProperty("user.dir") + "\\tools\\mta-cli-5.2.1\\rules\\migration-core\\quarkus").listFiles(File::isDirectory);

        for (File directory : directories) {
            File[] rules = new File(String.valueOf(directory)).listFiles();
            for (File rule : rules) {
                try {
                    String contentType = Files.probeContentType(Path.of(String.valueOf(rule)));
                    if (contentType != null && contentType.equals("text/xml")) {
                        SAXParserFactory saxParserFactory = SAXParserFactory.newDefaultInstance();
                        SAXParser saxParser = saxParserFactory.newSAXParser();
                        RuleXmlHandler ruleXmlHandler = new RuleXmlHandler(packagesOfRules);
                        saxParser.parse(rule, ruleXmlHandler);
                        packagesOfRules = ruleXmlHandler.getPackagesOfRules();
                    }
                } catch (IOException | ParserConfigurationException | SAXException e) {
                    e.printStackTrace();
                }
            }
        }

        return packagesOfRules;
    }


    /**
     * Handler class to parse the MTA rules
     */
    public static class RuleXmlHandler extends DefaultHandler {

        private final HashMap<String, String> packagesOfRules;
        private String insideRule = "";

        public RuleXmlHandler(HashMap<String, String> packagesOfRules) {

            this.packagesOfRules = packagesOfRules;
        }

        @Override
        public void startElement(String uri, String lName, String qName, Attributes attr) {

            if (qName.equals("rule") && insideRule.equals("")) {
                insideRule = attr.getValue("id");
                packagesOfRules.put(insideRule, "");
            } else if (qName.equals("dependency") && !insideRule.equals("")) {
                packagesOfRules.replace(insideRule, attr.getValue("groupId") + "." + attr.getValue("artifactId"));
            } else if (qName.equals("artifact") && !insideRule.equals("") && packagesOfRules.get(insideRule).equals("")) {
                packagesOfRules.replace(insideRule, attr.getValue("groupId") + "." + attr.getValue("artifactId"));
            } else if (qName.equals("javaclass") && !insideRule.equals("") && packagesOfRules.get(insideRule).equals("")) {
                packagesOfRules.replace(insideRule, attr.getValue("references"));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            if (qName.equals("rule")) {
                insideRule = "";
            }
        }

        public HashMap<String, String> getPackagesOfRules() {

            return packagesOfRules;
        }
    }
}
