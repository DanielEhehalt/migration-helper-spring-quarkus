package com.devonfw.application;

import com.devonfw.application.analyzer.Analyzer;
import net.sf.mmm.code.impl.java.JavaContext;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * TODO dehehalt This type ...
 */
public class Application {

    public static void main(String args[]) {

        JavaContext javaContext = Analyzer.getJavaContext(
                Path.of("C:/Projects/test-project/workspaces/main/migration-helper-spring-quarkus/src/main/resources/Testapp/jtqj/core/src/main/java/com/devonfw/application/jtqj/visitormanagement/logic/impl/VisitormanagementImpl.java"),
                Path.of("C:/Projects/test-project/workspaces/main/migration-helper-spring-quarkus/src/main/resources/Testapp/jtqj/core"),
                Path.of("C:/Projects/test-project/conf/.m2/repository"));

        Package[] packages = javaContext.getClassLoader().getDefinedPackages();
        System.out.println(Arrays.toString(packages));
    }

}
