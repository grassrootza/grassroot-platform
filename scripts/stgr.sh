#!/bin/bash

if [[ "" ==  "$GRASSROOT_HOME" ]]; then
	export GRASSROOT_HOME=$PWD
fi

if [[ "" ==  "$PROFILE" ]]; then
	export PROFILE=localpg
fi

java \
 -Dapp.home="$GRASSROOT_HOME" \
 -Dspring.profiles.active=$PROFILE \
 -jar $GRASSROOT_HOME/grassroot-webapp/build/libs/grassroot-webapp-1.0.0.RC1.jar \
 $*

