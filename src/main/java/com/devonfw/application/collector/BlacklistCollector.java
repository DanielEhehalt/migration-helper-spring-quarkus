package com.devonfw.application.collector;

import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.model.MavenIdentifier;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects blacklist
 */
public class BlacklistCollector {

    private static final Logger LOG = LoggerFactory.getLogger(BlacklistCollector.class);

    /**
     * Converts output from CSV parser to blackList
     *
     * @param csvOutput Output from CSV parser
     * @return Blacklist
     */
    public static List<BlacklistEntry> generateBlacklist(List<List<String>> csvOutput) {

        List<BlacklistEntry> blackList = new ArrayList<>();

        csvOutput.forEach(entry -> {
            if (entry.get(1).equals("mandatory")) {
                String mtaRuleId = entry.get(0);
                String description = entry.get(2);

                //Searches for duplicate entries. MTA generates irrelevant duplicate entries
                boolean duplicateEntry = false;
                for (BlacklistEntry blacklistEntry : blackList) {
                    if (blacklistEntry.getMtaRuleId().equals(mtaRuleId)) {
                        duplicateEntry = true;
                        break;
                    }
                }

                if (!duplicateEntry) {
                    blackList.add(new BlacklistEntry(mtaRuleId, description));
                }
            }
        });

        //Enhancing the found incompatibilities with the corresponding groupId and artifactId
        return resolveGroupIdAndArtifactIdFromMtaRules(blackList);
    }

    /**
     * Parses the rules (xml-files) stored in the MTA and resolves the possible groupId and the artifactId that belong to the blacklist entries.
     *
     * @param blacklist Blacklist to compare
     * @return Map with the rule ids and the corresponding groupId and artifactId
     */
    public static List<BlacklistEntry> resolveGroupIdAndArtifactIdFromMtaRules(List<BlacklistEntry> blacklist) {

        File[] directories = new File(System.getProperty("user.dir") + "\\tools\\mta-cli-5.2.1\\rules\\migration-core\\quarkus").listFiles(File::isDirectory);

        for (File directory : directories) {
            File[] rules = new File(String.valueOf(directory)).listFiles();
            for (File rule : rules) {
                try {
                    String contentType = Files.probeContentType(Path.of(String.valueOf(rule)));
                    if (contentType != null && contentType.equals("text/xml")) {
                        SAXParserFactory saxParserFactory = SAXParserFactory.newDefaultInstance();
                        SAXParser saxParser = saxParserFactory.newSAXParser();
                        RuleXmlHandler ruleXmlHandler = new RuleXmlHandler(blacklist);
                        saxParser.parse(rule, ruleXmlHandler);
                        blacklist = ruleXmlHandler.getBlacklist();
                    }
                } catch (IOException | ParserConfigurationException | SAXException e) {
                    e.printStackTrace();
                }
            }
        }

        return blacklist;
    }

    /**
     * Handler class to parse the MTA rules
     */
    public static class RuleXmlHandler extends DefaultHandler {

        private List<BlacklistEntry> blacklist;
        private int indexOfBlackListEntry = -1;

        public RuleXmlHandler(List<BlacklistEntry> blacklist) {

            this.blacklist = blacklist;
        }

        @Override
        public void startElement(String uri, String lName, String qName, Attributes attr) {

            if (qName.equals("rule") && indexOfBlackListEntry == -1) {
                indexOfBlackListEntry = getIndexOfBlacklistEntryByRuleId(attr.getValue("id"));
            } else if (qName.equals("dependency") && indexOfBlackListEntry != -1) {
                if (blacklist.get(indexOfBlackListEntry).getMavenIdentifiers()
                        .stream()
                        .noneMatch(mavenIdentifier -> mavenIdentifier.getGroupId().equals(attr.getValue("groupId")) && mavenIdentifier.getArtifactId().equals(attr.getValue("artifactId")))) {
                    blacklist.get(indexOfBlackListEntry).getMavenIdentifiers().add(new MavenIdentifier(attr.getValue("groupId"), attr.getValue("artifactId")));
                }
            } else if (qName.equals("artifact") && indexOfBlackListEntry != -1) {
                if (blacklist.get(indexOfBlackListEntry).getMavenIdentifiers()
                        .stream()
                        .noneMatch(mavenIdentifier -> mavenIdentifier.getGroupId().equals(attr.getValue("groupId")) && mavenIdentifier.getArtifactId().equals(attr.getValue("artifactId")))) {
                    blacklist.get(indexOfBlackListEntry).getMavenIdentifiers().add(new MavenIdentifier(attr.getValue("groupId"), attr.getValue("artifactId")));
                }
            } else if (qName.equals("javaclass") && indexOfBlackListEntry != -1) {
                if (blacklist.get(indexOfBlackListEntry).getMavenIdentifiers()
                        .stream()
                        .noneMatch(mavenIdentifier -> mavenIdentifier.getGroupId().equals(attr.getValue("references")))) {
                    blacklist.get(indexOfBlackListEntry).getPackages().add(attr.getValue("references"));
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            if (qName.equals("rule")) {
                indexOfBlackListEntry = -1;
            }
        }

        private Integer getIndexOfBlacklistEntryByRuleId(String ruleId) {

            Optional<BlacklistEntry> optionalBlacklistEntry = blacklist.stream().filter(blacklistEntry -> blacklistEntry.getMtaRuleId().equals(ruleId)).findFirst();
            return optionalBlacklistEntry.map(blacklistEntry -> blacklist.indexOf(blacklistEntry)).orElse(-1);
        }

        public List<BlacklistEntry> getBlacklist() {

            return blacklist;
        }
    }
}
