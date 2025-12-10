#!/bin/bash
# Run with optimized async logging configuration

export JAVA_OPTS="-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
                  -Dlog4j2.asyncLoggerRingBufferSize=262144 \
                  -Dlog4j2.asyncLoggerWaitStrategy=Block \
                  -Dlog4j.configurationFile=log4j2-async.xml \
                  -XX:+UseG1GC \
                  -XX:MaxGCPauseMillis=200 \
                  -Xms2g -Xmx4g"

mvn exec:java -Dexec.mainClass="phd.distributed.App" $@
