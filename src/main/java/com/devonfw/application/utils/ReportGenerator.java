package com.devonfw.application.utils;

import com.devonfw.application.analyzer.PomAnalyzer;
import com.devonfw.application.model.BlacklistEntry;
import com.devonfw.application.model.ReflectionUsageEntry;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Generates HTML report
 */
public class ReportGenerator {

    /**
     * Generates HTML report
     *
     * @param blacklist Blacklisted Dependencies
     * @param resultPath Path to the directory where the results will be saved
     */
    public static void generateReport(List<BlacklistEntry> blacklist, List<ReflectionUsageEntry> reflectionUsage, Path inputProject, String resultPath) {

        //Configuration for template location under src/main/resources
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "class");
        properties.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        //Init Velocity
        Velocity.init(properties);
        VelocityContext context = new VelocityContext();
        Template template = Velocity.getTemplate("report-template.vm");

        //Insert dynamic values
        context.put("blacklist", blacklist.iterator());
        context.put("reflectionUsageList", reflectionUsage.iterator());
        context.put("projectName", PomAnalyzer.getNameAndVersionFromProject(inputProject));
        context.put("javaVersion", PomAnalyzer.getJavaVersionFromProject(inputProject));

        //Merge template
        StringWriter sw = new StringWriter();
        template.merge(context, sw);

        //Generate html
        try {
            FileWriter fw = new FileWriter(resultPath + "/report.html");
            fw.write(sw.toString());
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}