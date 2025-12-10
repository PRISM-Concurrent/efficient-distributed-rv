#!/bin/bash
cd "$(dirname "$0")"
mvn compile -q
java -cp "target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)" Test
