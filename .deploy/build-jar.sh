#!/bin/bash

# SET ENVIRONMENT VARIABLES BASED ON THE ENVIRONMENT PREVIOUSLY SETUP VIA
. /usr/src/grassroot/environment/environment-variables

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
