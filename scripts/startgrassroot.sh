#!/bin/bash
#service apache2 start > /var/log/startapache.out 2>&1
#daemon  -F /var/grassroot/grass_root_pid `cat /etc/grassroot` java -Dspring.profiles.active=localpg -jar ./grassroot-webapp/target/grassroot-webapp-1.0-SNAPSHOT.jar --server.ssl.keyStore=keystore.jks
. /etc/environment
. /etc/grassroot
CURR=$PWD
cd /var/grassroot
nohup java -Dspring.profiles.active=production -jar grassroot-webapp/target/grassroot-webapp-1.0-SNAPSHOT.jar > grassroot-app.log 2>&1 &
echo $! > .pid
cd $CURR
