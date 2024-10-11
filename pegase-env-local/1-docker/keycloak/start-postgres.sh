#!/usr/bin/env bash

ABSOLUTE_SCRIPT_PATH="$(readlink -f `dirname $0`)"
docker-compose -f $ABSOLUTE_SCRIPT_PATH/postgres-docker-compose.yml up --remove-orphans

