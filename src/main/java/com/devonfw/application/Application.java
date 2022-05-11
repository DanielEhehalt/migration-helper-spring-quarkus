package com.devonfw.application;

import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.utils.AnalyzerUtils;
import com.devonfw.application.utils.CommandLineUtils;
import com.devonfw.application.utils.ReportGenerator;
import com.devonfw.application.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.util.List;

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
    private String filepath;

    /**
     * Main method. Initiates CLI
     */
    public static void main(String[] args) {

        new CommandLine(new Application()).execute(args);

/*        JavaContext javaContext = Analyzer.getJavaContext(
                Path.of("C:/Projects/test-project/workspaces/main/migration-helper-spring-quarkus/src/main/resources/Testapp/jtqj/core/src/main/java/com/devonfw/application/jtqj/visitormanagement/logic/impl/VisitormanagementImpl.java"),
                Path.of("C:/Projects/test-project/workspaces/main/migration-helper-spring-quarkus/src/main/resources/Testapp/jtqj/core"),
                Path.of("C:/Projects/test-project/conf/.m2/repository"));

        Package[] packages = javaContext.getClassLoader().getDefinedPackages();
        System.out.println(Arrays.toString(packages));*/
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
        else if (!filepath.equals("")) {
            //Check if file exists
            File file = new File(filepath);
            if (!file.exists()) {
                System.out.println("Error: File does not exist");
                System.exit(1);
            }

            //Create directory for results
            String resultPath = Utils.createDirectoryForResults();

            System.out.println("Start the analysis");
            System.out.println("Filepath: " + filepath);
            System.out.println("Result folder: " + resultPath);

            //Execute MTA
            boolean execution = AnalyzerUtils.executeMTA(filepath, resultPath);

            //Generate list of blacklisted packages
            List<List<String>> csvOutput = Utils.parseCSV(resultPath);
            List<BlacklistEntry> blacklist = Utils.convertCSVOutputToBlacklist(csvOutput);
            ReportGenerator.generateReport(blacklist, resultPath);

            //Analyse usage of blacklisted packages
            /*TODO: Analyse usage of blacklisted packages*/

            //Analyse usage of reflection
            /*TODO: Analyse usage of reflection*/

            //Exit
            System.exit(0);
        } else {
            System.out.println("No arguments");
            System.exit(255);
        }
    }
}