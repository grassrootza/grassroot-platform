# GRASSROOT PLATFORM

Application to make it faster, cheaper and easier to persistently organize and mobilize people in low-income communities.

The platform is built with the Spring framework and launches through Spring Boot. To run it on a local environment, use
the profile "localpg". It requires some local set up and configuration, namely:

### Local Deployment using Docker ###

* 1 -- Install Docker and Docker-compose
MAC: https://docs.docker.com/docker-for-mac/
LINUX/UBUNTU: https://docs.docker.com/engine/installation/linux/docker-ce/ubuntu/
WINDOWS: https://docs.docker.com/docker-for-windows/

In linux you might have to go through this https://techoverflow.net/2017/03/01/solving-docker-permission-denied-while-trying-to-connect-to-the-docker-daemon-socket/

If you already have it installed, make sure you can run it from the terminal by typing:

docker --version
docker-compose --version

TESTED VERSIONS
Docker version 17.09.0-ce, build afdb6d4
docker-compose version 1.16.1, build 6d1ac21

* 2 -- Clone Master Branch to a DIR
* 3 -- Inside the DIR, either copy  the files from DIR/.deploy/templates to a folder OUTSIDE your project source (VERY IMPORTANT DUE TO SECURITY ISSUES), example /home/user/environment OR if you already have an integration properties file, just copy it directly into your folder OUTSIDE your project source.
* 4 -- Inside the DIR/.deploy, copy the file envpath.txt.template to envpath.txt and update the path you copied and modified your environment variables at step 3 above.
* 5 -- Run "./localdeploy.sh -d [docker/localhost/external] -b [quick/restart/clean]" from the DIR root. See the command options below to which parameter to choose. The quickest way to start is "./localdeploy.sh -d docker -b quick"

COMMAND OPTIONS

**-b**

* -b quick #It will only restart the java application (or re-compile if its the first deploy) leaving the container and gradlew daemon running for faster deployment (or instantiate a new container if its the first deploy)

* -b restart #It will quickly restart the containers (or create one if its the first deploy) and recompile (or compile if its the first deploy) the java application. Note that Gradlew Daemon will be restarted. Thats to be used when the container become unstable.

* -b clean #It will remove and download all the images again, and rebuild the docker containers. Thats the case when you have modified the container images and want to reset to its initial state. Note that doing that, the Postgres container if user the option "-d docker" will be reseted loosing its data.

**-d**
* -d docker  # It will instantiate 3 containers, one for a local postgres db running at port 5432 with a blank db, application running on port 8080 accessible via http://localhost:8080 and a postgres admin app accessible via http://localhost:8081

* -d localhost # FOR LINUX ONLY, it will instantiate 1 container only for the app accessible via http://localhost:8080 and enable the container to connect to a db located at localhost:5432. Remember to modify your environment-variables files to reflect that as in the template file we have http://db:5432 for the "-d docker" option.

* -d external # It will instantiate 1 container only for the app accessible via http://localhost:8080 and enable the container to connect to an external DB (outside your localnetwork). Remember to modify your environment-variables files to reflect that as in the template file we have http://db:5432 for the "-d docker" option.

-----------------

** You can check the application logs at DIR/log/grassroot-app.log

** Note that the initial deployment will take between 10 - 20 min to download all the libraries dependencies. Subsequent deployments/buildings should take around 5 min as it just build and boot the jar files.

** If you are having issues with running your Docker project, some of the following commands may help, use with care.

docker system prune                #Remove all stopped containers, unused volumes and unused images
docker kill $(docker ps -q);       #Stop all the containers
docker rm $(docker ps -a -q);      #Remove all containers
docker rmi $(docker images -q -a)  #Remove all images

** We have a "always" restart policy set for the containers as long as the docker engine is running. So, its very likely that when you restart your computer and the docker engine starts as a daemon, the containers will also restart. To avoid that, make sure you remove the docker to start as a daemon on reboot.

### AWS Deployment using Beanstalk and Docker ###

We currently use a combination of AWS Services, Circleci and Papertrail (Logging) to deploy our application to the cloud.

* 1 -- Upload the source code to either GIT HUB or BIT bucket
* 2 -- Sign up for a Circleci account, a free account should be enough at this stage, configure to read changes from your repository
* 3 -- Configure CircleCI with the AWS Credentials that have "Read Access" to S3 and "Full Control" to beanstalk.
* 4 -- Setup a Docker Beanstalk environment
* 5 -- Setup a Postgres Database using AWS RDS or any other cloud database provider (faster if you setup within the same region of your Docker server)
* 6 -- Create a S3Bucket that will contain the environment files, a template of these files can be found at DIR/.deploy/templates, update them accordingly to your needs.
* 7 -- Update the deployment variables in the circle.yml file.


### EXTRA NOTES ###

1 -- A configuration file, ~/grassroot/grassroot-integration.properties. This should set the following properties:

* The SMS gateway for SMS notifications (grassroot.sms.gateway for the host, and grassroot.sms.gateway.username and
grassroot.sms.gateway.password)

* A GCM sender ID and key for push notifications (gcm.sender.id and gcm.sender.key), unless GCM is turned off by setting
property gcm.connection.enabled=false in application-localpg.properties in grassroot-webapp

* Usual Spring Mail properties, plus grassroot.mail.from.address, grassroot.mail.from.name, and grassroot.system.mail
(destination for daily summary mail of activity on the platform), unless email is disabled via grassroot.email.enabled

* The name of AWS S3 buckets in which to store task images, if desired

2 -- A configuration file, ~/grassroot/grassroot-payments.properties, with details of the payments provider, only
necessary if billing and payments are switched on via grassroot.accounts.active, grassroot.billing.enabled and
grassroot.payments.enabled, in application-localpg.properties.
