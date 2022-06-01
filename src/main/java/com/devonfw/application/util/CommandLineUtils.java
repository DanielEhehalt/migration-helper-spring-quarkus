package com.devonfw.application.util;

/**
 * Contains utility functions for processing the analysis steps and for processing the CLI options
 */
public class CommandLineUtils {

    /**
     * Returns the help text for the --help argument
     *
     * @return The help text
     */
    public static String printHelp() {

        return "This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus.\n " +
                "After various analyses, a decision aid is created that estimates the effort and identifies tasks.\n " +
                "Currently only Maven is supported as build tool.\n\n " +
                "Options:\n" +
                "-p --project                Maven project location\n" +
                "-a --app                    Application entry point location (@SpringBootApplication)\n" +
                "-m --mavenRepo              Maven repository location\n" +
                "-w --withoutDependencies    Without analysis of the reflection usage of the dependencies. This analysis can take a very long time\n" +
                "-v --verbose                Enable debug logging\n" +
                "-h --help                   Display help";
    }
}