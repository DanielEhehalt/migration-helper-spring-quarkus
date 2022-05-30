# Migration Helper Spring Quarkus

This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus. After various analyses, a decision aid is created that estimates the effort and identifies tasks. Currently only Maven is supported as build tool.

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
`target\appassembler\bin\app.bat -f [projectLocation] -m [mavenRepoLocation]`

**Linux:** \
`./target/appassembler/bin/app -f [projectLocation] -m [mavenRepoLocation]`

## Options
```
-f --file       jar or war file location
-m --mavenRepo  maven repository location
-h --help       display help
```
