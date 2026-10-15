#!/usr/bin/env sh
set -e
# Build with Maven in a container so you don't need Maven locally.
docker run --rm -v "$(pwd)":/ws -w /ws maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package
