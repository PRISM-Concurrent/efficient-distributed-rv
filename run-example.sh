#!/bin/bash

# Move to the directory of this script
cd "$(dirname "$0")"

# Check if a class name was provided
if [ -z "$1" ]; then
  echo "Usage: $0 <MainClassName>"
  exit 1
fi

MAIN_CLASS=$1

# Build classpath and run
java -cp "target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)" "$MAIN_CLASS"