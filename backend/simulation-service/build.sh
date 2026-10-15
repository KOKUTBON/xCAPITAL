#!/usr/bin/env sh
set -e
docker run --rm -v "$(pwd)":/ws -w /ws maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package
