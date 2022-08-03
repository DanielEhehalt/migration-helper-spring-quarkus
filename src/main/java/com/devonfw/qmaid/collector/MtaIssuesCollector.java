package com.devonfw.qmaid.collector;

import com.devonfw.qmaid.model.MtaIssue;
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
 * Collects found MTA issues
 */
public class MtaIssuesCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MtaIssuesCollector.class);

    List<MtaIssue> mtaIssuesList;

    public MtaIssuesCollector() {

        mtaIssuesList = new ArrayList<>();
    }

    /**
     * This method converts the output from the CSV parser to a list with MTA issues
     *
     * @param csvOutput Output from CSV parser
     */
    public void generateMtaIssuesList(List<List<String>> csvOutput) {

        for (List<String> entry : csvOutput) {
            if (entry.get(1).equals("mandatory")) {
                String mtaRuleId = entry.get(0);
                String description = entry.get(2);

                //Searches for duplicate entries. MTA generates irrelevant duplicate entries
                if (mtaIssuesList.stream().noneMatch(mtaIssue -> mtaIssue.getMtaRuleId().equals(mtaRuleId))) {
                    mtaIssuesList.add(new MtaIssue(mtaRuleId, description));
                }
            }
        }

        //Enhancing the found incompatibilities with the corresponding groupId and artifactId
        File locationOfBuiltInMtaRules = new File(System.getProperty("user.dir") +
                File.separator + "tools" + File.separator + "mta-cli-5.2.1" + File.separator + "rules" + File.separator + "migration-core" + File.separator + "quarkus");
        System.out.println(locationOfBuiltInMtaRules);
        mtaIssuesList = resolveGroupIdAndArtifactIdFromMtaRules(mtaIssuesList, locationOfBuiltInMtaRules);

        File locationOfCustomMtaRules = new File(System.getProperty("user.dir") + File.separator + "tools" + File.separator + "custom-mta-rules");
        mtaIssuesList = resolveGroupIdAndArtifactIdFromMtaRules(mtaIssuesList, locationOfCustomMtaRules);
    }

    /**
     * This method parses the rules (xml files) stored in the MTA to enhance the MTA issues with the blacklisted artifacts and packages.
     *
     * @param mtaIssuesList    MTA issues list to compare
     * @param locationMTARules Location of the MTA rules
     * @return Map with the rule ids and the corresponding groupId and artifactId
     */
    private List<MtaIssue> resolveGroupIdAndArtifactIdFromMtaRules(List<MtaIssue> mtaIssuesList, File locationMTARules) {

        File[] directories = locationMTARules.listFiles(File::isDirectory);

        for (File directory : directories) {
            File[] rules = new File(String.valueOf(directory)).listFiles();
            for (File rule : rules) {
                try {
                    String contentType = Files.probeContentType(Path.of(String.valueOf(rule)));
                    if (contentType != null && (contentType.equals("text/xml") || contentType.equals("application/xml"))) {
                        SAXParserFactory saxParserFactory = SAXParserFactory.newDefaultInstance();
                        SAXParser saxParser = saxParserFactory.newSAXParser();
                        RuleXmlHandler ruleXmlHandler = new RuleXmlHandler(mtaIssuesList);
                        saxParser.parse(rule, ruleXmlHandler);
                        mtaIssuesList = ruleXmlHandler.getMtaIssuesList();
                    }
                } catch (IOException | ParserConfigurationException | SAXException e) {
                    e.printStackTrace();
                }
            }
        }

        return mtaIssuesList;
    }

    /**
     * Handler class to parse the MTA rules
     */
    public class RuleXmlHandler extends DefaultHandler {

        private List<MtaIssue> mtaIssuesList;
        private int indexOfMtaIssuesListEntry = -1;

        public RuleXmlHandler(List<MtaIssue> mtaIssuesList) {

            this.mtaIssuesList = mtaIssuesList;
        }

        @Override
        public void startElement(String uri, String lName, String qName, Attributes attr) {

            if (qName.equals("rule") && indexOfMtaIssuesListEntry == -1) {
                indexOfMtaIssuesListEntry = getIndexOfMtaIssuesListEntryByRuleId(attr.getValue("id"));
            } else if (qName.equals("dependency") && indexOfMtaIssuesListEntry != -1) {
                if (mtaIssuesList.get(indexOfMtaIssuesListEntry).getMavenIdentifiers()
                        .stream()
                        .noneMatch(mavenIdentifier -> mavenIdentifier.getGroupId()
                                .equals(attr.getValue("groupId")) && mavenIdentifier.getArtifactId()
                                .equals(attr.getValue("artifactId")))) {
                    mtaIssuesList.get(indexOfMtaIssuesListEntry).getMavenIdentifiers()
                            .add(new MtaIssue.MavenIdentifier(attr.getValue("groupId"), attr.getValue("artifactId")));
                }
            } else if (qName.equals("artifact") && indexOfMtaIssuesListEntry != -1) {
                if (mtaIssuesList.get(indexOfMtaIssuesListEntry).getMavenIdentifiers()
                        .stream()
                        .noneMatch(mavenIdentifier -> mavenIdentifier.getGroupId()
                                .equals(attr.getValue("groupId")) && mavenIdentifier.getArtifactId()
                                .equals(attr.getValue("artifactId")))) {
                    mtaIssuesList.get(indexOfMtaIssuesListEntry).getMavenIdentifiers()
                            .add(new MtaIssue.MavenIdentifier(attr.getValue("groupId"), attr.getValue("artifactId")));
                }
            } else if (qName.equals("javaclass") && indexOfMtaIssuesListEntry != -1) {
                if (mtaIssuesList.get(indexOfMtaIssuesListEntry).getMavenIdentifiers()
                        .stream()
                        .noneMatch(mavenIdentifier -> mavenIdentifier.getGroupId().equals(attr.getValue("references")))) {
                    mtaIssuesList.get(indexOfMtaIssuesListEntry).getPackages().add(attr.getValue("references"));
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            if (qName.equals("rule")) {
                indexOfMtaIssuesListEntry = -1;
            }
        }

        private Integer getIndexOfMtaIssuesListEntryByRuleId(String ruleId) {

            Optional<MtaIssue> optionalMtaIssuesListEntry =
                    mtaIssuesList.stream().filter(mtaIssue -> mtaIssue.getMtaRuleId().equals(ruleId)).findFirst();
            return optionalMtaIssuesListEntry.map(mtaIssue -> mtaIssuesList.indexOf(mtaIssue)).orElse(-1);
        }

        public List<MtaIssue> getMtaIssuesList() {

            return mtaIssuesList;
        }
    }

    public List<MtaIssue> getMtaIssuesList() {

        return mtaIssuesList;
    }
}
