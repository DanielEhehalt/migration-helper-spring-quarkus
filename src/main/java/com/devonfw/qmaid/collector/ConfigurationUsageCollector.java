package com.devonfw.qmaid.collector;

import com.devonfw.qmaid.model.ConfigurationUsageInProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects Spring configuration usage
 */
public class ConfigurationUsageCollector {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationUsageCollector.class);

    List<ConfigurationUsageInProject> configurationInjectionUsageInProject;
    List<ConfigurationUsageInProject> configurationPropertyUsageInProject;

    public ConfigurationUsageCollector(File inputProjectLocation, List<List<String>> csvOutput) {

        collectConfigurationUsageInProject(csvOutput, inputProjectLocation);
    }

    /**
     * This method converts the output from the CSV parser to a list of ConfigurationUsageInProject objects
     *
     * @param csvOutput Output from CSV parser
     * @return Spring configuration usage list
     */
    private void collectConfigurationUsageInProject(List<List<String>> csvOutput, File inputProjectLocation) {

        configurationInjectionUsageInProject = new ArrayList<>();
        configurationPropertyUsageInProject = new ArrayList<>();

        for (List<String> csvEntry : csvOutput) {
            if (csvEntry.get(1).equals("configuration") && csvEntry.get(0).equals("configuration-annotations-0000")) {
                String className = csvEntry.get(6);
                String path = csvEntry.get(7).substring(inputProjectLocation.toString().length() + 1);
                if (configurationInjectionUsageInProject.stream().noneMatch(configurationUsage -> configurationUsage.getClassName().equals(className) && configurationUsage.getPath().equals(path))) {
                    configurationInjectionUsageInProject.add(new ConfigurationUsageInProject(className, path));
                }
            } else if (csvEntry.get(1).equals("configuration") && csvEntry.get(0).equals("configuration-annotations-0001")) {
                String className = csvEntry.get(6);
                String path = csvEntry.get(7).substring(inputProjectLocation.toString().length() + 1);
                if (configurationPropertyUsageInProject.stream().noneMatch(configurationUsage -> configurationUsage.getClassName().equals(className) && configurationUsage.getPath().equals(path))) {
                    configurationPropertyUsageInProject.add(new ConfigurationUsageInProject(className, path));
                }
            }
        }
    }

    public List<ConfigurationUsageInProject> getConfigurationInjectionUsageInProject() {
        return configurationInjectionUsageInProject;
    }

    public List<ConfigurationUsageInProject> getConfigurationPropertyUsageInProject() {
        return configurationPropertyUsageInProject;
    }
}
