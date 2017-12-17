#! /bin/bash
###################################################################################################
####
#### LOCAL DEPLOY DOCKER SCRIPT WRITEN EXCLUSIVELY FOR GRASSROOT BY CARLOS SANTINI hello@csantini.me
####
###################################################################################################

# set how the script will response for errors
set -o errexit -o pipefail

# define variables to modify colours during the execution
export RED='\033[0;31m'
export NC='\033[0m' # No Color

# define path for environment variables, set the path without /, example  /etc/myfiles or /home/user/myfiles

# FUNCTIONS
# display help if tag -h is used, ignore everything else
function usage {
cat << EOF
usage: $0 -d [docker/localhost/external] -b [quick/restart/clean]

This script will deploy your grassroot app on a docker container.

OPTIONS:
   -d      Database option can be: ’docker’, ’localhost’ or ’external’
           - docker -> It instantiate 3 containers, grassroot app, postgres db
           and adminier to manage the postgres db
           - localhost -> It will instantiate only the grassroot app container
           and enable the app to connect on a local postgres db previous
           installed at localhost:5432 . Note that its for linux users
           only (currently not compatible with Mac due to Docer for Mac
           network host restrictions)
           - external -> It will instantiate only the grassroot app container,
           alternatively you can connect to an external postgres db such as
           one hosted at AWS RDS.
   -b      Build option can be: ’quick’, ’restart’ and ’clean’Test type.
           - quick -> It will only restart the java application (or re-compile
           if its the first deploy) leaving the container and gradlew daemon
           running for faster deployment (or instantiate a new container if
           its the first deploy)
           - restart -> It will quickly restart the containers (or create
           one if its the first deploy) and recompile (or compile if its
           the first deploy) the java application. Note that Gradlew Daemon
           will be restarted. Thats to be used when the container become unstable.
           - clean -> It will remove and download all the images again,
           and rebuild the docker containers. Thats the case when you have modified
           the container images and want to reset to its initial state.
           Note that doing that, the Postgres container if user the option "-d docker"
           will be reseted loosing its data.
   -v      Verbose
EOF
exit 0
}

# display help if -h is supplied
function display_help {
  #echo "display_help"
  local OPTIND opt h
  while getopts "h" opt; do
    case ${opt} in
      h )
      usage
      ;;
    esac
  done
}

# check if basic arguments were supplied
function check_basic_arguments {
  { echo "#### CHECKING IF ARGUMENTS WERE SET ####"; } 2> /dev/null
  if [ $@ -eq 0 ]; then
    { printf "${RED}No arguments set. \nUsage: './localdeploy.sh -d [docker/localhost/external] -b [quick/restart/clean]'. \nThe quickest way to start is './localdeploy.sh -d docker -b quick'. \nSee README file for more info.\n${NC}"; } 2> /dev/null
    exit 1
  else
    { echo "Arguments set, continuing..."; } 2> /dev/null
  fi
}

# check docker and docker-compose are running before proceeding
function check_docker_status {
  { echo "#### CHECKING DOCKER SERVICE STATUS BEFORE PROCEEDING ####"; } 2> /dev/null
  DOCKERSTATUS=$(echo $(docker info 2>&1))
  DOCKERCOMPOSESTATUS=$(echo $(docker-compose ps 2>&1))
  #echo $DOCKERSTATUS
  if [[ "$DOCKERSTATUS" = *"Cannot connect to the Docker daemon"* ]] && [[ "$DOCKERCOMPOSESTATUS" = *"Couldn't connect to Docker daemon"* ]]; then
    { echo -e "${RED}Docker and Docker Compose are not running, please start or install docker before continuing...${NC}"; } 2> /dev/null
    exit 1;
  else
    { echo "Docker is running, displaying versions and continuing..."; } 2> /dev/null
    docker --version
    docker-compose --version
  fi
}

# check if other dockers or services are using the required ports
function check_ports {
  { echo "Checking if there is any container or services using the ports needed..."; } 2> /dev/null
  TEST8080="$(nc localhost 8080 < /dev/null; echo $?)"
  echo $TEST8080
  TEST8081="$(nc localhost 8081 < /dev/null; echo $?)"
  echo $TEST8081
  if [ "$TEST8080" = "0" ]; then
     { echo -e "${RED}There is another container using port 8080, please remove it and deploy again${NC}"; } 2> /dev/null
     PORTSBEINGUSED="true"
  fi
  if [ "$TEST8081" = "0" ]; then
     { echo -e "${RED}There is another container using port 8081, please remove it and deploy again${NC}"; } 2> /dev/null
     PORTSBEINGUSED="true"
  fi
  if [ "$PORTSBEINGUSED" = "true" ]; then
    unset ENVIRONMENTVARIABLESEXIST
    exit 1
  else
    { echo "There are no containers or services using the required ports, continuing..."; } 2> /dev/null
  fi
}
# check if previous dockers setup are running that dont belong to the same project and might cause conflicts
function check_existent_dockers {
  # searching for docker-compose containers running for current project folder
  #docker ps -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER"
  { echo "#### CHECKING EXISTENT DOCKER SERVICES RUNNING THAT MIGHT CAUSE CONFLICT ####"; } 2> /dev/null
    DOCKERCOMPOSEOTHERPROJECTRUNNING=$(docker ps -a -q -f "name=_grassroot")
    if [ -z "$DOCKERCOMPOSEOTHERPROJECTRUNNING" ]; then
      { echo "No other grassroot projects running, continuing..."; } 2> /dev/null
      check_ports
    else
      { echo "There is another project running, checking if running for current folder"; } 2> /dev/null
      echo $DOCKERCOMPOSEOTHERPROJECTRUNNING
      WORKINGFOLDER=${PWD##*/}
      DOCKERCOMPOSEPROJECTFOLDER="${WORKINGFOLDER//-}"
      DOCKERCOMPOSEPROJECTRUNNING=$(docker ps -a -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER")
      echo $DOCKERCOMPOSEPROJECTRUNNING
      if [ -z "$DOCKERCOMPOSEPROJECTRUNNING" ]; then
        { echo -e "${RED}Project is running but not for current folder, here is the list${NC}"; } 2> /dev/null
        docker ps -a
        while true; do
            read -p "$* Are you sure you want to remove all you docker containers? This cannot be reversed [y/n]: " yn
            case $yn in
                [Yy]*) docker kill $(docker ps -q);
                docker rm $(docker ps -a -q);
                { echo "Docker containers removed, please try to deploy again."; } 2> /dev/null
                exit 1  ;;
                [Nn]*) echo "Aborted" ; exit  1 ;;
            esac
        done
        # checking if there are other projects
        exit 1
      else
        { echo  "project is running for current folder, continuing..."; } 2> /dev/null
      fi
  fi
}

function check_existent_dockers_old {
  { echo "#### CHECKING EXISTENT DOCKER SERVICES RUNNING THAT MIGHT CAUSE CONFLICT ####"; } 2> /dev/null
  WORKINGFOLDER=${PWD##*/}
  DOCKERCOMPOSEPROJECTFOLDER="${WORKINGFOLDER//-}"
  # searching for docker-compose containers running for current project folder
  #docker ps -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER"
  DOCKERCOMPOSEPROJECTRUNNING=$(docker ps -q -f "name=$DOCKERCOMPOSEPROJECTFOLDER")
  echo $DOCKERCOMPOSEPROJECTRUNNING
  if [ -z "$DOCKERCOMPOSEPROJECTRUNNING" ]; then
    { echo "Project not running for current folder"; } 2> /dev/null
    # checking if there are other projects
    DOCKERCOMPOSEOTHERPROJECTRUNNING=$(docker ps -q -f "name=_grassroot")
    if [ -z "$DOCKERCOMPOSEOTHERPROJECTRUNNING" ]; then
      { echo "No other project running, continuing..."; } 2> /dev/null
    else
      { echo -e "${RED}There is another project running that may conflict with the current deploy, plese review and remove it first before continuing...${NC}"; } 2> /dev/null
    exit 1
    fi
  else
    { echo "Project alredy running, continuing..."; } 2> /dev/null
  fi
}

# check the existent of environment variables at ENVPATH  folder
function check_environment_variables {
  # CHECKING FOR ENVIRONMENT VARIABLES
  # check if ENVPATH has been set
  if [ ! -f .deploy/envpath.txt ]; then
    { echo -e "${RED}.deploy/envpath.txt file NOT FOUND, deployment can't proceed! Template can be found at .deploy/envpath.txt.template. Refer to Readme for more information.${NC}"; } 2> /dev/null
    exit 1
  else
  # set envpath variable
  ENVPATH="$(<.deploy/envpath.txt)"
  if [ ! -z "$ENVPATH" ]; then
    { echo "#### CHECKING FOR ENVIRONMENT VARIABLES ####"; } 2> /dev/null
    if [ ! -f $ENVPATH/environment-variables ]; then
      { echo -e "${RED}$ENVPATH/environment-variables file NOT FOUND, deployment can't proceed! Templates can be found at .deploy/templates/ Refer to Readme for more information.${NC}"; } 2> /dev/null
      ENVIRONMENTVARIABLESEXIST="false"
    else
      { echo "environment-variables files FOUND, continuing..."; } 2> /dev/null
    fi
    if [ ! -f $ENVPATH/grassroot-integration.properties ]; then
      { echo -e "${RED}$ENVPATH/grassroot-integration.properties file NOT FOUND, deployment can't proceed! Templates can be found at .deploy/templates/ Refer to Readme for more information.${NC}"; } 2> /dev/null
      ENVIRONMENTVARIABLESEXIST="false"
    else
      { echo "grassroot-integration.properties FOUND, continuing..."; } 2> /dev/null
    fi
    if [ ! -f $ENVPATH/grassroot-payments.properties ]; then
      { echo -e "${RED}$ENVPATH/grassroot-payments.properties file NOT FOUND, deployment can't proceed! Templates can be found at .deploy/templates/ Refer to Readme for more information.${NC}"; } 2> /dev/null
      ENVIRONMENTVARIABLESEXIST="false"
    else
      { echo "grassroot-payments.properties FOUND, continuing..."; } 2> /dev/null
    fi
    if [ "$ENVIRONMENTVARIABLESEXIST" = "false" ]; then
      unset ENVIRONMENTVARIABLESEXIST
      exit 1
    fi
    else
      { echo -e "${RED}Environment Path variable is null or not set, please adjust the path for the environment variables inside .deploy/envpath.txt before continuing...${NC}"; } 2> /dev/null
      exit 1
    fi
  fi
}

# check if db variables were properly set
function check_db_variables {
  { echo "#### CHECKING IF DATABASE VARIABLE HAS BEEN PROPERLY SET ####"; } 2> /dev/null
  DATABASETYPE=$1
  # check in case -d is set to external
  if [ -z $DATABASETYPE ]; then
    DATABASETYPE="db:5432"
    DATABASETYPE2="localhost:5432"
    if [ grep -e "$DATABASETYPE" $ENVPATH/environment-variables ]; then
      { echo -e "${RED}External db not being used inside $ENVPATH/environment-variables, exiting...${NC}" ; } 2> /dev/null
      exit 1
    elif [ grep -e "$DATABASETYPE2" $ENVPATH/environment-variables ]; then
      { echo -e "${RED}External db not being used inside $ENVPATH/environment-variables, exiting...${NC}"; } 2> /dev/null
      exit 1
    else
      { echo "External db string seems to have been properly setup"; } 2> /dev/null
    fi
  # check in case -d is set docker or localhost
  else
    if grep -e "$DATABASETYPE" $ENVPATH/environment-variables
    then
       { echo $DATABASETYPE "seems to have been properly setup inside $ENVPATH/environment-variables"; } 2> /dev/null
    else
       { echo -e ${RED} $DATABASETYPE "could not been found inside $ENVPATH/environment-variables, exiting... ${NC}"; } 2> /dev/null
        exit 1
    fi
  fi
}

# select proper docker files based on the datbase mode selected
function select_docker_compose_file {
  { echo -e "#### SETTING DATABASE MODE ####"; } 2> /dev/null
  DATABASEMODE=$1
  { echo $DATABASEMODE "mode setup"; } 2> /dev/null
  { echo "Checking if previous docker-compose exist"; } 2> /dev/null
  if [ -f docker-compose.yml ]; then
    { echo "Previous docker-compose found, deleting..."; } 2> /dev/null
    rm -f docker-compose.yml
    { echo "Deleted"; } 2> /dev/null
  fi
  { echo "Setting correct docker-compose file based on database mode set with optin -d"; } 2> /dev/null
  cp .deploy/docker-compose.yml.db-$DATABASEMODE docker-compose.yml
  { echo docker-compose.yml.db-$DATABASEMODE "set as the current docker-compose.yml, continuing..."; } 2> /dev/null
  { echo "Updating environment path ($ENVPATH) for docker compose file folder mapping"; } 2> /dev/null
  sed -i -e "s#<ENVPATH>#$ENVPATH#" docker-compose.yml
}

# popup confirmation for user if option -d clean is selected
function clean_dockers_yes_or_no {
  { echo "#### CLEAN DEPLOYMENT MODE STARTED ####"; } 2> /dev/null
  while true; do
      read -p "$* Are you sure you want to remove all you docker images? This cannot be reversed [y/n]: " yn
      case $yn in
          [Yy]*) return 0  ;;
          [Nn]*) echo "Aborted" ; exit  1 ;;
      esac
  done
}

# if option -d clean is set and confirmed by user
function clean_dockers {
  { echo -e "Removing all docker containers and images, if you want to just rebuild your JARS, run with ${RED}quick${NC} option."; } 2> /dev/null
  DOCKERMODE=$1
  docker-compose down --rmi all || exit_on_error initiate_dockers $DOCKERMODE
}

# quick restart java processes
function restart_java {
  { echo "Restarting grassroot java instance"; } 2> /dev/null
  #docker start $containerdb
  CONTAINERAPP=$(docker-compose ps -q grassroot)
  { echo "Killing grassroot java process if exist"; } 2> /dev/null
  docker exec $CONTAINERAPP pkill -f grassroot-webapp || true
  { echo "Killing gradlew setup process if exist"; } 2> /dev/null
  docker exec $CONTAINERAPP pkill -f gradlew || true
  { echo "Restarting supervidord"; } 2> /dev/null
  docker exec $CONTAINERAPP supervisorctl reload all
}

# initiate docker containers based on -d option selected
function initiate_dockers {
  { echo "#### INITIATING DOCKER CONTAINERS SETUP ####"; } 2> /dev/null
  DOCKERMODE=$1

  # DEFINE ENVIRONMENT VARIABLES
  DOCKERCOMPOSE_FILE=docker-compose.yml
  DOCKERFILE=Dockerfile.localdeploy
  LOCAL_USER=$(whoami)
  cp .deploy/$DOCKERFILE Dockerfile
  sed -i '/#ADD LOCALHOST USER/a RUN useradd -ms /bin/bash '"$LOCAL_USER"'\nRUN echo "user='"$LOCAL_USER"'" >> /etc/supervisor/conf.d/supervisord.conf' Dockerfile 

  # COPY THE STARTUP SCRIPT TO THE ROOT DIR FOLDER
  { echo "copying startup scripts to the root folder"; } 2> /dev/null
  cp .deploy/startgrassroot.sh.localdeploy startgrassroot.sh
  #cp .deploy/build-jar.sh build-jar.sh
  #chmod +x build-jar.sh
  chmod +x startgrassroot.sh
  if [ "$DOCKERMODE" = "clean" ]; then
    { echo "CLEAN MODE STARTED"; } 2> /dev/null
    touch .clean #if .clean file exist, gradlew will run with "clean".
    { echo "Recreating all containers"; } 2> /dev/null
    docker-compose build
    { echo "Initializing all containers"; } 2> /dev/null
    docker-compose up -d
  elif [ "$DOCKERMODE" = "quick" ]; then
    if [ ! "$(docker ps -q -f "name=_grassroot")" ]; then
      #starting docker
      { echo "DOCKER WASN'T STARTED, STARTING IT NOW"; } 2> /dev/null
      docker-compose up -d
    else
      { echo "QUICK DEPLOYMENT MODE STARTED"; } 2> /dev/null
      #cat docker-compose.yml
      #docker-compose ps -q grassroot
      # { echo "STARTING ALL CONTAINERS, IF YOU WANT TO REBUILD ALL DOCKER IMAGES, RUN WITH CLEAN"; } 2> /dev/null
      { echo "Checking to remove .clean file to make sure java build will run without the clean mode"; } 2> /dev/null
      if [[ -f .clean ]]; then
        rm .clean #if .clean file exist, removes it, so gradlew will run without "clean".
        { echo ".clean file removed"; } 2> /dev/null
      fi

      #CHECK IF DATABASE MODE IS DOCKER SO IT RESTARTS TO AVOID CHANGELOG
      if [ "$DATABASEMODE" = "docker" ]; then
        #CONTAINERDB=$(docker ps -q -f "name=_db")
        CONTAINERDB=$(docker-compose ps -q db)
        if [ -z "$CONTAINERDB" ]; then
          { echo "Database container doesn't exist, restarting all services"; } 2> /dev/null
          docker-compose up -d
        else
          { echo "Clearing db changelock"; } 2> /dev/null
          #docker start $CONTAINERDB
          docker exec $CONTAINERDB echo PGPASSWORD=verylongpassword
          docker exec $CONTAINERDB psql -U grassroot -d grassroot -c "DROP TABLE  IF EXISTS \"databasechangeloglock\" "
          { echo "Finish clearing db changelock"; } 2> /dev/null
          # Restart Java
          restart_java
        fi
      elif [ "$DATABASEMODE" = "localhost" ] || [ "$DATABASEMODE" = "external" ]; then
        CONTAINERDB=$(docker ps -q -f "name=_db")
        if [ ! -z "$CONTAINERDB" ]; then
          { echo "Different docker compose file was previously used, re-create it again"; } 2> /dev/null
          docker-compose up -d --remove-orphans
        else
          # Restart Java
          restart_java
        fi
      fi
    fi
  elif [ "$DOCKERMODE" = "restart" ]; then
    if [[ -f .clean ]]; then
      rm .clean #if .clean file exist, removes it, so gradlew will run without "clean".
    fi
    docker-compose stop
    docker-compose up -d --remove-orphans
  fi
}

# clean logs
function clean_logs {
  { echo "#### CLEANING LOGS ####"; } 2> /dev/null
  if [[ ! -d log ]]; then
    mkdir -p log
    touch log/grassroot-app.log
    { echo "DIR/log folder created, continuing..."; } 2> /dev/null
  else
    rm -f log/*
    touch log/grassroot-app.log
    { echo "DIR/log already exists, deleting previous logs... DONE, continuing... "; } 2> /dev/null
  fi
  { echo "#### DONE CLEANING LOGS ####"; } 2> /dev/null
}
##############################################
############# SCRIPT STARTS ##################
##############################################

#enable_debug "$@"
display_help "$@"

# CHECK IF BASIC ARGUMENTS WERE SUPPLIED, IF NOT, EXIT
check_basic_arguments $#

# CHECK IF DOCKER AND DOCKER-COMPOSE ARE RUNNING BEFORE CONTINUING
check_docker_status

# CHECK IF NOT PREVIOUS DOCKER PROJECTS ARE RUNNING
check_existent_dockers

# CHECK IF ENVIRONMENT VARIABLES WERE SET BEFORE CONTINUING
check_environment_variables

# CLEAN LOGS PRIOR NEW DEPLOYMENT
clean_logs

# CHECK PARAMETERS PROVIDED FOR -D AND -B
while getopts ":d:b:" opt; do
  case ${opt} in
    d )
      #echo $OPTIND
      if [ "$OPTARG" = "docker" ]; then
        DATABASEMODE=$OPTARG
        DATABASETYPE="db:5432"
        select_docker_compose_file $DATABASEMODE
        check_db_variables $DATABASETYPE
      elif [ "$OPTARG" = "localhost" ]; then
        DATABASEMODE=$OPTARG
        DATABASETYPE="localhost:5432"
        check_db_variables $DATABASETYPE
        select_docker_compose_file $DATABASEMODE
      elif [ "$OPTARG" = "external" ]; then
        DATABASEMODE=$OPTARG
        check_db_variables $DATABASETYPE
        select_docker_compose_file $DATABASEMODE
      else
        { echo -e "${RED}INVALID COMMANDS, USE docker, localhost or external. More info README file.${NC}" ; } 2> /dev/null
        exit 1
      fi
      ;;
    b )
      #echo $OPTIND
      if [ "$OPTIND" = "3" ]; then
        { echo "Wrong order -d should be specified first"; } 2> /dev/null
        exit 1
      fi
      if [ "$OPTARG" = "clean" ]; then
        # if set to yes, it will remove all docker images
        clean_dockers_yes_or_no "$message" && clean_dockers $OPTARG
        # build docker
        initiate_dockers $OPTARG
      elif [ "$OPTARG" = "restart" ]; then
        # build docker
        initiate_dockers $OPTARG
      elif [ "$OPTARG" = "quick" ]; then
        # build docker
        initiate_dockers $OPTARG
      else
        { echo -e "${RED}INVALID COMMANDS for -b, USE clean, restart or quick. More info README file.${NC}"; } 2> /dev/null
        exit 1
      fi
      ;;
    \? )
      { echo -e "${RED}INVALID OPTIONS. More info README file.${NC}"; } 2> /dev/null
      usage
      ;;
     * )
     { echo -e "${RED}INVALID OPTIONS. More info README file.${NC}"; } 2> /dev/null
      usage
      ;;
  esac
done
#shift $((OPTIND -1))
{ echo "#### DONE SETTING UP DOCKER CONTAINER ####"; } 2> /dev/null

# DEPLOYS THE APPLICATION TO THE CONTAINER
{ echo "#### DEPLOYMENT STARTING ... ####"; } 2> /dev/null
tail -n0 -f log/grassroot-app.log
fi
fi
