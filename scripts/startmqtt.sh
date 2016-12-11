#!/bin/bash
. /etc/environment
. /etc/grassroot

PID=`ps -eaf | grep activemq | grep -v grep | awk '{print $2}'`

if [[ "" !=  "$PID" ]]; then
  echo "Found ActiveMQ running, not starting"
else
  echo "ActiveMQ not running, starting it up"
  ${MQTT_PATH}/activemq start
fi