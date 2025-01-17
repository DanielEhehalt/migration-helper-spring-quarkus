package com.devonfw.qmaid.collector;

import com.devonfw.qmaid.model.AnalysisFailureEntry;

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
