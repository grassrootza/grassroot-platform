#!/bin/bash
#yum -y install httpd > /var/log/installapache.out 2>&1
. /etc/environment
chown -R ubuntu:ubuntu *
cd /var/grassroot && mvn -Dmaven.test.skip=true -e clean compile && mvn -Dmaven.test.skip=true install 
