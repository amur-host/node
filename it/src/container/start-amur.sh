#!/bin/bash

trap 'kill -TERM $PID' TERM INT
echo Options: $AMUR_OPTS
java $AMUR_OPTS -jar /opt/amur/amur.jar /opt/amur/template.conf &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EXIT_STATUS=$?
