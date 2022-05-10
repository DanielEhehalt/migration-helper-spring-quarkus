package com.devonfw.application;

import com.devonfw.application.cli.CommandLineUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.time.Instant;

/**
 * TODO dehehalt This type ...
 */

@CommandLine.Command(
        name = "MigrationHelper",
        description = "Helps you"
)
public class Application implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @CommandLine.Option(names = {"-h", "--help"}, defaultValue = "false", description = "Help")
    private Boolean help;

    @CommandLine.Option(names = {"-f", "--file"}, description = "Filepath")
    private String filepath;

    public static void main(String args[]) {

        new CommandLine(new Application()).execute(args);

/*        JavaContext javaContext = Analyzer.getJavaContext(
                Path.of("C:/Projects/test-project/workspaces/main/migration-helper-spring-quarkus/src/main/resources/Testapp/jtqj/core/src/main/java/com/devonfw/application/jtqj/visitormanagement/logic/impl/VisitormanagementImpl.java"),
                Path.of("C:/Projects/test-project/workspaces/main/migration-helper-spring-quarkus/src/main/resources/Testapp/jtqj/core"),
                Path.of("C:/Projects/test-project/conf/.m2/repository"));

        Package[] packages = javaContext.getClassLoader().getDefinedPackages();
        System.out.println(Arrays.toString(packages));*/
    }

    @Override
    public void run() {

        //Print Help
        if (help) {
            System.out.println(CommandLineUtils.printHelp());
            System.exit(0);
        }

        //Execute analysis
        if (!filepath.equals("")) {
            String resultPath = createDirectoryForResults();
            System.out.println("Start the analysis");
            System.out.println("Filepath: " + filepath);
            System.out.println("Result folder: " + resultPath);

            //Execute MTA
            boolean execution = CommandLineUtils.executeMTA(filepath, resultPath);

            //Generate package blacklist
            /*TODO: Generate package blacklist*/

            //Analyse usage of blacklisted packages
            /*TODO: Analyse usage of blacklisted packages*/

            //Analyse usage of reflection
            /*TODO: Analyse usage of reflection*/

            //Exit
            System.exit(0);
        }
    }

    public String createDirectoryForResults() {

        Instant instant = Instant.now();
        long timeStampMillis = instant.toEpochMilli();
        String resultPath = System.getProperty("user.dir") + "\\results\\" + timeStampMillis;
        boolean mkdir = new File(resultPath).mkdirs();
        return resultPath;
    }
}
