package com.devonfw.qmaid;

import com.devonfw.qmaid.collector.DependencyBlacklistCollector;
import com.devonfw.qmaid.collector.MtaIssuesCollector;
import com.devonfw.qmaid.collector.ReflectionUsageCollector;
import com.devonfw.qmaid.operator.DependencyTreeOperator;
import com.devonfw.qmaid.operator.ProjectOperator;
import com.devonfw.qmaid.util.CsvParser;
import com.devonfw.qmaid.util.DependencyUtilities;
import com.devonfw.qmaid.util.MtaExecutor;
import com.devonfw.qmaid.util.ReportGenerator;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.time.Instant;
import java.util.List;

/**
 * Manages CLI and initiates the analysis steps
 */
@CommandLine.Command(
        name = "QMAid",
        description = "Quarkus Migration Aid"
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

    @CommandLine.Option(names = {"-wd", "--withoutDependencies"}, defaultValue = "false",
            description = "With analysis of the reflection usage of the dependencies")
    private Boolean withoutDependencyAnalysis;

    @CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false", description = "Enable debug logging")
    private Boolean debugLoggingEnabled;

    /**
     * Main method. Initiates CLI
     */
    public static void main(String[] args) {

        new CommandLine(new Application()).execute(args);
    }

    /**
     * This method execute the analysis steps
     */
    @Override
    public void run() {

        //Print Help
        if (help) {
            System.out.println(printHelp());
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
            File mavenRepoLocation;
            if (mavenRepo.contains("~")) {
                mavenRepoLocation = new File(System.getProperty("user.home") + mavenRepo.substring(1));
            } else {
                mavenRepoLocation = new File(this.mavenRepo);
            }
            File applicationEntryPointLocation = new File(this.app);
            File projectPomLocation = new File(this.inputProject + File.separator + "pom.xml");
            File resultFolderLocation = new File(createDirectoryForResults());

            checkIfFilesExist(inputProjectLocation, mavenRepoLocation, applicationEntryPointLocation, projectPomLocation);

            LOG.info("Start the analysis");
            LOG.info("Project location: " + inputProjectLocation);
            LOG.info("Result folder: " + resultFolderLocation);

            executeAnalysis(inputProjectLocation, mavenRepoLocation, applicationEntryPointLocation, projectPomLocation, resultFolderLocation);

            System.exit(0);
        } else {
            LOG.error("Arguments -p, -a and -m are mandatory");
            System.exit(255);
        }
    }

    private void executeAnalysis(File inputProjectLocation, File mavenRepoLocation, File applicationEntryPointLocation, File projectPomLocation,
                                 File resultFolderLocation) {

        ProjectOperator projectOperator = new ProjectOperator(inputProjectLocation, mavenRepoLocation, applicationEntryPointLocation);
        DependencyTreeOperator dependencyTreeOperator = new DependencyTreeOperator(projectPomLocation, mavenRepoLocation,
                projectOperator.getApplicationStartupLibrariesOfProject());

        MtaExecutor.executeMtaForProject(inputProjectLocation, resultFolderLocation);
        List<List<String>> mtaOutput = CsvParser.parseCSV(resultFolderLocation);

        MtaIssuesCollector mtaIssuesCollector = new MtaIssuesCollector();
        mtaIssuesCollector.generateMtaIssuesList(mtaOutput);

        ReflectionUsageCollector reflectionUsageCollector = new ReflectionUsageCollector(inputProjectLocation, mtaOutput);

        if (!withoutDependencyAnalysis) {
            LOG.info("Start scanning dependencies. " + dependencyTreeOperator.getAllArtifactsOfProject().size() + " dependencies found");
            dependencyTreeOperator.getAllArtifactsOfProject().forEach(dependency -> {
                MtaExecutor.executeMtaForLibrary(dependency.getFile(), resultFolderLocation);
                List<List<String>> mtaOutputDependency = CsvParser.parseCSV(resultFolderLocation);
                mtaIssuesCollector.generateMtaIssuesList(mtaOutputDependency);
                reflectionUsageCollector.generateReflectionUsageInDependenciesList(mtaOutputDependency);
            });
            reflectionUsageCollector.setReflectionUsageInDependencies(
                    DependencyUtilities.mapJarFilesToFullArtifactNames(reflectionUsageCollector.getReflectionUsageInDependencies(),
                            dependencyTreeOperator.getAllArtifactsOfProject()));
        }

        DependencyBlacklistCollector dependencyBlacklistCollector = new DependencyBlacklistCollector(mtaIssuesCollector.getMtaIssuesList(),
                dependencyTreeOperator.getProjectDependencies(), dependencyTreeOperator.getDependencyTreeRootNodes());

        projectOperator.occurrenceMeasurement(new File(applicationEntryPointLocation.getParent()),
                dependencyBlacklistCollector.getDependencyBlacklist(), dependencyTreeOperator);

        ReportGenerator reportGenerator = new ReportGenerator(dependencyBlacklistCollector.getDependencyBlacklist(),
                projectOperator.getTotalJavaClassesScanned(), mtaIssuesCollector.getMtaIssuesList(),
                reflectionUsageCollector.getReflectionUsageInProject(),
                reflectionUsageCollector.getReflectionUsageInDependencies(), dependencyTreeOperator.getDependencyTreeRootNodes(),
                projectPomLocation, resultFolderLocation, withoutDependencyAnalysis);
    }

    /**
     * This method checks if all given locations exist
     */
    private void checkIfFilesExist(File inputProjectLocation, File mavenRepoLocation, File applicationEntryPoint, File projectPom) {

        if (!inputProjectLocation.exists()) {
            LOG.error("Project does not exist on: " + inputProjectLocation);
            System.exit(1);
        } else if (!mavenRepoLocation.exists()) {
            LOG.error("Maven Repository location does not exist on: " + mavenRepoLocation);
            System.exit(2);
        } else if (!applicationEntryPoint.exists()) {
            LOG.error("SpringBootApplication file does not exist on: " + applicationEntryPoint);
            System.exit(3);
        } else if (!projectPom.exists()) {
            LOG.error("Can not found project POM in: " + projectPom);
            System.exit(4);
        }
    }

    /**
     * This method creates the directory for the results
     *
     * @return Path to directory
     */
    private String createDirectoryForResults() {

        Instant instant = Instant.now();
        long timeStampMillis = instant.toEpochMilli();
        String resultPath = System.getProperty("user.dir") + File.separator + "results" + File.separator + timeStampMillis;
        boolean mkdir = new File(resultPath).mkdirs();
        return resultPath;
    }

    /**
     * This method returns the help text for the -h / --help argument
     *
     * @return The help text
     */
    private String printHelp() {

        return "This migration helper analyzes Spring Boot microservices in terms of migration capability to Quarkus.\n" +
                "After various analyses, a decision aid is created that estimates the effort and identifies tasks.\n" +
                "Currently, only Maven is supported as build tool. This tool focuses on microservices.\n" +
                "The analysis of projects with multiple modules is possible with limitations.\n" +
                "When specifying the project location, the folder of the module containing the application entry class must be specified.\n\n" +

                "Options:\n" +
                "-p  --project                Maven project location (mandatory)\n" +
                "-a  --app                    Application entry point location (@SpringBootApplication) (mandatory)\n" +
                "-m  --mavenRepo              Local Maven repository location (mandatory)\n" +
                "-wd --withoutDependencies    Without analysis of the reflection usage of the dependencies. This analysis can take a very long time\n" +
                "-v  --verbose                Enable debug logging\n" +
                "-h  --help                   Display help";
    }
}