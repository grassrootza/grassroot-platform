#!/bin/bash

# SET ENVIRONMENT VARIABLES BASED ON THE ENVIRONMENT PREVIOUSLY SETUP VIA
. /usr/src/grassroot/environment/environment-variables

wget http://s3.amazonaws.com/ec2metadata/ec2-metadata
chmod 777 ec2-metadata
INSTANCE_ID=$(./ec2-metadata -i | cut -b 14-)
HOSTNAME=$PROFILE$INSTANCE_ID
echo $HOSTNAME > /etc/hostname

# SETUP PAPERTRAIL
wget -qO - --header="X-Papertrail-Token: zZJTlxJZTvV5r18UunSF" https://papertrailapp.com/destinations/6213432/setup.sh | bash
wget https://github.com/papertrail/remote_syslog2/releases/download/v0.19/remote-syslog2_0.19_amd64.deb
dpkg -i remote-syslog2_0.19_amd64.deb
remote_syslog -p 48972 -d logs6.papertrailapp.com --pid-file=/var/run/remote_syslog.pid  /usr/src/grassroot/log/grassroot-app.log

# START GRASSROOT
cd /usr/src/grassroot/

# BUILD JAR FILES
if [[ ! -f .clean ]]; then
  #if .clean file exist, removes it, so gradlew will run without "clean".
  echo "startgrassroot using quick build"
  echo "Saving tmp files to /usr/src/grassroot/.gradle/tmp"
  ./gradlew build -x test -g /usr/src/grassroot/.gradle/tmp --configure-on-demand --parallel --daemon
else
  echo "startgrassroot using clean build"
  echo "Saving tmp files to /usr/src/grassroot/.gradle/tmp"
  ./gradlew clean build -x test -g /usr/src/grassroot/.gradle/tmp --configure-on-demand --parallel --daemon
fi

#./scripts/startmqtt.sh
java -Djava.security.egd=file:/dev/urandom -Dspring.profiles.active=$PROFILE,fast -jar -Xmx1024m grassroot-webapp/build/libs/grassroot-webapp-1.0.0.RC3.jar > log/grassroot-app.log 2>&1 &
