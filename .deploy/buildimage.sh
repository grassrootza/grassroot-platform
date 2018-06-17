#! /bin/bash

# Check this
echo "environment: $ENVIRONMENT"

# Define
S3BUCKET=grassroot-circleci
S3REGION=eu-west-1

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

# DOWNLOAD ENVIRONMENT VARIABLES AND CREDENTIALS FROM S3
aws s3 cp s3://$S3BUCKET/environment-variables.$ENVIRONMENT environment/environment-variables --region $S3REGION
aws s3 cp s3://$S3BUCKET/aws-credentials.$ENVIRONMENT environment/aws-credentials --region $S3REGION
aws s3 cp s3://$S3BUCKET/grassroot-integration.properties.$ENVIRONMENT environment/grassroot-integration.properties --region $S3REGION
aws s3 cp s3://$S3BUCKET/grassroot-payments.properties.$ENVIRONMENT environment/grassroot-payments.properties --region $S3REGION
aws s3 cp s3://$S3BUCKET/jwt_keystore.jks.$ENVIRONMENT environment/jwt_keystore.jks --region $S3REGION

# DOWNLOAD PDF TEMPLATES (AT SOME POINT JUST FETCH FROM S3 DIRECTLY IN APP)
aws s3 cp s3://$S3BUCKET/pdf_templates/ templates/pdf/ --region $S3REGION --recursive

echo "Finished downloading, proceeding to build docker image"

#docker build --rm=false -t awsassembly/grassroot:$ENVIRONMENT$SHA1 .
docker build --rm=false -t grassrootdocker/gr-app:$ENVIRONMENT .
docker login -u $DOCKER_USER -p $DOCKER_PASS
docker push grassrootdocker/gr-app:$ENVIRONMENT
