package com.devonfw.qmaid.collector;

import com.devonfw.qmaid.model.ReflectionUsageInDependencies;
import com.devonfw.qmaid.model.ReflectionUsageInProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects reflection usage
 */
public class ReflectionUsageCollector {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionUsageCollector.class);

    List<ReflectionUsageInProject> reflectionUsageInProject;
    List<ReflectionUsageInDependencies> reflectionUsageInDependencies;

    public ReflectionUsageCollector(File inputProjectLocation, List<List<String>> csvOutput) {

        reflectionUsageInDependencies = new ArrayList<>();
        collectReflectionUsageInProject(csvOutput, inputProjectLocation);
    }

    /**
     * This method converts the output from the CSV parser to a list of ReflectionUsageInProject objects
     *
     * @param csvOutput Output from CSV parser
     * @return reflection usage list
     */
    private void collectReflectionUsageInProject(List<List<String>> csvOutput, File inputProjectLocation) {

        reflectionUsageInProject = new ArrayList<>();

        for (List<String> csvEntry : csvOutput) {
            if (csvEntry.get(1).equals("reflection")) {
                String className = csvEntry.get(6);
                String path = csvEntry.get(7).substring(inputProjectLocation.toString().length() + 1);
                if (reflectionUsageInProject.stream().noneMatch(reflectionUsage -> reflectionUsage.getClassName().equals(className) && reflectionUsage.getPath().equals(path))) {
                    reflectionUsageInProject.add(new ReflectionUsageInProject(className, path));
                }
            }
        }
    }

    /**
     * This method converts the output from the CSV parser to a list of ReflectionUsageInProject objects
     *
     * @param csvOutput Output from CSV parser
     */
    public void generateReflectionUsageInDependenciesList(List<List<String>> csvOutput) {

        for (List<String> csvEntry : csvOutput) {
            if (csvEntry.get(1).equals("reflection")) {
                String jarFile = csvEntry.get(10);
                String className = csvEntry.get(6);

                Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsageInDependencies.stream()
                        .filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(jarFile))
                        .findAny();
                if (optionalReflectionUsageInDependency.isPresent()) {
                    reflectionUsageInDependencies.get(reflectionUsageInDependencies.indexOf(optionalReflectionUsageInDependency.get())).getClasses()
                            .add(className);
                } else {
                    ArrayList<String> classes = new ArrayList<>();
                    classes.add(className);
                    reflectionUsageInDependencies.add(new ReflectionUsageInDependencies(jarFile, classes));
                }
            }
        }
    }

    public List<ReflectionUsageInProject> getReflectionUsageInProject() {
        return reflectionUsageInProject;
    }

    public List<ReflectionUsageInDependencies> getReflectionUsageInDependencies() {
        return reflectionUsageInDependencies;
    }

    public void setReflectionUsageInDependencies(List<ReflectionUsageInDependencies> reflectionUsageInDependencies) {
        this.reflectionUsageInDependencies = reflectionUsageInDependencies;
    }
}
