package com.devonfw.application.util;

import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.AnalysisFailureEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Executes the migration toolkit for applications cli
 */
public class MtaExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MtaExecutor.class);

    /**
     * Runs the Red Hat Migration Toolkit for Applications (MTA) to find incompatible dependencies.
     * The result of the analysis is temporarily saved in the results folder
     *
     * @param projectLocation Path to the jar/ war file which should be analyzed
     * @param resultPath Path to the directory where the results will be saved
     * @return If execution was successful
     */
    public static boolean executeMtaForProject(String projectLocation, String resultPath) {

        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.directory(new File(System.getProperty("user.dir")));

        if (isWindows) {
            builder.command("tools\\mta-cli-5.2.1\\bin\\mta-cli.bat", "--input", projectLocation, "--output", resultPath, "--target", "quarkus", "--target", "reflection", "--exportCSV", "--batchMode", "--skipReports", "--sourceMode", "--userRulesDirectory", "tools\\custom-mta-rules");
        } else {
            builder.command("./tools/mta-cli-5.2.1/bin/mta-cli", "--input", projectLocation, "--output", resultPath, "--target", "quarkus", "--exportCSV", "--batchMode");
        }

        try {
            //Start script
            LOG.info("Analyzing project... This can take a while");
            Process process = builder.start();

            //Log script output
            ScriptOutputStreamParser scriptOutputStreamParser = new ScriptOutputStreamParser(process.getInputStream(), LOG::debug);
            Executors.newSingleThreadExecutor().submit(scriptOutputStreamParser);

            //Waiting for successful end of execution
            int exitCode = process.waitFor();
            assert exitCode == 0;
            return true;
        } catch (IOException | InterruptedException e) {
            LOG.error("MTA execution failed.", e);
            return false;
        }
    }

    public static boolean executeMtaToFindReflectionInLibrary(String projectLocation, String resultPath) {

        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.directory(new File(System.getProperty("user.dir")));

        builder.command("tools\\mta-cli-5.2.1\\bin\\mta-cli.bat", "--input", projectLocation, "--output", resultPath, "--target", "reflection", "--exportCSV", "--batchMode", "--skipReports", "--userRulesDirectory", "tools\\custom-mta-rules");

        try {
            //Start script
            LOG.info("Analyze reflection usage in project dependency: " + projectLocation);
            Process process = builder.start();

            //Log script output
            ScriptOutputStreamParser scriptOutputStreamParser = new ScriptOutputStreamParser(process.getInputStream(), LOG::debug);
            Executors.newSingleThreadExecutor().submit(scriptOutputStreamParser);

            //Waiting for successful end of execution
            int exitCode = process.waitFor();
            assert exitCode == 0;
            return true;
        } catch (IOException | InterruptedException e) {
            AnalysisFailureCollector.addAnalysisFailure(new AnalysisFailureEntry(projectLocation, "MTA reflection analysis failed."));
            LOG.debug("MTA reflection analysis failed.", e);
            return false;
        }
    }

    /**
     * Processing output of a script execution
     */
    static class ScriptOutputStreamParser implements Runnable {

        private final InputStream inputStream;
        private final Consumer<String> consumer;

        public ScriptOutputStreamParser(InputStream inputStream, Consumer<String> consumer) {

            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }
}
