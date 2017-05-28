#!/bin/bash
. /etc/environment
. /etc/grassroot

CURR=$PWD
cd /var/grassroot
./scripts/startmqtt.sh
nohup java  -Dspring.profiles.active=$PROFILE -jar grassroot-webapp/build/libs/grassroot-webapp-1.0.0.RC1.jar  `cat /home/ubuntu/cmd_line_arguments` > grassroot-app.log 2>&1 &
echo $! > .pid
sleep 1
chgrp sudo /var/grassroot/grassroot-app.log
chmod 640 /var/grassroot/grassroot-app.log
cd $CURR