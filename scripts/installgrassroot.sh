#!/bin/bash
#yum -y install httpd > /var/log/installapache.out 2>&1
CURR=$PWD;
cd ..
mvn -Dmaven.test.skip=true -e clean compile && mvn -Dmaven.test.skip=true install
cd $CURR
