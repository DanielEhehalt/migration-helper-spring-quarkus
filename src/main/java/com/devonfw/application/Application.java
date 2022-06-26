package com.devonfw.application;

import com.devonfw.application.collector.DependencyBlacklistCollector;
import com.devonfw.application.collector.MtaIssuesCollector;
import com.devonfw.application.collector.ReflectionUsageCollector;
import com.devonfw.application.operator.DependencyTreeOperator;
import com.devonfw.application.operator.ProjectOperator;
import com.devonfw.application.util.ReportGenerator;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.time.Instant;

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

    @CommandLine.Option(names = {"-wd", "--withoutDependencies"}, defaultValue = "false",
            description = "With analysis of the reflection usage of the dependencies")
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
            File mavenRepoLocation = new File(this.mavenRepo);
            File applicationEntryPointLocation = new File(this.app);
            File projectPomLocation = new File(this.inputProject + File.separator + "pom.xml");

            checkIfFilesExist(inputProjectLocation, mavenRepoLocation, applicationEntryPointLocation, projectPomLocation);

            File resultFolderLocation = new File(createDirectoryForResults());

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
        MtaIssuesCollector mtaIssuesCollector = new MtaIssuesCollector(inputProjectLocation, resultFolderLocation);
        ReflectionUsageCollector reflectionUsageCollector = new ReflectionUsageCollector(inputProjectLocation, resultFolderLocation);
        DependencyBlacklistCollector dependencyBlacklistCollector = new DependencyBlacklistCollector(mtaIssuesCollector.getMtaIssuesList(),
                dependencyTreeOperator.getProjectDependencies(), dependencyTreeOperator.getDependencyTreeRootNodes());

        projectOperator.occurrenceMeasurement(new File(applicationEntryPointLocation.getParent()),
                dependencyBlacklistCollector.getDependencyBlacklist(), dependencyTreeOperator);
        if (!withoutReflectionUsageOfDependencies) {
            reflectionUsageCollector.collectReflectionUsageInDependencies(dependencyTreeOperator.getAllArtifactsOfProject(),
                    resultFolderLocation);
        }

        ReportGenerator reportGenerator = new ReportGenerator(dependencyBlacklistCollector.getDependencyBlacklist(),
                projectOperator.getTotalJavaClassesScanned(), mtaIssuesCollector.getMtaIssuesList(),
                reflectionUsageCollector.getReflectionUsageInProject(),
                reflectionUsageCollector.getReflectionUsageInDependencies(), dependencyTreeOperator.getDependencyTreeRootNodes(),
                projectPomLocation, resultFolderLocation, withoutReflectionUsageOfDependencies);
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

        return "This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus.\n" +
                "After various analyses, a decision aid is created that estimates the effort and identifies tasks.\n" +
                "Currently only Maven is supported as build tool.\n\n" +
                "For a better result the project to be analyzed must be built (mvn package).\n" +
                "Otherwise the dependencies are not available in the local Maven repository.\n\n" +

                "Options:\n" +
                "-p  --project                Maven project location\n" +
                "-a  --app                    Application entry point location (@SpringBootApplication)\n" +
                "-m  --mavenRepo              Local Maven repository location\n" +
                "-wd --withoutDependencies    Without analysis of the reflection usage of the dependencies. This analysis can take a very long time\n" +
                "-v  --verbose                Enable debug logging\n" +
                "-h  --help                   Display help";
    }
}