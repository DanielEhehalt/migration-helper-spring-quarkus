package com.devonfw.application.util;

import com.devonfw.application.analyzer.PomAnalyzer;
import com.devonfw.application.collector.AnalysisFailureCollector;
import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.model.ReflectionUsageEntry;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

/**
 * Generates HTML report
 */
public class ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ReportGenerator.class);

    /**
     * Generates HTML report
     * @param blacklist Blacklisted Dependencies
     * @param reflectionUsageInProject List of reflection usage in the project
     * @param reflectionUsageInDependencies List of reflection usage in the project dependencies
     * @param locationOfPom Location of project POM
     * @param resultPath Path to the directory where the report will be saved
     */
    public static void generateReport(List<BlacklistEntry> blacklist, List<ReflectionUsageEntry> reflectionUsageInProject, List<ReflectionUsageEntry> reflectionUsageInDependencies, String locationOfPom, String resultPath) {

        LOG.info("Generate report.html");

        //Configuration for template location under src/main/resources
        Properties properties = new Properties();
        properties.setProperty("resource.loaders", "class");
        properties.setProperty("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        //Init Velocity
        Velocity.init(properties);
        VelocityContext context = new VelocityContext();
        Template template = Velocity.getTemplate("report-template.vm");

        //Insert dynamic values
        context.put("projectName", PomAnalyzer.getNameAndVersionFromProject(locationOfPom));
        context.put("javaVersion", PomAnalyzer.getJavaVersionFromProject(locationOfPom));
        context.put("blacklist", blacklist.iterator());
        context.put("blacklistSize", blacklist.size());
        context.put("reflectionUsageInProjectList", reflectionUsageInProject.iterator());
        context.put("reflectionUsageInProjectListSize", reflectionUsageInProject.size());
        context.put("reflectionUsageInDependenciesList", reflectionUsageInDependencies.iterator());
        context.put("reflectionUsageInDependenciesListSize", reflectionUsageInDependencies.size());
        context.put("analysisFailuresList", AnalysisFailureCollector.getAnalysisFailures());
        context.put("analysisFailuresListSize", AnalysisFailureCollector.getAnalysisFailures().size());

        //Merge template
        StringWriter sw = new StringWriter();
        template.merge(context, sw);

        //Generate html
        try {
            FileWriter fw = new FileWriter(resultPath + "/report.html");
            fw.write(sw.toString());
            fw.close();
        } catch (IOException e) {
            LOG.error("Could not generate report.", e);
        }
    }
}
