# QMAid Quarkus Migration Aid

This CLI tool analyzes Spring Boot microservices for their migratability to Quarkus. Based on various analyses of the source code of a given Spring Boot project, a decision aid is generated. This report highlights the challenges of a migration. These include detecting incompatible dependencies and detecting reflection in the application source code and in the dependencies. This will help estimate the effort required for a migration and identify tasks for performing a migration.

## Prerequisites

- Java 11
- Maven

## Build

**Windows:** \
`mvnw.cmd package appassembler:assemble`

**Linux:** \
`./mvnw package appassembler:assemble`

## Run
Project which should be analyzed must be built (mvn package) once before, so that the dependencies are available in the local Maven repository.
The CLI options ```-p```, ```-a``` and ```-m``` are mandatory.

**Windows:** (For long paths Git Bash recommended because the input length of Windows cmd is limited. In Git Bash Linux Syntax is required) <br/>
`target\appassembler\bin\qmaid.bat -p [projectLocation] -a [appEntryPointFileLocation] -m [mavenRepoLocation]`

**Linux:** \
`./target/appassembler/bin/qmaid -p [projectLocation] -a [appEntryPointLocation] -m [mavenRepoLocation]`

**Examples:** \
Windows: <br/>
`target\appassembler\bin\qmaid.bat -p C:\Projects\spring-boot-project -a C:\Projects\spring-boot-project\src\main\java\com\example\app\SpringBootApplication.java -m C:\Projects\conf\.m2\repository`

Linux: <br/>
`./target/appassembler/bin/qmaid -p /home/test-microservice -a /home/spring-boot-project/src/main/java/com/example/app/SpringBootApplication.java -m /home/.m2/repository`

Modulith: (Given project -p must be the module that contains the @SpringBootApplication class) <br/>
`./target/appassembler/bin/qmaid -p /home/test-microservice/main-module -a /home/spring-boot-project/main-module/src/main/java/com/example/app/SpringBootApplication.java -m /home/.m2/repository`

## Options
```
-p  --project               Maven project location
-a  --app                   Application entry point file location (@SpringBootApplication)
-m  --mavenRepo             Maven repository location
-wd --withoutDependencies   Without analysis of the reflection usage of the dependencies. This analysis can take a very long time
-v  --verbose               Enable debug logging
-h  --help                  Display help
```

## Understanding results
The generated report ist available in the `results` directory.
This information is also available in the report, by pressing the `Info` buttons.

**General issues:** \
The issues listed below are general issues and are not assignable to a specific dependency. The rule-based analysis also detects configuration issues or parent POMs. In these cases, only the description of the triggered rule is displayed

**Blacklisted Dependencies:** \
The blacklisted dependencies list contains all Maven dependencies that have been identified as incompatible. The incompatibility is based on the Quarkus ruleset of the Migration Toolkit for Applications and on extended user-defined rules, e.g. for devon4j. Based on the dependencies of the project, transitive dependencies are also analyzed. Since dependencies are only recognized if rules exist for them, this list may be incomplete.

**Occurrence in Java classes:** \
The measurement of the occurrence indicates the strength of the binding between the dependency and the code. For this purpose, all import statements of all Java classes of the project are analyzed. All possible packages and classes of a dependency are collected for the mapping. For direct dependencies, the packages and classes of the transitive dependencies are also collected. If a class or a package of a dependency is found in an import statement in a class of the user code, the counter is incremented once per class. For a better evaluation, the number of all scanned Java classes is also displayed. The adjacent button displays all classes determined in this way. For better traceability, all import statements that were responsible for the assignment are also listed for the classes.

**Reflection usage in project:** \
For determining the reflection usage in the code of the project, all import statements are checked for the occurrence of the java.lang.reflect package. If a class is found that imports the reflection API, it is displayed below.

**Reflection usage in dependencies as dependency tree:** \
For determining the reflection usage in the dependencies, all import statements of a dependency are checked for the occurrence of the java.lang.reflect package. The number of classes that imports the reflection API are shown in the brackets. The names of the identified classes can be displayed using the button next to it. To shorten the tree, duplicate entries are marked with an asterisk and are not further executed.

**Analysis Failures:** \
To ensure the stability of the execution of the analysis, exceptions are just collected and displayed below. For a more detailed failure analysis the use of the --verbose argument is recommended.

## Customization

The analysis is mainly based on a rule-based analysis which is performed using the Red Hat Migration Toolkit for Applications. This makes it easy to extend the analysis with new requirements. To add rules, create a folder under tools/custom-mta-rules and enter rules using the windup rules syntax.

Important: To collect dependency and project issues, you must set the metadata property `<targetTechnology id="quarkus"/>` and the element attribute `category-id="mandatory"`. Other targets and categories are currently ignored. The element attribute `title` is used for descriptions.

More information on creating windup rules:
https://access.redhat.com/documentation/en-us/red_hat_jboss_migration_toolkit/3.0/html-single/windup_rules_development_guide/index#creating_xml_rules

## Current limitations

- Only Maven is supported as build tool
- Paths must not have blanks
- The analysis of projects with multiple modules is possible with limitations. When specifying the project location `-p`, the folder of the module containing the application entry class must be specified (see example). Analysis is then performed only for this module. An analysis for the other modules of the application is not possible. A identification of the Java version and the project name is also not possible in this mode
