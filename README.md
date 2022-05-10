# Migration Helper Spring Quarkus

This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus. After various analyses, a decision aid is created that estimates the effort and identifies tasks. Currently only Maven is supported as build tool.

## Prerequisites

- Java 11

## Build

**Windows:** \
`mvnw.cmd package appassembler:assemble`

**Linux:** \
`./mvnw package appassembler:assemble`

## Run

**Windows:** \
`target\appassembler\bin\app.bat -f [filepath]`

**Linux:** \
`./target/appassembler/bin/app -f [filepath]`

## Options
```
-f --file     jar or war file location
-h --help     display help
```
