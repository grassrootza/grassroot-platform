#!/bin/bash

# SET ENVIRONMENT VARIABLES BASED ON THE ENVIRONMENT PREVIOUSLY SETUP VIA
. /usr/src/grassroot/environment/environment-variables

# START GRASSROOT
cd /usr/src/grassroot/

#./scripts/startmqtt.sh
java -Djava.security.egd=file:/dev/urandom -Dspring.profiles.active=$PROFILE,fast -jar -Xmx1024m grassroot-webapp/build/libs/grassroot-webapp-1.0.0.RC3.jar > log/grassroot-app.log 2>&1 &
