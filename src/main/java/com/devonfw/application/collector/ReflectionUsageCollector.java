package com.devonfw.application.collector;

import com.devonfw.application.model.ReflectionUsageInDependencies;
import com.devonfw.application.model.ReflectionUsageInProject;
import com.devonfw.application.util.CsvParser;
import com.devonfw.application.util.DependencyUtilities;
import com.devonfw.application.util.MtaExecutor;
import org.eclipse.aether.artifact.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects reflection usage
 */
public class ReflectionUsageCollector {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionUsageCollector.class);

    /**
     * This method converts the output from the CSV parser to a list of ReflectionUsageInProject objects
     *
     * @param csvOutput    Output from CSV parser
     * @param inputProject Location of the project
     * @return reflection usage list
     */
    public static List<ReflectionUsageInProject> generateReflectionUsageInProjectList(List<List<String>> csvOutput,
                                                                                      String inputProject) {

        List<ReflectionUsageInProject> reflectionUsage = new ArrayList<>();

        for (List<String> csvEntry : csvOutput) {
            if (csvEntry.get(1).equals("reflection")) {
                String className = csvEntry.get(6);
                String path = csvEntry.get(7).substring(inputProject.length() + 1);
                reflectionUsage.add(new ReflectionUsageInProject(className, path));
            }
        }
        return reflectionUsage;
    }

    /**
     * This method runs the MTA for all specified libraries and collects the reflection usage of these libraries
     *
     * @param libraries  Libraries to analyze
     * @param resultPath Path for analysis results
     * @return Reflection usage of the specified libraries
     */
    public static List<ReflectionUsageInDependencies> collectReflectionUsageInLibraries(List<Artifact> libraries,
                                                                                        String resultPath) {

        List<ReflectionUsageInDependencies> reflectionUsage = new ArrayList<>();
        libraries.forEach(library -> {
            boolean execution = MtaExecutor.executeMtaToFindReflectionInLibrary(library.getFile().toString(), resultPath);
            List<List<String>> csvOutput = CsvParser.parseCSV(resultPath);
            List<ReflectionUsageInDependencies> reflectionUsageInDependencies =
                    generateReflectionUsageInDependenciesList(csvOutput);
            reflectionUsage.addAll(reflectionUsageInDependencies);
        });
        return DependencyUtilities.mapJarFilesToFullArtifactNames(reflectionUsage, libraries);
    }

    /**
     * This method converts the output from the CSV parser to a list of ReflectionUsageInProject objects
     *
     * @param csvOutput Output from CSV parser
     * @return reflection usage list
     */
    public static List<ReflectionUsageInDependencies> generateReflectionUsageInDependenciesList(
            List<List<String>> csvOutput) {

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
}
