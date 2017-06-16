#!/bin/bash
. /etc/environment
. /etc/grassroot

CURR=$PWD
cd $GRASSROOT_HOME
mkdir -p $GRASSROOT_HOME/log
./scripts/startmqtt.sh
nohup java \
    -Dapp.home="$GRASSROOT_HOME" \
    -Dspring.profiles.active=$PROFILE \
    -jar $GRASSROOT_HOME/grassroot-webapp/build/libs/grassroot-webapp-1.0.0.RC1.jar \
    `cat /home/ubuntu/cmd_line_arguments` > $GRASSROOT_HOME/log/grassroot-app.log 2>&1 &
echo $! > $GRASSROOT_HOME/.pid
sleep 1
chgrp sudo $GRASSROOT_HOME/log/grassroot-app.log
chmod 640 $GRASSROOT_HOME/log/grassroot-app.log
cd $CURR
