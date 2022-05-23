package com.devonfw.application;

import com.devonfw.application.analyzer.Analyzer;
import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.model.ReflectionUsageEntry;
import com.devonfw.application.utils.AnalyzerUtils;
import com.devonfw.application.utils.CommandLineUtils;
import com.devonfw.application.utils.ReportGenerator;
import com.devonfw.application.utils.Utils;
import net.sf.mmm.code.impl.java.JavaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
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
            List<BlacklistEntry> blacklist = Utils.generateBlacklist(csvOutput);
            List<ReflectionUsageEntry> reflectionUsage = Utils.generateReflectionUsageList(csvOutput);

            //Analyse usage of blacklisted packages
            //TODO: Analyse usage of blacklisted packages

            //Analyse usage of reflection
            //TODO: Analyse usage of reflection

            //Generate report
            ReportGenerator.generateReport(blacklist, reflectionUsage, resultPath);

            //Exit
            System.exit(0);
        } else {
            System.out.println("No arguments");
            System.exit(255);
        }
    }
}