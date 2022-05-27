package com.devonfw.application.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains utility functions for processing the analysis steps and for processing the CLI options
 */
public class CommandLineUtils {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineUtils.class);

    /**
     * Returns the help text for the --help argument
     *
     * @return The help text
     */
    public static String printHelp() {

        return "This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus. \n " +
                "After various analyses, a decision aid is created that estimates the effort and identifies tasks \n " +
                "Currently only Maven is supported as build tool. \n\n " +
                "Options: \n" +
                "-f --file      jar or war file location \n" +
                "-h --help      help \n";
    }
}