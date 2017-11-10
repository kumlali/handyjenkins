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


# Creating Custom Images Based on `handyjenkins`

* Clone https://github.com/kumlali/handyjenkins.git 
* Put public certificate of corporate proxy under `certs` directory.
* To access Jenkins over HTTPS, put key and certificate files under `certs` directory. You can use `https_cert.pem` and `https_key.pem` files provided.
* Put public certificates of git, svn, Artifactory, SonarCube and other services accessed through HTTPS under `certs` directory.
* Customize `conf\handyjenkins.conf` according to your environment.
* Replace `conf\settings.xml` with the one having your company's private Maven repository definitions.
* Replace sample `ssh\id\_rsa` and `ssh\id\_rsa.pub` files with yours. You can use sample files provided. In that case, do not forget to add content of `ssh\id\_rsa.pub` into `authorized_keys` of your target servers.
* Create Dockerfile for your Jenkins based on `handyjenkins`:
```bash
FROM alisadikkumlali/handyjenkins:latest
```
* Build your image by providing build arguments for proxy server:
```
docker build \
  --build-arg HTTP_PROXY="http://proxy.mycompany.com:8080" \
  --build-arg NO_PROXY=".mycompany.com,.sock,localhost,127.0.0.1,::1" \
  -t artifactory.mycompany.com/myjenkins .
```

Version info can also be added to image's `/tmp/version.txt`:

```
docker build \
  --build-arg HTTP_PROXY="http://proxy.mycompany.com:8080" \
  --build-arg NO_PROXY=".mycompany.com,.sock,localhost,127.0.0.1,::1" \
  --build-arg HJ_BUILD_DATE="20171108_1325" \
  --build-arg HJ_IMAGE="myjenkins" \
  --build-arg HJ_VERSION="1" \  
  -t artifactory.mycompany.com/myjenkins -t artifactory.mycompany.com/myjenkins:1 .
```
* Push the image to registry such as Artifactory
```
docker push artifactory.mycompany.com/myjenkins
```

# Running Docker Containers Based on `handyjenkins`

We need to provide key-value pairs listed in `conf\handyjenkins.conf` as environment variables.

## Running Container without Persistent Data

```bash
serviceName="myproject-jenkins"

docker run --name ${serviceName} \
  -p 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  artifactory.mycompany.com/myjenkins:latest
```

## Running Container with Persistent Data

```bash
serviceName="myproject-jenkins"

# We create the directory only once.  
dataDir=~/${serviceName}
sudo mkdir -p ${dataDir}
sudo chown 1000:1000 ${dataDir}

docker run -d --name ${serviceName} \
  -p 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  --mount type=bind,src=${dataDir},dst=/var/jenkins_home \
  artifactory.mycompany.com/myjenkins:latest
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
  -p 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  --mount type=bind,src=${dataDir},dst=/var/jenkins_home \
  artifactory.mycompany.com/myjenkins:latest
```

## Creating Swarm Service with Persistent Data and Sending Console Log to Graylog

```bash
serviceName="myproject-jenkins"

# We create the directory only once.
dataDir=/nfsdir/${serviceName}
sudo mkdir -p ${dataDir}
sudo chown 1000:1000 ${dataDir}

docker service create --name ${serviceName} \
  -p 8443:8083 \
  --env-file conf/handyjenkins.conf \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
  --mount type=bind,src=${dataDir},dst=/var/jenkins_home \
  --log-driver=gelf --log-opt gelf-address=udp://graylog.mycompany.local:12214 \
  --log-opt env=ENV_NAME,PROJECT_NAME,SERVICE_NAME,DOCKER_SERVICE_NAME,PUBLIC_PORT,VERSION \
  -e ENV_NAME= -e PROJECT_NAME=myproject -e SERVICE_NAME=jenkins -e DOCKER_SERVICE_NAME=${serviceName}s -e PUBLIC_PORT=8443 -e VERSION=1 \
  artifactory.mycompany.com/myjenkins:latest
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


