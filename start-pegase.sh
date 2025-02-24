#!/usr/bin/env bash

ABSOLUTE_SCRIPT_PATH="$(readlink -f `dirname $0`)"
export JDK_JAVA_OPTIONS="--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED"
docker-compose -f $ABSOLUTE_SCRIPT_PATH/pegase-docker-compose.yml up --build