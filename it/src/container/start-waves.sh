#!/bin/bash

trap 'kill -TERM $PID' TERM INT
echo Options: $WAVES_OPTS
java $WAVES_OPTS -jar /opt/amur/amur.jar /opt/amur/template.conf &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EXIT_STATUS=$?
