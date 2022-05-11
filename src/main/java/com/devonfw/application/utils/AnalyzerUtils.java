package com.devonfw.application.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AnalyzerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyzerUtils.class);

    /**
     * Runs the Red Hat Migration Toolkit for Applications (MTA) to find incompatible dependencies.
     * The result of the analysis is temporarily saved in the results folder
     * @param filepath Path to the jar/ war file which should be analyzed
     * @param resultPath Path to the directory where the results will be saved
     * @return If execution was successful
     */
    public static boolean executeMTA(String filepath, String resultPath) {

        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.directory(new File(System.getProperty("user.dir")));

        if (isWindows) {
            builder.command("tools\\mta-cli-5.2.1\\bin\\mta-cli.bat", "--input", filepath, "--output", resultPath, "--target", "quarkus", "--exportCSV", "--batchMode", "--skipReports");
        } else {
            builder.command("./tools/mta-cli-5.2.1/bin/mta-cli", "--input", filepath, "--output", resultPath, "--target", "quarkus", "--exportCSV", "--batchMode");
        }

        try {
            //Start script
            System.out.println("Using MTA... This can take a while");
            Process process = builder.start();

            //Log script output
            StreamParser streamParser = new StreamParser(process.getInputStream(), System.out::println);
            Executors.newSingleThreadExecutor().submit(streamParser);

            //Waiting for successful end of execution
            int exitCode = process.waitFor();
            assert exitCode == 0;
            System.out.println("MTA execution successful");
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Inner class for processing script output
     */
    private static class StreamParser implements Runnable {

        private final InputStream inputStream;
        private final Consumer<String> consumer;

        public StreamParser(InputStream inputStream, Consumer<String> consumer) {

            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }
}
