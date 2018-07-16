#! /bin/bash

# Check this
echo "environment: $ENVIRONMENT"

# GET THE COMMIT ID TO SET AS PART OF THE INSTANCE NAME
COMMITID=$(git rev-parse --short HEAD)
sed -i "s/<BUILDID>/$COMMITID/" .deploy/startgrassroot.sh.$ENVIRONMENT

# DEPLOY MODIFIED IMAGE TO DOCKER HUB
SHA1=$1
ENVIRONMENT=$2
mv .deploy/Dockerfile Dockerfile
mv .deploy/startgrassroot.sh.$ENVIRONMENT startgrassroot.sh
mv .deploy/stopgrassroot.sh stopgrassroot.sh
mv .deploy/build-jar.sh build-jar.sh
mv .deploy/log_files.yml log_files.yml
chmod +x build-jar.sh
chmod +x startgrassroot.sh
chmod +x stopgrassroot.sh

echo "Finished downloading, proceeding to build docker image"

#docker build --rm=false -t awsassembly/grassroot:$CIRCLE_BRANCH-$CIRCLE_SHA1 .
docker build --rm=false -t grassrootdocker/gr-app:$ENVIRONMENT .
docker login -u $DOCKER_USER -p $DOCKER_PASS
docker push grassrootdocker/gr-app:$ENVIRONMENT
