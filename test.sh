#! /bin/bash
set -o errexit -o pipefail


function remove {
echo "removed"

}

WORKINGFOLDER=${PWD##*/}
  DOCKERCOMPOSEPROJECTFOLDER="${WORKINGFOLDER//-}"
  # searching for docker-compose containers running for current project folder
  #docker ps -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER"
  DOCKERCOMPOSEPROJECTRUNNING=$(docker ps -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER")
  echo $DOCKERCOMPOSEPROJECTRUNNING
  if [ -z "$DOCKERCOMPOSEPROJECTRUNNING" ]; then
    echo "project not running for current folder"
    # checking if there are other projects
    DOCKERCOMPOSEOTHERPROJECTRUNNING=$(docker ps -q -f "name=_grassroot")
    if [ -z "$DOCKERCOMPOSEOTHERPROJECTRUNNING" ]; then
    echo "no other project running, continuing..."
  else
  echo "there is another project running, stop"
  while true; do
      read -p "$* You can remove the current running docker project as it will probably conflict with the one you trying to run now. If you do, this cannot be reversed [y/n]: " yn
      case $yn in
          [Yy]*) remove ;;
          [Nn]*) echo "Aborted" ; exit  1 ;;
      esac
    done
  fi
  else
    echo "project alredy running, continuing..."
  fi

#
