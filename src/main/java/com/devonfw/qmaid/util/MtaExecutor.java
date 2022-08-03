package com.devonfw.qmaid.util;

import com.devonfw.qmaid.collector.AnalysisFailureCollector;
import com.devonfw.qmaid.model.AnalysisFailureEntry;
import org.apache.commons.lang3.SystemUtils;
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
     * This method runs the Red Hat Migration Toolkit for Applications (MTA) to find incompatible dependencies. The result of the analysis is
     * temporarily saved in the results folder
     *
     * @param inputProjectLocation  Path to the project which should be analyzed
     * @param resultFolderLocation  Path to the directory where the results will be saved
     */
    public static void executeMtaForProject(File inputProjectLocation, File resultFolderLocation) {

        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.directory(new File(System.getProperty("user.dir")));

        if(SystemUtils.IS_OS_WINDOWS) {
            builder.command("tools" + File.separator + "mta-cli-5.2.1" + File.separator + "bin" + File.separator + "mta-cli.bat",
                    "--input", inputProjectLocation.toString(),
                    "--output", resultFolderLocation.toString(),
                    "--target", "quarkus",
                    "--target", "reflection",
                    "--exportCSV",
                    "--batchMode",
                    "--skipReports",
                    "--overwrite",
                    "--sourceMode",
                    "--userRulesDirectory", "tools" + File.separator + "custom-mta-rules");
        } else {
            builder.command("tools" + File.separator + "mta-cli-5.2.1" + File.separator + "bin" + File.separator + "mta-cli",
                    "--input", inputProjectLocation.toString(),
                    "--output", resultFolderLocation.toString(),
                    "--target", "quarkus",
                    "--target", "reflection",
                    "--exportCSV",
                    "--batchMode",
                    "--skipReports",
                    "--overwrite",
                    "--sourceMode",
                    "--userRulesDirectory", "tools" + File.separator + "custom-mta-rules");
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
        } catch (IOException | InterruptedException e) {
            LOG.error("MTA execution failed.", e);
        }
    }

    /**
     * This method runs the Red Hat Migration Toolkit for Applications (MTA) to find reflection calls and migration issues in a library
     *
     * @param libraryLocation Path to the jar file which should be analyzed
     * @param resultFolderLocation      Path to the directory where the results will be saved
     */
    public static void executeMtaForLibrary(File libraryLocation, File resultFolderLocation) {

        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.directory(new File(System.getProperty("user.dir")));

        if(SystemUtils.IS_OS_WINDOWS) {
            builder.command("tools" + File.separator + "mta-cli-5.2.1" + File.separator + "bin" + File.separator + "mta-cli.bat",
                    "--input", libraryLocation.toString(),
                    "--output", resultFolderLocation.toString(),
                    "--target", "quarkus",
                    "--target", "reflection",
                    "--exportCSV",
                    "--batchMode",
                    "--skipReports",
                    "--overwrite",
                    "--userRulesDirectory", "tools" + File.separator + "custom-mta-rules");
        } else {
            builder.command("tools" + File.separator + "mta-cli-5.2.1" + File.separator + "bin" + File.separator + "mta-cli",
                    "--input", libraryLocation.toString(),
                    "--output", resultFolderLocation.toString(),
                    "--target", "quarkus",
                    "--target", "reflection",
                    "--exportCSV",
                    "--batchMode",
                    "--skipReports",
                    "--overwrite",
                    "--userRulesDirectory", "tools" + File.separator + "custom-mta-rules");
        }

        try {
            //Start script
            LOG.info("Analyze reflection usage in project dependency: " + libraryLocation);
            Process process = builder.start();

            //Log script output
            ScriptOutputStreamParser scriptOutputStreamParser = new ScriptOutputStreamParser(process.getInputStream(), LOG::debug);
            Executors.newSingleThreadExecutor().submit(scriptOutputStreamParser);

            //Waiting for successful end of execution
            int exitCode = process.waitFor();
            assert exitCode == 0;
        } catch (IOException | InterruptedException e) {
            AnalysisFailureCollector.addAnalysisFailure(
                    new AnalysisFailureEntry(libraryLocation.toString(), "MTA reflection analysis failed."));
            LOG.debug("MTA reflection analysis failed.", e);
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
