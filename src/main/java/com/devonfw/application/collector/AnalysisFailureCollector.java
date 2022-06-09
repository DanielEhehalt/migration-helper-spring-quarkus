package com.devonfw.application.collector;

import com.devonfw.application.model.AnalysisFailureEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects all analysis failures
 */
public class AnalysisFailureCollector {

    private static List<AnalysisFailureEntry> analysisFailures = new ArrayList<>();

    public static void addAnalysisFailure(AnalysisFailureEntry analysisFailure) {

        analysisFailures.add(analysisFailure);
    }

    public static List<AnalysisFailureEntry> getAnalysisFailures() {

        return analysisFailures;
    }
}
