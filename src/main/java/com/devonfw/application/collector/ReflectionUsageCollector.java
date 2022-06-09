package com.devonfw.application.collector;

import com.devonfw.application.model.ReflectionUsageEntry;
import com.devonfw.application.util.CsvParser;
import com.devonfw.application.util.MtaExecutor;
import org.eclipse.aether.artifact.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects reflection usage
 */
public class ReflectionUsageCollector {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionUsageCollector.class);

    /**
     * Converts output from CSV parser to a list of reflection usage
     *
     * @param csvOutput Output from CSV parser
     * @return reflection usage list
     */
    public static List<ReflectionUsageEntry> generateReflectionUsageList(List<List<String>> csvOutput) {

        List<ReflectionUsageEntry> reflectionUsage = new ArrayList<>();

        for (List<String> csvEntry : csvOutput) {
            if (csvEntry.get(1).equals("reflection")) {
                String nameOfClass = csvEntry.get(6);
                String library = csvEntry.get(5);

                boolean classWasAppended = false;
                for (ReflectionUsageEntry reflectionUsageEntry : reflectionUsage) {
                    if (reflectionUsageEntry.getClasses().contains(nameOfClass)) {
                        classWasAppended = true;
                        break;
                    }
                    if (reflectionUsageEntry.getPath().equals(library)) {
                        reflectionUsageEntry.getClasses().add(nameOfClass);
                        classWasAppended = true;
                        break;
                    }
                }
                if (!classWasAppended) {
                    List<String> classes = new ArrayList<>();
                    classes.add(nameOfClass);
                    reflectionUsage.add(new ReflectionUsageEntry(library, classes));
                }
            }
        }
        return reflectionUsage;
    }

    /**
     * Runs MTA for all specified libraries and collects reflection usage of these libraries
     * @param libraries Libraries to analyze
     * @param resultPath Path for analysis results
     * @return Reflection usage of the specified libraries
     */
    public static List<ReflectionUsageEntry> collectReflectionUsageInLibraries(List<Artifact> libraries, String resultPath) {

        List<ReflectionUsageEntry> reflectionUsage = new ArrayList<>();
        libraries.forEach(library -> {
            boolean execution = MtaExecutor.executeMtaToFindReflectionInLibrary(library.getFile().toString(), resultPath);
            List<List<String>> csvOutput = CsvParser.parseCSV(resultPath);
            List<ReflectionUsageEntry> temp = generateReflectionUsageList(csvOutput);
            reflectionUsage.addAll(temp);
        });
        return reflectionUsage;
    }
}
