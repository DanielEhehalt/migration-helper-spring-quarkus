package com.devonfw.application;

import com.devonfw.application.analyzer.ProjectAnalyzer;
import com.devonfw.application.collector.BlacklistCollector;
import com.devonfw.application.collector.ReflectionUsageCollector;
import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.model.ReflectionUsageEntry;
import com.devonfw.application.util.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.time.Instant;
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

    @CommandLine.Option(names = {"-p", "--project"}, description = "Project location")
    private String inputProject;

    @CommandLine.Option(names = {"-a", "--app"}, description = "Application entry point location (@SpringBootApplication)")
    private String app;

    @CommandLine.Option(names = {"-m", "--mavenRepo"}, description = "Maven repository location")
    private String mavenRepo;

    @CommandLine.Option(names = {"-w", "--withoutDependencies"}, defaultValue = "false", description = "Without analysis of the reflection usage of the dependencies")
    private Boolean withoutReflectionUsageOfDependencies;

    @CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false", description = "Enable debug logging")
    private Boolean debugLoggingEnabled;

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

        //Set log level
        org.apache.logging.log4j.Logger logger = LogManager.getRootLogger();
        if (debugLoggingEnabled) {
            Configurator.setAllLevels(logger.getName(), Level.DEBUG);
        } else {
            Configurator.setAllLevels(logger.getName(), Level.INFO);
        }

        //Execute analysis
        if (!inputProject.equals("") || !mavenRepo.equals("") || !app.equals("")) {
            File inputProjectLocation = new File(this.inputProject);
            File mavenRepoLocation = new File(this.mavenRepo);
            File applicationEntryPoint = new File(this.app);
            File projectPom = new File(this.inputProject + "\\pom.xml");

            checkIfFilesExist(inputProjectLocation, mavenRepoLocation, applicationEntryPoint, projectPom);

            //Create directory for results
            String resultPath = createDirectoryForResults();

            LOG.info("Start the analysis");
            LOG.info("Filepath: " + inputProjectLocation);
            LOG.info("Result folder: " + resultPath);

            //Step 1: Project analysis: Generates list of blacklisted packages and of the reflection usage in the project
            boolean execution = MtaExecutor.executeMtaForProject(inputProjectLocation.toString(), resultPath);
            List<List<String>> csvOutput = CsvParser.parseCSV(resultPath);
            List<BlacklistEntry> blacklist = BlacklistCollector.generateBlacklist(csvOutput);
            List<ReflectionUsageEntry> reflectionUsageInProject = ReflectionUsageCollector.generateReflectionUsageList(csvOutput);

            //Step 2: Usage of blacklisted packages
            List<String> libraries = ProjectAnalyzer.collectAllLibrariesOfProject(applicationEntryPoint.toPath(), inputProjectLocation, mavenRepoLocation);
            File entryPoint = new File(applicationEntryPoint.getParent());
            HashMap<String, Integer> imports = ProjectAnalyzer.collectImportStatementsFromAllClasses(entryPoint, new HashMap<>());
            //ToDo: Map occurrence with libraries

            //Step 3: Reflection usage in the project dependencies
            List<ReflectionUsageEntry> reflectionUsageInDependencies = new ArrayList<>();
            if (!withoutReflectionUsageOfDependencies) {
                reflectionUsageInDependencies = ReflectionUsageCollector.collectReflectionUsageInDependencies(libraries, resultPath);
            }

            //Generate report
            ReportGenerator.generateReport(blacklist, reflectionUsageInProject, reflectionUsageInDependencies, inputProjectLocation.toString(), resultPath);
            System.exit(0);
        } else {
            LOG.error("Arguments -p, -a and -m are mandatory");
            System.exit(255);
        }
    }

    /**
     * Check if locations exist
     */
    private void checkIfFilesExist(File inputProjectLocation, File mavenRepoLocation, File applicationEntryPoint, File projectPom) {

        if (!inputProjectLocation.exists()) {
            LOG.error("Project does not exist");
            System.exit(1);
        } else if (!mavenRepoLocation.exists()) {
            LOG.error("Maven Repository location does not exist");
            System.exit(2);
        } else if (!applicationEntryPoint.exists()) {
            LOG.error("SpringBootApplication file does not exist");
            System.exit(3);
        } else if (!projectPom.exists()) {
            LOG.error("Can not found project POM in: " + projectPom);
            System.exit(4);
        }
    }

    /**
     * Create directory for results
     *
     * @return Path to directory
     */
    private String createDirectoryForResults() {

        Instant instant = Instant.now();
        long timeStampMillis = instant.toEpochMilli();
        String resultPath = System.getProperty("user.dir") + "\\results\\" + timeStampMillis;
        boolean mkdir = new File(resultPath).mkdirs();
        return resultPath;
    }
}