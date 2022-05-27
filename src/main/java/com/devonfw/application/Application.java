package com.devonfw.application;

import com.devonfw.application.analyzer.Analyzer;
import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.model.ReflectionUsageEntry;
import com.devonfw.application.utils.CommandLineUtils;
import com.devonfw.application.utils.MTAExecutor;
import com.devonfw.application.utils.ReportGenerator;
import com.devonfw.application.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages CLI and initiates the analysis steps
 */
@CommandLine.Command(
        name = "Migration Helper Spring Quarkus",
        description = "Helps you"
)
public class Application implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @CommandLine.Option(names = {"-h", "--help"}, defaultValue = "false", description = "Help")
    private Boolean help;

    @CommandLine.Option(names = {"-f", "--file"}, description = "Filepath")
    private String inputProject;

    @CommandLine.Option(names = {"-m", "--mavenRepo"}, description = "Maven repository location")
    private String mavenRepo;

    /**
     * Main method. Initiates CLI
     */
    public static void main(String[] args) {

        new CommandLine(new Application()).execute(args);
    }

    /**
     * Execute the analysis steps
     */
    @Override
    public void run() {

        //Print Help
        if (help) {
            System.out.println(CommandLineUtils.printHelp());
            System.exit(0);
        }

        //Execute analysis
        else if (!inputProject.equals("") || !mavenRepo.equals("")) {
            //Check if locations exists
            File inputProjectLocation = new File(this.inputProject);
            File mavenRepoLocation = new File(mavenRepo);
            if (!inputProjectLocation.exists()) {
                System.out.println("Error: Project does not exist");
                System.exit(1);
            } else if (!mavenRepoLocation.exists()) {
                System.out.println("Error: Maven Repository location does not exist");
                System.exit(2);
            }

            //Create directory for results
            String resultPath = Utils.createDirectoryForResults();

            System.out.println("Start the analysis");
            System.out.println("Filepath: " + this.inputProject);
            System.out.println("Result folder: " + resultPath);

            //Execute MTA
            boolean execution = MTAExecutor.executeMTA(this.inputProject, resultPath);

            //Generate list of blacklisted packages
            List<List<String>> csvOutput = Utils.parseCSV(resultPath);
            List<BlacklistEntry> blacklist = Utils.generateBlacklist(csvOutput);
            List<ReflectionUsageEntry> reflectionUsage = Utils.generateReflectionUsageList(csvOutput);

            //Analyse usage of blacklisted packages
            //TODO: Analyse usage of blacklisted packages

            //Generate report
            ReportGenerator.generateReport(blacklist, reflectionUsage, Path.of(this.inputProject), resultPath);

            //Examples
            File entryPoint = new File(this.inputProject + "\\src\\main\\java");

            HashMap<String, Integer> packages = Analyzer.collectPackagesRecursively(entryPoint, Path.of(this.inputProject), Path.of(mavenRepo), new HashMap<>());
            System.out.println("############################## PACKAGES ##############################");
            sortMapByQuantityAndPrintContent(packages);

            HashMap<String, Integer> classes = Analyzer.collectClassesRecursively(entryPoint, Path.of(this.inputProject), Path.of(mavenRepo), new HashMap<>());
            System.out.println("############################## CLASSES ##############################");
            sortMapByQuantityAndPrintContent(classes);

            HashMap<String, Integer> imports = Analyzer.collectImportsRecursively(entryPoint, new HashMap<>());
            System.out.println("############################## IMPORTS ##############################");
            sortMapByQuantityAndPrintContent(imports);

            System.out.println("############################## DEPENDENCIES ##############################");
            Analyzer.collectDependenciesRecursively(entryPoint, Path.of(this.inputProject), Path.of(mavenRepo), new ArrayList<>()).forEach(System.out::println);

            //Exit
            System.exit(0);
        } else {
            System.out.println("Arguments -f and -m are mandatory");
            System.exit(255);
        }
    }

    public static void sortMapByQuantityAndPrintContent(HashMap<String, Integer> map) {
        map.entrySet().stream()
                .sorted((key1, key2) -> -key1.getValue().compareTo(key2.getValue()))
                .forEach(key -> System.out.println(key.getKey() + ": " + key.getValue()));
    }
}