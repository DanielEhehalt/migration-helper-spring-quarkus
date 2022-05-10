# migration-helper-spring-quarkus

This migration helper analyzes Spring Boot projects in terms of migration capability to Quarkus. After various analyses, a decision aid is created that estimates the effort and identifies tasks. Currently only Maven is supported as build tool.

#Prerequisites
Java 11

#Build
mvn package appassembler:assemble

#Run
Windows: \
target\appassembler\bin\app.bat -f filepath

Linux: \
./target/appassembler/bin/app -f filepath