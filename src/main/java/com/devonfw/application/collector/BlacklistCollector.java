package com.devonfw.application.collector;

import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.util.XmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
                String name = entry.get(0);
                String description = entry.get(2);

                //Searches for duplicate entries. MTA generates irrelevant duplicate entries
                boolean duplicateEntry = false;
                for (BlacklistEntry blacklistEntry : blackList) {
                    if (blacklistEntry.getRuleId().equals(name)) {
                        duplicateEntry = true;
                        break;
                    }
                }

                if (!duplicateEntry) {
                    blackList.add(new BlacklistEntry(name, description));
                }
            }
        });

        //Enhancing the found incompatibilities with the package names
        HashMap<String, String> packagesOfRules = XmlParser.resolvePackagesFromRules();
        blackList.forEach(blacklistEntry -> packagesOfRules.forEach((id, blacklistedPackage) -> {
            if (blacklistEntry.getRuleId().equals(id)) {
                blacklistEntry.setNameOfPackage(blacklistedPackage);
            }
        }));

        return blackList;
    }
}
