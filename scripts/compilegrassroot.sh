#!/bin/bash
#yum -y install httpd > /var/log/installapache.out 2>&1
. /etc/environment
#cd /var/grassroot
cd /opt/codedeploy-agent/deployment-root/${DEPLOYMENT_GROUP_ID}/${DEPLOYMENT_ID}/deployment-archive
mvn -e clean && mvn -Dmaven.test.skip=true -e generate-sources
mvn -Dmaven.test.skip=true -e compile && mvn -Dmaven.test.skip=true install