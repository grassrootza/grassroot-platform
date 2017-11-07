#! /bin/bash
set -o errexit -o pipefail
  
  # searching for docker-compose containers running for current project folder
  #docker ps -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER"
  DOCKERCOMPOSEOTHERPROJECTRUNNING=$(docker ps -q -f "name=_grassroot")
  if [ -z "$DOCKERCOMPOSEOTHERPROJECTRUNNING" ]; then
    echo "no other project running, continuing..."
    echo $DOCKERCOMPOSEOTHERPROJECTRUNNING
  else
    echo "there is another project running, checking if local project or not"
    WORKINGFOLDER=${PWD##*/}
    DOCKERCOMPOSEPROJECTFOLDER="${WORKINGFOLDER//-}"
    DOCKERCOMPOSEPROJECTRUNNING=$(docker ps -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER")
    echo $DOCKERCOMPOSEPROJECTRUNNING
    if [ -z "$DOCKERCOMPOSEPROJECTRUNNING" ]; then
      echo "project not running for current folder, stop"
      # checking if there are other projects
    else
      echo "project is running for current folder"
    fi
  fi