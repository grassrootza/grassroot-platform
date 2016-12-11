#!/bin/bash
. /etc/environment
. /etc/grassroot

PID=`ps -eaf | grep activemq | grep -v grep | awk '{print $2}'`

if [[ "" !=  "$PID" ]]; then
  echo "Found ActiveMQ running, stopping"
  ${MQTT_PATH}/activemq stop
else
  echo "ActiveMQ not running, not bothering"
fi