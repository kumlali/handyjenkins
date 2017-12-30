# handyjenkins

A Jenkins image to help minimize overhead of having separate Jenkins for each project.

Instead of managing all projects with a central Jenkins, in some cases, using separate Jenkins servers for each project would be a better solution. `handyjenkins` was created to simplify installation and management of Jenkins to achive that goal.

Put it simply, `handyjenkins` is a customized Docker image based on [Jenkins's official image](https://github.com/jenkinsci/docker) similar to 
* https://github.com/Accenture/adop-jenkins
* https://github.com/fabric8io/jenkins-docker

It aims to overcome following complications:
* Accessing internet over a corporate proxy which replaces SSL certificates of target sites with its own(SSL Interception proxy - [here](https://bto.bluecoat.com/webguides/proxysg/security_first_steps/Content/Solutions/SSL/ssl_solution.htm), [here](https://www.secureworks.com/research/transitive-trust), [here](http://www.zdnet.com/article/how-the-nsa-and-your-boss-can-intercept-and-break-ssl) and [here](https://media.blackhat.com/bh-eu-12/Jarmoc/bh-eu-12-Jarmoc-SSL_TLS_Interception-Slides.pdf)).
* Configuring proxy settings
* Configuring LDAP settings
* Configuring Root URL setting
* Configuring credentials
* Configuring global security
* Configuring project-based matrix authorization strategy
* Configuring default views
* Installing plugins
* Enabling HTTPS
* Accessing Docker host within Jenkins's container 
* Connecting and deploying artifacts to servers with ssh keys
* Installing Maven
* Installing Flyway and PostgreSQL driver
* Using pipeline templates
* Creating test jobs
* Creating central scripts having functions to simplify continues delivery processes and pipelines:  
  * Automatically removing old builds from Jenkins
  * Automatically deleting old artifacts from local Maven repositories
  * Automatically removing old Maven artifacts and old Docker images on Artifactory
  * Automatically adding GELF logging support to containers created by Jenkins.
  * Automatically creating pipeline versions and keeping it throughout the process
  * Automatically marking builds as "keep forever" if pipeline ends by deploying to production
  * Automatically generating unique ports for different deployment environments

# Screenshots

|  |  |  |
|:-------------:|:-------:|:-------:|
|![Default jobs, views and LDAP authentication](https://raw.githubusercontent.com/kumlali/public/master/images/handyjenkins/handyjenkins_screenshot_01.jpg)|![Credentials](https://raw.githubusercontent.com/kumlali/public/master/images/handyjenkins/handyjenkins_screenshot_02.jpg)|![Credentials - SSH Username with private key](https://raw.githubusercontent.com/kumlali/public/master/images/handyjenkins/handyjenkins_screenshot_03.jpg)|
|![Credentials - Username and password](https://raw.githubusercontent.com/kumlali/public/master/images/handyjenkins/handyjenkins_screenshot_04.jpg)|![Configure System - Number of executors](https://raw.githubusercontent.com/kumlali/public/master/images/handyjenkins/handyjenkins_screenshot_05.jpg)|![Configure Global Security - LDAP configuration](https://raw.githubusercontent.com/kumlali/public/master/images/handyjenkins/handyjenkins_screenshot_06.jpg)|
|![Configure Global Security - Project-based Matrix Authorization Strategy, CSRF, proxy compability, CLI over Remoting](https://raw.githubusercontent.com/kumlali/public/master/images/handyjenkins/handyjenkins_screenshot_07.jpg)| | |


# How to Build `handyjenkins`
* Clone https://github.com/kumlali/handyjenkins.git
* If you are required to use proxy server to access internet, put public certificate of your proxy into `certs` directory.
* Build the image
```bash
docker build \
  --build-arg HTTP_PROXY="http://proxy.mycompany.com:8080" \
  --build-arg NO_PROXY=".mycompany.com,.sock,localhost,127.0.0.1,::1" \
  -t handyjenkins .
```

# Creating Your Jenkins Image By Customizing `handyjenkins` Source
* Clone https://github.com/kumlali/handyjenkins.git 
* Put your certificates (SSL, proxy, Gitea, SVN, Artifactory etc.) into `certs` directory. Skip this step if you do not need Jenkins to serve on HTTPS port and to connect Gitea, SVN, Artifactory etc. via HTTPS.
* Replace `conf/settings.xml` with the one having your company's private Maven repository definitions. Skip this step if you do not have private repository and customized `settings.xml` for your environment.
* Replace sample `ssh/id_rsa` and `ssh/id_rsa.pub` files with yours. For testing purposes, you can use sample files and add content of `ssh/id_rsa.pub` into `authorized_keys` of your target servers. However, please be warned that it is not safe and recomended. Skip this step if you do not need to ssh via key-based authentication.
* Build the image. If you are required to use proxy server to access internet, you need to provide build arguments for the proxy server:
```bash
docker build \
  --build-arg HTTP_PROXY="http://proxy.mycompany.com:8080" \
  --build-arg NO_PROXY=".mycompany.com,.sock,localhost,127.0.0.1,::1" \
  -t myjenkins .
```

Version info can also be added to image's `/tmp/version.txt` file:

```bash
docker build \
  --build-arg HTTP_PROXY="http://proxy.mycompany.com:8080" \
  --build-arg NO_PROXY=".mycompany.com,.sock,localhost,127.0.0.1,::1" \
  --build-arg HJ_BUILD_DATE="`date`" \
  --build-arg HJ_IMAGE="myjenkins" \
  --build-arg HJ_VERSION="1.0" \  
  -t myjenkins .

...

docker run -it myjenkins cat /tmp/version.txt
BUILD DATE: Fri Nov 10 15:43:26 +03 2017, IMAGE: myjenkins, VERSION: 1.0
```

# Creating Your Jenkins Image Based on `handyjenkins` Image

* Create your project directory
```bash
mkdir myjenkins
```
* Create `certs`, `conf` and `ssh` directories under project's directory. If you have jobs to include the image create `jobs` directory as well.
```bash
cd myjenkins
mkdir certs conf jobs ssh
```
* Put your certificates (SSL, proxy, Gitea, SVN, Artifactory etc.) into `certs` directory
* Put Maven's `settings.xml` suitable to your environment into `conf` directory
* Copy job directories(if exists) into `jobs` directory
* Put `id_rsa` and `id_rsa.pub` files used for key based authentication into `ssh` directory
```bash
myjenkins/
├── certs/
│   ├── artifactory.mycompany.com.crt
│   ├── gogs.mycompany.com.crt
│   ├── myjenkins.mycompany.com.cert.pem
│   ├── myjenkins.mycompany.com.key.pem
│   ├── proxy.mycompany.com.crt
│   └── svn.mycompany.com.crt
├── conf/
│   └── settings.xml
├── jobs/
|    ├── job1
|    └── job2
├── ssh/
|    ├── id_rsa
|    └── id_rsa.pub
```
* Create `Dockerfile` under your p|roject directory
```bash
FROM alisadikkumlali/handyjenkins:latest

COPY certs/ /hj/certs
RUN sudo cp /hj/certs/* /usr/local/share/ca-certificates && sudo update-ca-certificates
COPY ssh/ /hj/ssh
COPY conf/settings.xml /hj/maven/conf/settings.xml

# If needed, add common jobs for the company
COPY jobs/ /usr/share/jenkins/ref/jobs

# Set timezone to Europe/Istanbul
ENV TIMEZONE="Europe/Istanbul"
ENV JAVA_OPTS="${JAVA_OPTS} -Duser.timezone=${TIMEZONE}"
RUN unlink /etc/localtime \
  && ln -s /usr/share/zoneinfo/"${TIMEZONE}" /etc/localtime

# If needed, add Oracle JDBC driver for Flyway
# RUN curl -o /hj/flyway/drivers/ojdbc6-11.2.0.2.0.jar http://artifactory.mycompany.com/artifactory/releases/com/oracle/ojdbc6/11.2.0.2.0/ojdbc6-11.2.0.2.0.jar
```
* Build the image
```
docker build -t myjenkins .
```

# Running Your Jenkins Container Based on `handyjenkins`

We need to provide runtime configuration as environment variables. All the variables `handyjenkins` understand exist in `conf/handyjenkins.conf`. We need to customize  `conf/handyjenkins.conf` according to our environment.
```
########################################################################
# Proxy server settings
########################################################################
HTTP_PROXY=proxy.mycompany.com:8080
http_proxy=proxy.mycompany.com:8080
HTTPS_PROXY=proxy.mycompany.com:8080
https_proxy=proxy.mycompany.com:8080
FTP_PROXY=proxy.mycompany.com:8080
ftp_PROXY=proxy.mycompany.com:8080
NO_PROXY=.mycompany.com,.sock,localhost,127.0.0.1,::1
no_proxy=.mycompany.com,.sock,localhost,127.0.0.1,::1

########################################################################
# Credentials
########################################################################
# Credentials for authenticating to SVN, Gitea, JIRA, FishEye, Artifactory, SonarCube, etc.
HJ_AUTH_CREDENTIALS_USERNAME=myuser
HJ_AUTH_CREDENTIALS_PASSWORD=mypass

# Username for key-based ssh authentication. /hj/ssh/id_rsa is used as private key.
HJ_SSH_CREDENTIALS_USERNAME=mysshuser

########################################################################
# Security settings
########################################################################
HJ_ENABLE_SECURITY=true

########################################################################
# LDAP server settings (HJ_ENABLE_SECURITY must be true)
########################################################################
# For example: ldap.mycompany.com:389
HJ_LDAP_SERVER=ldap.mycompany:389

# For example: OU=Internal,DC=mycompany,DC=com
HJ_LDAP_ROOTDN=OU=Internal,DC=mycompany,DC=com
...
```

## Running Container without Persistent Data

```bash
serviceName="myproject-jenkins"

docker run -d --name ${serviceName} \
  --publish 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  myjenkins
```

## Running Container with Persistent Data

```bash
serviceName="myproject-jenkins"

# We create the directory only once.  
dataDir=~/${serviceName}
sudo mkdir -p ${dataDir}
sudo chown 1000:1000 ${dataDir}

docker run -d --name ${serviceName} \
  --publish 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  --mount type=bind,src=${dataDir},dst=/var/jenkins_home \
  myjenkins
```

## Creating Swarm Service with Persistent Data

While Jenkins runs on swarm cluster, its data must be accessible from all the cluster nodes. We can use NFS to share data.

```bash
serviceName="myproject-jenkins"

# We create the directory only once.
dataDir=/nfsdir/${serviceName}
sudo mkdir -p ${dataDir}
sudo chown 1000:1000 ${dataDir}

docker service create --name ${serviceName} \
  --publish 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  --mount type=bind,src=${dataDir},dst=/var/jenkins_home \
  myjenkins
```

## Creating Swarm Service with Persistent Data and Sending Console Log to Graylog

```bash
serviceName="myproject-jenkins"

# We create the directory only once.
dataDir=/nfsdir/${serviceName}
sudo mkdir -p ${dataDir}
sudo chown 1000:1000 ${dataDir}

docker service create --name ${serviceName} \
  --publish 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  --mount type=bind,src=${dataDir},dst=/var/jenkins_home \
  --log-driver=gelf --log-opt gelf-address=udp://graylog.mycompany.local:12214 \
  --log-opt env=ENV_NAME,PROJECT_NAME,SERVICE_NAME,DOCKER_SERVICE_NAME,PUBLIC_PORT,VERSION \
  -e ENV_NAME= -e PROJECT_NAME=myproject -e SERVICE_NAME=jenkins -e DOCKER_SERVICE_NAME=${serviceName} -e PUBLIC_PORT=8443 -e VERSION=latest \
  myjenkins
```

# Testing

With Firefox:

* Open Firefox on the machine that container runs or any swarm node for the swarm case.
* Connect to [https://localhost:8443](https://localhost:8443)

With curl:

```bash
curl -kX GET https://localhost:8443
```

# Using Utility Functions in Pipelines

[utilities.groovy](scripts/utilities.groovy) provides functions to simplify continues delivery processes and pipelines. The file is under container's `/hj/scripts` directory and can be loaded within pipeline script:
```groovy
#!groovy

node {  
  UTILITIES = load("/hj/scripts/utilities.groovy")

  stage ("Initialization") {
    UTILITIES.printEnvironmentVariables ()
  }
}
```

Let's assume we have a Maven project. Here is the pipeline script that compiles, tests and deploys it:

```groovy
#!groovy

node { 
  UTILITIES = load("/hj/scripts/utilities.groovy")

  timestamps {

    stage ("Initialize") {
      checkout scm    
    }

    stage ("Compile Maven Project") {
      // mvn -B -V -U clean compile
      UTILITIES.compileMavenProject ()
    }

    stage ("Test Maven Project") {
      // mvn -B verify org.jacoco:jacoco-maven-plugin:prepare-agent
      UTILITIES.testCompiledMavenProject ()
    }

    stage ("Check Quality of Tested Maven Project") {
      // mvn -B sonar:sonar -P sonar-profile -Dsonar.host.url=${env.HJ_SONAR_SERVER}
      UTILITIES.checkQualityOfTestedMavenProject ()
    }

    stage ("Deploy Maven Artifacts to Artifactory") {
      // mvn -B deploy -DskipTests=true
      UTILITIES.deployMavenArtifactsToArtifactory ()
    }

  }
}
```

Let's assume;
* we have different swarm clusters for test and prod environments
* multiple projects can co-exist on the same swarm cluster. For example, test environment of project1 and project2 can co-exist on the same test cluster
* for each project; we have dev, alpha, beta and preprod as test environments those run on test swarm cluster
* for each project; we have prod environment that runs on prod swarm cluster
* we have a Maven project and would like to run it as swarm service
* we want to create different ports for dev, alpha, beta, ... automatically

Here is Dockerfile for it:

```Dockerfile
FROM artifactory.mycompany.com/java:alpine

ARG SERVICE_NAME=<serviceName>
ENV SERVICE_NAME=${SERVICE_NAME}

CMD java -jar /data/${SERVICE_NAME}.jar server /data/conf/${ENV_NAME}/config.yml

EXPOSE 80

ADD conf /data/conf
ADD target/${SERVICE_NAME}.jar /data/${SERVICE_NAME}.jar
```

and here is pipeline script:

```groovy
env.PL_PROJECT_NAME = "myproject"
env.PL_SERVICE_NAME = "myservice"
env.PL_MAVEN_GROUP = "com.mycompany.${env.PL_PROJECT_NAME}"
env.PL_MAVEN_ARTIFACT = "${env.PL_SERVICE_NAME}"
env.PL_MAVEN_ARTIFACT_PATH = "com/mycompany/${env.PL_PROJECT_NAME}/${env.PL_SERVICE_NAME}"
env.PL_SERVICE_PRIVATE_PORT = "80"
// If public port must be different for each environment(dev, alpha, ...)
// set following variable. (See UTILITIES.getPublicPort ())
// DEV: 8901, ALPHA: 8902, BETA: 8903, ...
env.PL_SERVICE_PUBLIC_PORT_BASE = "8900"
env.PL_DOCKER_IMAGE = "mycompany/${env.PL_PROJECT_NAME}/${env.PL_SERVICE_NAME}"

node { 
  UTILITIES = load("/hj/scripts/utilities.groovy")

  timestamps {

    // -------------------------------------------------------------------------------------------- Build
    stage ("Initialize") {
      checkout scm
      // Creates pipeline version from pom.xml, commit ID and Jenkins's 
      // build number and set env.PL_VERSION to it.
      UTILITIES.setMavenBasedPipelineVersion ()
    }

    stage ("Compile Maven Project") {
      UTILITIES.compileMavenProject ()
    }

    stage ("Test Maven Project") {
      UTILITIES.testCompiledMavenProject ()
    }

    stage ("Check Quality of Tested Maven Project") {
      UTILITIES.checkQualityOfTestedMavenProject ()
    }

    stage ("Deploy Maven Artifacts to Artifactory") {
      UTILITIES.deployMavenArtifactsToArtifactory ()
    }

    stage ("Build Docker Image") {
      def options = "--build-arg SERVICE_NAME=${env.PL_SERVICE_NAME}"
      UTILITIES.buildDockerImage (options)
      UTILITIES.pushDockerImageToArtifactory ()
    }

    stage ("Clean-Up") {
      UTILITIES.discardMavenArtifactsOnLocalMavenRepository ()
      UTILITIES.discardMavenArtifactsOnArtifactory()
      UTILITIES.discardImagesOnArtifactory()    
    }


    // -------------------------------------------------------------------------------------------- Deploy

    // ----- DEV
    env.PL_DEPLOY_ENV = "dev"
    env.PL_SWARM_MANAGER_NODE = "swarmmngtest.mycompany.com"
    env.PL_GRAYLOG_SERVER = "graylogtest.mycompanytest.local"
    env.PL_GRAYLOG_PORT = "12214"
    

    stage ("Initialize - ${env.PL_DEPLOY_ENV}") {
      env.PL_SWARM_SERVICE_NAME = "myproject-${env.PL_DEPLOY_ENV}-${env.PL_SERVICE_NAME}"
      env.PL_SWARM_NETWORK = "myproject-${env.PL_DEPLOY_ENV}-network"
    }

    stage ("Update DB - ${env.PL_DEPLOY_ENV}") {
      sh "if [ -e \"conf/${env.PL_DEPLOY_ENV}/flyway.conf\" ]; then flyway -configFile=conf/${env.PL_DEPLOY_ENV}/flyway.conf migrate info; else echo \"No update will be done as there is no Flyway configuration\"; fi"
    }

    stage ("Deploy Image to Swarm - ${env.PL_DEPLOY_ENV}") {    
      def createOptions = ""
      def updateOptions = ""
      UTILITIES.deployImageToSwarm (env.PL_VERSION, createOptions, updateOptions)
    }

    stage ("Smoke Tests") {
      println "Unimplemented"
      // build job: "myproject Smoke Tests"
    }

    
    // ----- ALPHA
    env.PL_DEPLOY_ENV = "alpha"
    env.PL_SWARM_MANAGER_NODE = "swarmmngtest.mycompany.com"
    env.PL_GRAYLOG_SERVER = "graylogtest.mycompanytest.local"
    env.PL_GRAYLOG_PORT = "12214"

    stage ("Initialize - ${env.PL_DEPLOY_ENV}") {
      env.PL_SWARM_SERVICE_NAME = "myproject-${env.PL_DEPLOY_ENV}-${env.PL_SERVICE_NAME}"
      env.PL_SWARM_NETWORK = "myproject-${env.PL_DEPLOY_ENV}-network"
    }

    stage ("Update DB - ${env.PL_DEPLOY_ENV}") {
      sh "if [ -e \"conf/${env.PL_DEPLOY_ENV}/flyway.conf\" ]; then flyway -configFile=conf/${env.PL_DEPLOY_ENV}/flyway.conf migrate info; else echo \"No update will be done as there is no Flyway configuration\"; fi"
    }

    stage ("Deploy Image to Swarm - ${env.PL_DEPLOY_ENV}") {    
      def createOptions = ""
      def updateOptions = ""
      UTILITIES.deployImageToSwarm (env.PL_VERSION, createOptions, updateOptions)
    }

    stage ("Regression Tests") {
      println "Unimplemented"
      //build job: "myproject Regression Tests"
    }
    
    // ----- BETA
    // TODO

    // ----- PREPROD
    // TODO

    // ----- PROD
    env.PL_DEPLOY_ENV = "prod"
    env.PL_SWARM_MANAGER_NODE = "swarmmng.mycompany.com"
    env.PL_GRAYLOG_SERVER = "graylog.mycompanyprod.local"
    env.PL_GRAYLOG_PORT = "12214"

    stage ("Initialize - ${env.PL_DEPLOY_ENV}") {
      env.PL_SWARM_SERVICE_NAME = "myproject-${env.PL_DEPLOY_ENV}-${env.PL_SERVICE_NAME}"
      env.PL_SWARM_NETWORK = "myproject-${env.PL_DEPLOY_ENV}-network"
    }

    stage ("Update DB - ${env.PL_DEPLOY_ENV}") {
      sh "if [ -e \"conf/${env.PL_DEPLOY_ENV}/flyway.conf\" ]; then flyway -configFile=conf/${env.PL_DEPLOY_ENV}/flyway.conf migrate info; else echo \"No update will be done as there is no Flyway configuration\"; fi"
    }

    stage ("Deploy Image to Swarm - ${env.PL_DEPLOY_ENV}") {    
      def createOptions = "--replicas=4"
      def updateOptions = ""
      UTILITIES.deployImageToSwarm (env.PL_VERSION, createOptions, updateOptions)
    }

    stage ("Keep This Build Forever") {
      // Because our service went live, we want to keep the build forever.
      UTILITIES.keepBuildForever (env.BUILD_NUMBER)
    }

  }
}
```




# Using Pipeline Templates

Instead of writing similar pipeline scripts for each project, we can use templates. Templates are simply Groovy scripts that can be used in pipelines.

For example, if we have following `mvn-build.template` file under `/hj/templates` directory:

```groovy
#!groovy

UTILITIES = load("/hj/scripts/utilities.groovy")

timestamps {

  stage ("Initialize") {
    checkout scm    
  }

  stage ("Compile Maven Project") {
    // mvn -B -V -U clean compile
    UTILITIES.compileMavenProject ()
  }

  stage ("Test Maven Project") {
    // mvn -B verify org.jacoco:jacoco-maven-plugin:prepare-agent
    UTILITIES.testCompiledMavenProject ()
  }

  stage ("Check Quality of Tested Maven Project") {
    // mvn -B sonar:sonar -P sonar-profile -Dsonar.host.url=${env.HJ_SONAR_SERVER}
    UTILITIES.checkQualityOfTestedMavenProject ()
  }

  stage ("Deploy Maven Artifacts to Artifactory") {
    // mvn -B deploy -DskipTests=true
    UTILITIES.deployMavenArtifactsToArtifactory ()
  }

}
```

then we can call it within our pipelines as follows:

```groovy
#!groovy

node {  
  
  load("/hj/templates/mvn-build.template")

}
```
