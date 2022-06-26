package com.devonfw.application.collector;

import com.devonfw.application.model.ReflectionUsageInDependencies;
import com.devonfw.application.model.ReflectionUsageInProject;
import com.devonfw.application.util.CsvParser;
import com.devonfw.application.util.DependencyUtilities;
import com.devonfw.application.util.MtaExecutor;
import org.eclipse.aether.artifact.Artifact;
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

    public ReflectionUsageCollector(File inputProjectLocation, File resultFolderLocation) {

        reflectionUsageInDependencies = new ArrayList<>();
        generateReflectionUsageInProjectList(CsvParser.parseCSV(resultFolderLocation), inputProjectLocation);
    }

    /**
     * This method converts the output from the CSV parser to a list of ReflectionUsageInProject objects
     *
     * @param csvOutput Output from CSV parser
     * @return reflection usage list
     */
    private void generateReflectionUsageInProjectList(List<List<String>> csvOutput, File inputProjectLocation) {

        reflectionUsageInProject = new ArrayList<>();

        for (List<String> csvEntry : csvOutput) {
            if (csvEntry.get(1).equals("reflection")) {
                String className = csvEntry.get(6);
                String path = csvEntry.get(7).substring(inputProjectLocation.toString().length() + 1);
                reflectionUsageInProject.add(new ReflectionUsageInProject(className, path));
            }
        }
    }

    /**
     * This method runs the MTA for all specified dependencies and collects the reflection usage of these dependencies
     *
     * @param dependencies         Libraries to analyze
     * @param resultFolderLocation Path for analysis results
     */
    public void collectReflectionUsageInDependencies(List<Artifact> dependencies, File resultFolderLocation) {

        reflectionUsageInDependencies = new ArrayList<>();
        dependencies.forEach(library -> {
            boolean execution = MtaExecutor.executeMtaToFindReflectionInLibrary(library.getFile(), resultFolderLocation);
            List<List<String>> csvOutput = CsvParser.parseCSV(resultFolderLocation);
            List<ReflectionUsageInDependencies> reflectionUsageInDependencies = generateReflectionUsageInDependenciesList(csvOutput);
            reflectionUsageInDependencies.addAll(reflectionUsageInDependencies);
        });
        DependencyUtilities.mapJarFilesToFullArtifactNames(reflectionUsageInDependencies, dependencies);
    }

    /**
     * This method converts the output from the CSV parser to a list of ReflectionUsageInProject objects
     *
     * @param csvOutput Output from CSV parser
     * @return reflection usage list
     */
    private List<ReflectionUsageInDependencies> generateReflectionUsageInDependenciesList(List<List<String>> csvOutput) {

        List<ReflectionUsageInDependencies> reflectionUsage = new ArrayList<>();

        for (List<String> csvEntry : csvOutput) {
            if (csvEntry.get(1).equals("reflection")) {
                String jarFile = csvEntry.get(10);
                String className = csvEntry.get(6);

                Optional<ReflectionUsageInDependencies> optionalReflectionUsageInDependency = reflectionUsage.stream()
                        .filter(reflectionUsageInDependency -> reflectionUsageInDependency.getJarFile().equals(jarFile))
                        .findAny();
                if (optionalReflectionUsageInDependency.isPresent()) {
                    reflectionUsage.get(reflectionUsage.indexOf(optionalReflectionUsageInDependency.get())).getClasses()
                            .add(className);
                } else {
                    ArrayList<String> classes = new ArrayList<>();
                    classes.add(className);
                    reflectionUsage.add(new ReflectionUsageInDependencies(jarFile, classes));
                }
            }
        }
        return reflectionUsage;
    }

    public List<ReflectionUsageInProject> getReflectionUsageInProject() {
        return reflectionUsageInProject;
    }

    public List<ReflectionUsageInDependencies> getReflectionUsageInDependencies() {
        return reflectionUsageInDependencies;
    }
}
