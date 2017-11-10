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
* Create `certs`, `conf` and `ssh` directories under project's directory
```bash
cd myjenkins
mkdir certs conf ssh
```
* Put your certificates (SSL, proxy, Gitea, SVN, Artifactory etc.) into `certs` directory
* Put Maven's `settings.xml` suitable to your environment into `conf` directory
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
└── ssh/
    ├── id_rsa
    └── id_rsa.pub
```
* Create `Dockerfile` under your project directory
```bash
FROM alisadikkumlali/handyjenkins:latest

COPY certs/ /hj/certs
RUN sudo cp /hj/certs/* /usr/local/share/ca-certificates && sudo update-ca-certificates
COPY ssh/ /hj/ssh
COPY conf/settings.xml /hj/maven/conf/settings.xml
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
# LDAP server settings
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
  -e ENV_NAME= -e PROJECT_NAME=myproject -e SERVICE_NAME=jenkins -e DOCKER_SERVICE_NAME=${serviceName}s -e PUBLIC_PORT=8443 -e VERSION=latest \
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

TODO

# Using Pipeline Templates

TODO


