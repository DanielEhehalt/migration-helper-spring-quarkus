# QMaid Quarkus Migration Aid

This migration helper analyzes Spring Boot microservices in terms of migration capability to Quarkus. After various analyses, a decision aid is created that estimates the effort and identifies tasks. Currently, only Maven is supported as build tool. This tool focuses on microservices. The analysis of projects with multiple modules is possible with limitations. When specifying the project location, the folder of the module containing the application entry class must be specified.

## Prerequisites

- Java 11
- Maven

## Build

**Windows:** \
`mvnw.cmd package appassembler:assemble`

**Linux:** \
`./mvnw package appassembler:assemble`

## Run
For a better result the project to be analyzed must be built (mvn package). Otherwise, the dependencies are not available in the local Maven repository.
The CLI options ```-p```, ```-a``` and ```-m``` are mandatory.

**Windows:** \
`target\appassembler\bin\mhsq.bat -p [projectLocation] -a [appEntryPoint] -m [mavenRepoLocation]`

**Linux:** \
`./target/appassembler/bin/mhsq -p [projectLocation] -a [appEntryPoint] -m [mavenRepoLocation]`

## Customization

The analysis is mainly based on a rule-based analysis which is performed using the Red Hat Migration Toolkit for Applications. This makes it easy to extend the analysis with new requirements. To add rules, create a folder under tools/custom-mta-rules and enter rules using the windup rules syntax.

Important: To collect dependency and project issues, you must set the metadata property ```<targetTechnology id="quarkus"/>``` and the element attribute ```category-id="mandatory"```. Other targets and categories are currently ignored. The element attribute ```title``` is used for descriptions.

More information on creating windup rules:
https://access.redhat.com/documentation/en-us/red_hat_jboss_migration_toolkit/3.0/html-single/windup_rules_development_guide/index#creating_xml_rules

## Options
```
-p  --project               Maven project location
-a  --app                   Application entry point location (@SpringBootApplication)
-m  --mavenRepo             Maven repository location
-wd --withoutDependencies   Without analysis of the reflection usage of the dependencies. This analysis can take a very long time
-v  --verbose               Enable debug logging
-h  --help                  Display help
```
