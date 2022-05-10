package com.devonfw.application.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * TODO dehehalt This type ...
 */
public class CommandLineUtils {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineUtils.class);

    public static String printHelp() {

        return "This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus. \n " +
                "After various analyses, a decision aid is created that estimates the effort and identifies tasks \n " +
                "Currently only Maven is supported as build tool. \n\n " +
                "Options: \n" +
                "-f     jar or war file location \n" +
                "-h	    help \n";
    }

    public static boolean executeMTA(String filepath, String resultPath) {

        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.directory(new File(System.getProperty("user.dir")));

        if (isWindows) {
            builder.command("tools\\mta-cli-5.2.1\\bin\\mta-cli.bat", "--input", filepath, "--output", resultPath, "--target", "quarkus", "--sourceMode", "--exportCSV");
        } else {
            builder.command("./tools/mta-cli-5.2.1/bin/mta-cli", "--input", filepath, "--output", "result", "--target", "quarkus", "--sourceMode", "--exportCSV");
        }

        try {
            System.out.println("Using MTA... This can take a while");
            Process process = builder.start();
            StreamParser streamParser = new StreamParser(process.getInputStream(), LOG::debug);
            Executors.newSingleThreadExecutor().submit(streamParser);
            int exitCode = process.waitFor();
            assert exitCode == 0;
            System.out.println("MTA execution successful");
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

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
