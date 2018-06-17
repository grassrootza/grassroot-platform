# deploy.sh
#! /bin/bash

# DEFINE VARIABLES
SHA1=$1
ENVIRONMENT=$2
EBENVIRONMENT=$3

# GET GIT LATEST COMMIT COMMENT TO SET AS APP VERSION FOR BEANSTALK #
COMMIT_MESSAGE=$(git rev-parse --short HEAD && date +"%m-%d-%Y-%H-%M")
export COMMIT_MESSAGE

# EDIT DOCKERRUN TO TAG THE ENVIRONMENT JUST PUSHED TO DOCKER HUB
DOCKERRUN_FILE=Dockerrun.aws.json
sed -i "s/<TAG>/$ENVIRONMENT/" .deploy/$DOCKERRUN_FILE

# STORE DEPLOYMENT DETAILS FOR FURTHER DEBUG
echo $COMMIT_MESSAGE > deploy_status.txt

# COMMIT
mv .deploy/.elasticbeanstalk .elasticbeanstalk
mv .deploy/.ebextensions .ebextensions
mv .deploy/.ebignore .ebignore
mv .deploy/Dockerrun.aws.json Dockerrun.aws.json

# thats just a "virtual" commit since we do not send anything back to the repository, but this is requred for beasntalke to get the latest staged version to be deployed
git config --global user.email "grassroot@grassroot.com"
git config --global user.name "GRASSROOT"
git add .
git commit -m "$ENVIRONMENT-$COMMIT_MESSAGE"

echo "Finished with Git, proceeding to deploy to $EBENVIRONMENT, with message $ENVIRONMENT-$COMMIT_MESSAGE"

# DEPLOY APP
eb use $EBENVIRONMENT --verbose
eb deploy $EBENVIRONMENT --label "$ENVIRONMENT-$COMMIT_MESSAGE" --timeout 20

echo "Deployment completed"