#!/bin/bash

# BUILD JAR FILES
# ./gradlew clean build -x test -g /usr/src/grassroot/.gradle/tmp --configure-on-demand --parallel --daemon
./gradlew build -x test -g /usr/src/grassroot/.gradle/tmp --configure-on-demand --parallel --daemon
