# Migration Helper Spring Quarkus

This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus. After various analyses, a decision aid is created that estimates the effort and identifies tasks. Currently, only Maven is supported as build tool.

## Prerequisites

- Java 11
- Maven

## Build

**Windows:** \
`mvnw.cmd package appassembler:assemble`

**Linux:** \
`./mvnw package appassembler:assemble`

## Run

**Windows:** \
`target\appassembler\bin\app.bat -p [projectLocation] -a [appEntryPoint] -m [mavenRepoLocation]`

**Linux:** \
`./target/appassembler/bin/app -p [projectLocation] -a [appEntryPoint] -m [mavenRepoLocation]`

## Options
```
-p --project                Maven project location
-a --app                    Application entry point location (@SpringBootApplication)
-m --mavenRepo              Maven repository location
-wd --withoutDependencies   Without analysis of the reflection usage of the dependencies. This analysis can take a very long time
-v --verbose                Enable debug logging
-h --help                   Display help
```
