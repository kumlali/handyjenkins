/* -------------------------------------------------------------------
   Constants & Enums
   -------------------------------------------------------------------*/
// Color codes used by Ansi Color Plug-in
enum COLOR {
  RED("\u001B[31m"),
  BLACK("\u001B[30m"),
  GREEN("\u001B[32m"),
  YELLOW("\u001B[33m"),
  BLUE("\u001B[34m"),
  PURPLE("\u001B[35m"),
  CYAN("\u001B[36m"),
  WHITE("\u001B[37m"),
  NO_COLOR("\u001B[0m")

  String code

  public COLOR(String code) {
      this.code = code
  }
}

// Swarm service statuses
enum STATUS {
  SERVICE_EXISTS("0"),
  SERVICE_DOES_NOT_EXIST("1"),
  SERVICE_RUNNING("2"),
  SERVICE_IS_NOT_RUNNING("3")

  String status

  public STATUS(String status) {
    this.status = status
  }
}



/* -------------------------------------------------------------------
   General Utilities
   -------------------------------------------------------------------*/
/*
  Each Jenkinsfile must call this function after it loads the script.
*/
def initialize () {

  /* -------------------------------------------------------------------
     Project &/ Service Independent Definitions
     -------------------------------------------------------------------*/

  // Will be calculated
  env.PL_VERSION = ""


  /* -------------------------------------------------------------------
     Jenkins customization 
     -------------------------------------------------------------------*/
  // We need to manually update "Discard old build" settings. Because, 
  // discarding of the old builds is not performed in multibranch 
  // pipeline: https://issues.jenkins-ci.org/browse/JENKINS-34738
  //
  // WARNING: Overrides all job properties. See setNumberToKeep ()
  setNumberToKeep (env.HJ_BUILD_AND_ARTIFACT_COUNT_TO_KEEP)
}

/*
  ANSI Color Plug-in is required: https://wiki.jenkins-ci.org/display/JENKINS/AnsiColor+Plugin 
*/
def printMsg (def message, def color) {
   println "${color.code}${message}${COLOR.NO_COLOR.code}"
}

def printTitle (def title, def color) {
   def seperator = "------------------------------------------------------------"
   def fancyTitle = "${color.code}${seperator}\n${title}\n${seperator}${COLOR.NO_COLOR.code}"
   println fancyTitle
}

def printRed (def message) {
   printMsg (message, COLOR.RED)
}

def printGreen (def message) {
   printMsg (message, COLOR.GREEN)
}

def printBlue (def message) {
   printMsg (message, COLOR.BLUE)
}

def printRedTitle (def title) {
  printTitle (title, COLOR.RED)
}

def printGreenTitle (def title) {
  printTitle (title, COLOR.GREEN)
}

def printBlueTitle (def title) {
  printTitle (title, COLOR.BLUE)
}

/*
  Prints environment variables excluding passwords.
*/
def printEnvironmentVariables () {
  sh "env | grep -vi password | sort > env.txt"
  printTitle ("Environment Variables", COLOR.NO_COLOR)
  println readFile('env.txt')
}

/*
  If env.PL_SERVICE_PUBLIC_PORT is not "", returns it
  
  If env.PL_SERVICE_PUBLIC_PORT is "" but env.PL_SERVICE_PUBLIC_PORT_BASE 
  is not, returns calculated port according to the following rule:
  
    DEV PORT =  env.PL_SERVICE_PUBLIC_PORT_BASE + 1
    ALPHA PORT =  env.PL_SERVICE_PUBLIC_PORT_BASE + 2
    BETA PORT =  env.PL_SERVICE_PUBLIC_PORT_BASE + 3
    PREPROD PORT =  env.PL_SERVICE_PUBLIC_PORT_BASE + 4
    PROD PORT =  env.PL_SERVICE_PUBLIC_PORT_BASE + 5

    Example: 
      env.PL_SERVICE_PUBLIC_PORT_BASE = "8550" => DEV PORT: 8551, ALPHA PORT: 8552, ...
*/
def getPublicPort () {

  if (env.PL_SERVICE_PUBLIC_PORT != "") 
    return env.PL_SERVICE_PUBLIC_PORT

  if (env.PL_SERVICE_PUBLIC_PORT_BASE == "") 
    return ""

  basePort = env.PL_SERVICE_PUBLIC_PORT_BASE.toInteger()  
  switch (env.PL_DEPLOY_ENV) {
     case "dev": return basePort + 1
     case "alpha": return basePort + 2
     case "beta": return basePort + 3
     case "preprod": return basePort + 4
     case "prod": return basePort + 5
     default: return -1
  }
}

/*
  Tests whether given port is up and listening. If a service publishes port(s), then the function
  can be used to test the availibility of the service.
*/
def isPortListening (def host, def port) {  
  def timeout = 3

  // References:
  // - How do I run a different Shell interpreter in a Pipeline Script: https://support.cloudbees.com/hc/en-us/articles/215171038-How-do-I-run-a-different-Shell-interpreter-in-a-Pipeline-Script
  // - Test from shell script if remote TCP port is open: http://stackoverflow.com/a/19866239/5903564
  def result = sh script: "#!/bin/bash \n" + 
    "/usr/bin/timeout ${timeout} /bin/bash -c '/bin/cat < /dev/null > /dev/tcp/${host}/${port}';echo \$?", returnStdout: true
 
  switch(result.trim()) {
    case "0":
      return true
    case "1":
      return false
    case "124":
      return false
    default:
      return false
  }    
}

/*
  Checks host:port every 15 seconds until the port is available or waitPeriodInSeconds is reached. 
  If waitPeriodInSeconds is exceeded, a timeout exception is thrown.
*/
def waitForPortToListen (def host, def port, def waitPeriodInSeconds) {
  timeout (time: waitPeriodInSeconds, unit: 'SECONDS') { 
    while (!isPortListening (host, port)) {
      sleep (15)
    }
  }
}

/*
  Executes given command on given host via ssh.
  
  Requires Credentials, SSH Agent and SSH Credentials plugins. 
  Please refer to SSH Agent Plug-in documentation: https://wiki.jenkins-ci.org/x/WQLiAw

  Note: -tt parameter is used to prevent "Pseudo-terminal will not be allocated 
        because stdin is not a terminal." error.
  
  Note: Because sh command returns a string having '\r' line ending character,  
        in some cases, trim() might be necessary to remove '\r'. 
        (e.g. executeSshCommand(...).trim())
        
        If result is a multi line string, in some cases, the result must be converted
        to ArrayList to prevent "java.io.NotSerializableException: java.util.AbstractList$Itr".
        (e.g.  executeSshCommandOnHost (...).split("\\r?\\n").toList()) 
*/
def executeSshCommandOnHost (def command, def host, def preComment, def postComment) {
  // Tries to connect remote machine for 5 seconds. If it fails, exits with 255.
  def sshPrefix = "ssh -tt -o StrictHostKeyChecking=no -o ConnectTimeout=5 ${env.HJ_SSH_CREDENTIALS_USERNAME}@${host}"
  def result = null
  sshagent(["ssh-credentials"]) {
    println "${preComment}"
    result = sh script: "${sshPrefix} \"${command}\"", returnStdout: true
    println "${postComment}"
  }
  return result
}

/*
  Returns Git commit id.
  
  This should be performed at the point where you've checked out your 
  sources. A 'git' executable must be available. Please refer to 
  https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/gitcommit/gitcommit.groovy
*/
def getGitCommitId () {
  gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
  shortCommit = gitCommit.take(7)
  gitCommit = null
  return shortCommit
}

/*
  * Creates pipeline version from pom.xml, commit ID and Jenkins's build number:
  ** Pipeline version = pomMajorVersion<.pomMinorVersion><.pomIncrementalVersion><.otherVersion1>...<.otherVersionN>.getGitCommitId.buildNumber
  * Version in pom.xml must have at least major version such as 1-SNAPSHOT. Normally 
     we use major, minor and incremental versions such as 1.0.0-SNAPSHOT.
  ** Here are valid pom.xml versions: 1-SNAPSHOT, 1.0-SNAPSHOT, 1.0.0-SNAPSHOT, 1.0.0.0-SNAPSHOT, 1.0.0.0.VEDO-538-SNAPSHOT, ...
  * Sets env.PL_VERSION to pipeline version
  * Sets currentBuild.displayName to pipeline version
  * Sets Maven project's version to pipeline version
  * Returns pipeline version

  Note: setMavenBasedPipelineVersion () requires pom.xml. That 
        is why we need checkout has to be completed before calling this
        function.

  Note: "-SNAPSHOT" is removed from the pom.xml version.
*/
def setMavenBasedPipelineVersion () {
  def pomXml = readFile("pom.xml")
  def parsedPomXml = new XmlSlurper().parseText(pomXml)
  def versionText = parsedPomXml.version.text()
  println "Version of pom.xml: " + versionText

  def versionArray = versionText.replace("-SNAPSHOT", "").split("\\.")
  if (versionArray == null || versionArray.length == 0 || versionArray[0] == "") {
    error "Incorrect pom version for Maven based pipeline: " + versionText + ". It must have at least major version such as 1-SNAPSHOT."
  }

  // Variables are set to null to prevent 'java.io.NotSerializableException: groovy.util.slurpersupport.NodeChild'
  pomXml = null
  parsedPomXml = null

  for (i = 0; i < versionArray.length; i++) {
    if (i == 0) {
       env.PL_VERSION = versionArray[i]
    } else {
      env.PL_VERSION = env.PL_VERSION + "." + versionArray[i]
    }
  }

  env.PL_VERSION = env.PL_VERSION + '.' + getGitCommitId () + '.' + env.BUILD_NUMBER
  println "Pipeline version: " + env.PL_VERSION
  versionArray = null

  currentBuild.displayName = env.PL_VERSION

  sh "mvn versions:set -DnewVersion=${env.PL_VERSION}"

  return env.PL_VERSION
}

/*
  * Creates pipeline version from commit ID and Jenkins's build number: 
  ** pipeline version = getGitCommitId.buildNumber
  * Sets env.PL_VERSION to pipeline version
  * Sets currentBuild.displayName to pipeline version
  * Returns pipeline version
*/
def setPipelineVersion () {
  // pomMajorVersion.pomMinorVersion.pomIncrementalVersion.getGitCommitId.buildNumber
  env.PL_VERSION = getGitCommitId () + '.' + env.BUILD_NUMBER
  currentBuild.displayName = env.PL_VERSION
  return env.PL_VERSION
}

def getPipelineVersion () {
  return env.PL_VERSION
}

/*
  https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/maven-and-jdk-specific-version/mavenAndJdkSpecificVersion.groovy

  Apache Maven related side notes:
  --batch-mode : recommended in CI to inform maven to not run in interactive mode (less logs)
  -V : strongly recommended in CI, will display the JDK and Maven versions in use.
       Very useful to be quickly sure the selected versions were the ones you think.
  -U : force maven to update snapshots each time (default : once an hour, makes no sense in CI).
  -Dsurefire.useFile=false : useful in CI. Displays test errors in the logs directly (instead of
                             having to crawl the workspace files to see the cause).
*/
def compileMavenProject () {
  sh "mvn -B -V -U clean compile"
}

def testCompiledMavenProject () {
  sh "mvn -B verify org.jacoco:jacoco-maven-plugin:prepare-agent"
}

def checkQualityOfTestedMavenProject () {
  sh "mvn -B sonar:sonar -P sonar-profile -Dsonar.host.url=${env.HJ_SONAR_SERVER}"
}

def deployMavenArtifactsToArtifactory () {
  sh "mvn -B deploy -DskipTests=true"
}

/*
  Builds the image(latest) and tags it with pipeline version.

  If "latest" image on local machine is older than the "latest" image on
  registry, "FROM ....:latest" in Dockerfile causes local(older) image to be 
  used. To force the latest image from resgitry to be used, we pass --pull=true 
  option to build command. (See https://github.com/moby/moby/issues/4238)
*/
def buildDockerImage (def options) {
  println "[INFO] options: ${options}"
  sh "docker build ${options} --pull=true --build-arg HJ_BUILD_DATE=\"`date`\" --build-arg HJ_IMAGE=\"${env.PL_DOCKER_IMAGE}\" --build-arg HJ_VERSION=\"${env.PL_VERSION}\" -t ${env.HJ_DOCKER_REMOTE_REPO}/${env.PL_DOCKER_IMAGE} -t ${env.HJ_DOCKER_REMOTE_REPO}/${env.PL_DOCKER_IMAGE}:${env.PL_VERSION} ."
}

/*
  Pushes both latest and tagged image to Artifactory.
*/
def pushDockerImageToArtifactory () {
  sh "docker login --username ${env.HJ_AUTH_CREDENTIALS_USERNAME} --password ${env.HJ_AUTH_CREDENTIALS_PASSWORD} ${env.HJ_DOCKER_REMOTE_REPO};docker push ${env.HJ_DOCKER_REMOTE_REPO}/${env.PL_DOCKER_IMAGE}"
}


/* -------------------------------------------------------------------
   Jenkins Utilities
   -------------------------------------------------------------------*/
// https://issues.jenkins-ci.org/browse/JENKINS-34738
/*
  Sets the number of builds and artifacts to be kept by Jenkins. 
  The function simply sets "Discard old builds" properties ofof the job.
  
  WARNING: By desing, the properties step will remove all job properties 
           currently configured in this job, either from the UI or from 
           an earlier properties step.
           
           This includes configuration for discarding old builds, 
           parameters, concurrent builds and build triggers.
           
           See: https://groups.google.com/forum/#!topic/jenkinsci-issues/kbql47PFXvI   
*/
def setNumberToKeep (def number) {
  if (number != null && number != "") {
    properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: "${number}", daysToKeepStr: '', numToKeepStr: "${number}"]]])
  }
}

/*
  Returns "Max # of builds to keep" value of "Discard old builds" 
  settings in the job
  
  Reference: https://blog.sandra-parsick.de/2014/02/16/groovy-script-for-jenkins-script-console-to-add-or-modify-discard-old-builds-setting/
*/
def getNumToKeep () {
  def buildDiscarder = getJob().getBuildDiscarder() 
  return (buildDiscarder == null) ? null : buildDiscarder.numToKeep
}

def getJob () {
  return Jenkins.getInstance().getItemByFullName(env.JOB_NAME)
}

def getBuilds () {
  return getJob().getBuilds()
}

def getSuccessfulBuilds () {
  return getJob().getBuilds().overThresholdOnly(Result.SUCCESS).completedOnly()
}

/*
  Returns build versions to be deleted by Jenkins at the end of
  the current build. The function make calculations according
  to "Discard old builds" setting of the job and the builds marked 
  as "Keep this build forever".
  
  Note: Build's display name = Build's Version = Pipeline version.
*/
def getBuildVersionsToBeDeletedByJenkins () {
  def job = getJob ()
  def builds = job.getBuilds()
  def buildDiscarder = job.getBuildDiscarder() 
  def buildsToBeDeleted = []
  
  if (buildDiscarder != null) {
    int counter = 0
    for (build in builds) {
      counter++
      // By considering the current build, we need to use (counter > numToKeep)
      // instead of (counter >= numToKeep)
      if (counter > buildDiscarder.numToKeep && !build.isKeepLog()) {
        buildsToBeDeleted.add(build.displayName)
      }
    }
  }
  buildDiscarder = null
  builds = null
  job = null
  return buildsToBeDeleted
}

/*
  Returns build numbers of builds marked as "Keep this build forever".
*/
def getBuildNumbersMarkedAsKeepForever () {

  def buildNumbers = []
  
  // Only successful builds can be marked as "keep forever"
  for (build in getSuccessfulBuilds()) {
    if (build.isKeepLog()) {
      buildNumbers.add(build.number)
    }
  }

  return buildNumbers
}

/*
  Returns build versions those are not discarded by Jenkins at the 
  end of the current build. The function make calculations according
  to "Discard old builds" setting of the job and the builds marked 
  as "Keep this build forever".
  
  Jenkins do not discard following builds:
  - marked as "Keep this build forever"
  - last n builds(including currently running) conforming to 
    "Discard Old Builds" settings.
    
  Note: Build's display name = Build's Version = Pipeline version.
*/
def getBuildVersionsKeptByJenkins () {
  def job = getJob()
  def builds = job.getBuilds()
  def buildDiscarder = job.getBuildDiscarder()
  def buildsToBeKept = []
  
  if (buildDiscarder == null) {
    for (build in builds) {
      buildsToBeKept.add(build.displayName)
    }
  } else {
    int counter = 0
    for (build in builds) {
      counter++
      // By considering the current build, we need to use (counter <= numToKeep)
      // instead of (counter < numToKeep)
      if (counter <= buildDiscarder.numToKeep || build.isKeepLog()) {
        buildsToBeKept.add(build.displayName)
      }
    }
  }

  buildDiscarder = null
  builds = null
  job = null
  return buildsToBeKept
}

/*
  Enables "Keep this build forever" option for given build.  
*/
def keepBuildForever (def buildNumber) {
  def job = getJob()
  def builds = job.getBuilds()
  
  for (build in builds) {
    if (build.number == buildNumber) {
      build.keepLog(true)
      break
    }
  }
}

/*
Prints installed plugins and their versions to console in a format compatible 
with plugins.sh (https://github.com/jenkinsci/docker/blob/master/plugins.sh).

Sample output:
...
ace-editor:1.1
analysis-core:1.82
ant:1.4
antisamy-markup-formatter:1.5  
...
*/
def printInstalledPlugins () {
  def pluginList = []
  for (plugin in Jenkins.instance.pluginManager.plugins) {
    pluginList.add(plugin.getShortName() + ":" + plugin.getVersion())
  }
  
  def installedPlugins = ""
  for (plugin in pluginList.sort()) {
    installedPlugins = installedPlugins + plugin + "\r"
  }
  
  println installedPlugins
}


/* -------------------------------------------------------------------
   Utilities for Maven artifacts on Artifactory
   -------------------------------------------------------------------*/
/*
  Returns version list of the given artifact on release repository

  Test: curl -X GET -u <user>:<password> "http://mycompany.com/artifactory/api/search/versions?g=com.mycompany.myproject&a=myservice&repos=artifactoryMavenReleaseRepo"
*/
def getMavenArtifactVersionsOnArtifactory () {
  
  def command = "curl -X GET -u ${env.HJ_AUTH_CREDENTIALS_USERNAME}:${env.HJ_AUTH_CREDENTIALS_PASSWORD} \"${env.HJ_ARTIFACTORY_SERVER}/api/search/versions?g=${env.PL_MAVEN_GROUP}&a=${env.PL_MAVEN_ARTIFACT}&repos=${env.HJ_ARTIFACTORY_MAVEN_RELEASE_REPO}\""
  def jsonText = sh script: "${command}" , returnStdout: true

  // https://sourceforge.net/p/artifactory/mailman/message/33033898/ works with Scriptler but 
  // does not work in pipeline: 
  //   - new groovy.json.JsonSlurper().parseText(jsonText).results.version fails.
  //   - new groovy.json.JsonSlurper().parseText(jsonText).results.collect { it.version } requires @NonCPS. 
  def results = new groovy.json.JsonSlurper().parseText(jsonText).results
  def versions = []
  for (item in results) {
    versions.add(item.version)
  }
  results = null
  jsonText = null  
  return versions
}

/*
  Deletes given artifact on release repository

  Test: curl -X DELETE -v -u <user>:<password> "http://mycompany.com/artifactory/artifactoryMavenReleaseRepo/com/mycompany/myproject/myservice/0.0.1.8193108.106"
*/
def deleteMavenArtifactOnArtifactory (def version) {
  def command = "curl -X DELETE -v -u ${env.HJ_AUTH_CREDENTIALS_USERNAME}:${env.HJ_AUTH_CREDENTIALS_PASSWORD} \"${env.HJ_ARTIFACTORY_SERVER}/${env.HJ_ARTIFACTORY_MAVEN_RELEASE_REPO}/${env.PL_MAVEN_ARTIFACT_PATH}/${version}\""
  sh "${command}"
}

/*
  Deletes eligible artifacts from Artifactory. See @getBuildVersionsKeptByJenkins()
  for eligible artifacts.
*/
def discardMavenArtifactsOnArtifactory () {
  println "[INFO] discardMavenArtifactsOnArtifactory () is executing ..."
  def buildVersionsKeptByJenkins = getBuildVersionsKeptByJenkins ()
  def versions = getMavenArtifactVersionsOnArtifactory ()
  for (version in versions) {
    if (!buildVersionsKeptByJenkins.contains(version)) {
      println "${version} will be deleted from Artifactory..."
      deleteMavenArtifactOnArtifactory (version) 
    }
  }
  versions = null
  buildVersionsKeptByJenkins = null
}



/* -------------------------------------------------------------------
   Utilities for Maven artifacts on Maven local repository 
   -------------------------------------------------------------------*/
/*
  Returns all the versions but *-SNAPSHOT of the artifact on local 
  Maven repository
  
  Test: find $HOME/.m2/repository/com/mycompany/myproject/myservice/* -prune -not -name *-SNAPSHOT -type d -exec basename {} \;
*/
def getMavenArtifactVersionsOnLocalMavenRepository () {
  def command = "find \$HOME/.m2/repository/${env.PL_MAVEN_ARTIFACT_PATH}/* -prune -not -name *-SNAPSHOT -type d -exec basename {} \\;"
  def versions = sh script: "${command}" , returnStdout: true
  // Convert to ArrayList to prevent "java.io.NotSerializableException: java.util.AbstractList$Itr"
  return versions.split("\\r?\\n").toList()
}

/*
  Test: rm -rf /var/jenkins_home/.m2/repository/com/mycompany/myproject/myservice/0.0.1.9c53363.34
*/
def deleteMavenArtifactOnLocalMavenRepository (def version) {
  if (version != null && version != "")  {
    println "${version} will be deleted from local maven repository ..."
    def command = "rm -rf \${HOME}/.m2/repository/${env.PL_MAVEN_ARTIFACT_PATH}/${version}"
    sh "${command}"
  }
}

/*
  Deletes eligible artifacts from local Maven repository. See 
  @getBuildVersionsKeptByJenkins() for eligible artifacts.
*/
def discardMavenArtifactsOnLocalMavenRepository () {
  println "[INFO] discardMavenArtifactsOnLocalMavenRepository () is executing ..."
  def buildVersionsKeptByJenkins = getBuildVersionsKeptByJenkins ()
  def versions = getMavenArtifactVersionsOnLocalMavenRepository ()
  for (version in versions) {
    if (!buildVersionsKeptByJenkins.contains(version)) {
      deleteMavenArtifactOnLocalMavenRepository (version) 
    }
  }
  versions = null
  buildVersionsKeptByJenkins = null
}



/* -------------------------------------------------------------------
   Utilities for Docker images on Artifactory
   -------------------------------------------------------------------*/
/*
   Returns all the tags but 'latest' of the image from Artifactory.

   Test: curl -X GET -u <user>:<password> "http://mycompany.com/artifactory/api/docker/artifactoryDockerRepo/v2/mycompany/myproject/myservice/tags/list"
*/
def getDockerImageTagsOnArtifactory () {
  def command = "curl -X GET -u ${env.HJ_AUTH_CREDENTIALS_USERNAME}:${env.HJ_AUTH_CREDENTIALS_PASSWORD} \"${env.HJ_ARTIFACTORY_SERVER}/api/docker/${env.HJ_ARTIFACTORY_DOCKER_REPO}/v2/${env.PL_DOCKER_IMAGE}/tags/list\""
  def jsonText = sh script: "${command}" , returnStdout: true
  def tags = new groovy.json.JsonSlurper().parseText(jsonText).tags
  tags.removeElement("latest")
  jsonText = null  
  return tags
}

/*
  Deletes given tag on docker repository
  
  Test: curl -X DELETE -v -u <user>:<password> "http://mycompany.com/artifactory/api/docker/artifactoryDockerRepo/mycompany/myproject/myservice/0.0.1.8193108.106"
*/
def deleteDockerImageOnArtifactory (def tag) {

  def command = "curl -X DELETE -v -u ${env.HJ_AUTH_CREDENTIALS_USERNAME}:${env.HJ_AUTH_CREDENTIALS_PASSWORD} \"${env.HJ_ARTIFACTORY_SERVER}/${env.HJ_ARTIFACTORY_DOCKER_REPO}/${env.PL_DOCKER_IMAGE}/${tag}\""
  sh "${command}"
}

/*
  Deletes eligible image tags from Artifactory. See 
  @getBuildVersionsKeptByJenkins() for eligible image tags.
*/
def discardImagesOnArtifactory () {
  println "[INFO] discardImagesOnArtifactory () is executing ..."
  def buildVersionsKeptByJenkins = getBuildVersionsKeptByJenkins ()
  def tags = getDockerImageTagsOnArtifactory ()
  for (tag in tags) {
    if (!buildVersionsKeptByJenkins.contains(tag)) {
      println "${tag} will be deleted from Artifactory..."
      deleteDockerImageOnArtifactory (tag) 
    }
  }
  tags = null
  buildVersionsKeptByJenkins = null
}



/* -------------------------------------------------------------------
   Utilities for Docker images on host and swarm cluster
   -------------------------------------------------------------------*/
/*
  Test(on swarm manager node): docker node ls | tail -n +2 | awk '{ print $2 " " $3}'
*/
def getSwarmNodes () {
  def command = "docker node ls | tail -n +2 | awk '{ print \\\$2 \\\" \\\" \\\$3}'"
  def resultList = executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "").split("\\r?\\n")
  def nodes = []
  for (item in resultList) {
    subItems = item.tokenize(' ')
    if ("*".equals(subItems[0])) {
      nodes.add(subItems[1])
    } else {
      nodes.add(subItems[0])
    }
  }
  resultList = null
  return nodes
}

def updateSwarmService (def tag, def options) {

  def publicPort = getPublicPort ()

  // These are generic environment variables passed to every container.
  // Application running inside the container can use them if it is needed.
  def envOptions="--env-add ENV_NAME=${env.PL_DEPLOY_ENV} \
                  --env-add PROJECT_NAME=${env.PL_PROJECT_NAME} \
                  --env-add SERVICE_NAME=${env.PL_SERVICE_NAME} \
                  --env-add SWARM_SERVICE_NAME=${env.PL_SWARM_SERVICE_NAME} \
                  --env-add PUBLIC_PORT=${publicPort} \
                  --env-add VERSION=${env.PL_VERSION}"

  // SYS-6497
  if (tag == "latest") {
    options = "${options} --force"
  }

  def command = "docker service update \
                   ${envOptions} \
                   ${options} \
                   --image ${env.HJ_DOCKER_REMOTE_REPO}/${env.PL_DOCKER_IMAGE}:${tag} ${env.PL_SWARM_SERVICE_NAME}"
  
  def result = executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "")
  println result
}

def createSwarmService (def tag, def options) {

  def networkOptions = ""
  if (env.PL_SWARM_NETWORK != "") {
    createOverlayNetworkIfDoesNotExist (env.PL_SWARM_NETWORK)
    networkOptions = "--network=${env.PL_SWARM_NETWORK}"
  }

  def portOptions = ""
  def publicPort = getPublicPort ()
  if (publicPort != "") {
    portOptions = "-p ${publicPort}:${env.PL_SERVICE_PRIVATE_PORT}"
  }

  // These are generic environment variables passed to every container.
  // Application running inside the container can use them if it is needed.
  def envOptions ="-e ENV_NAME=${env.PL_DEPLOY_ENV} \
                   -e PROJECT_NAME=${env.PL_PROJECT_NAME} \
                   -e SERVICE_NAME=${env.PL_SERVICE_NAME} \
                   -e SWARM_SERVICE_NAME=${env.PL_SWARM_SERVICE_NAME} \
                   -e PUBLIC_PORT=${publicPort} \
                   -e VERSION=${env.PL_VERSION}"
  
  // GELF logging adapter sends generic information such as host, 
  // container name, image name etc. We additionally log generic 
  // environment variables(defined above)
  def loggingOptions = "--log-driver=gelf \
                        --log-opt gelf-address=udp://${env.PL_GRAYLOG_SERVER}:${env.PL_GRAYLOG_PORT} \
                        --log-opt env=ENV_NAME,PROJECT_NAME,SERVICE_NAME,SWARM_SERVICE_NAME,PUBLIC_PORT,VERSION"
  
  def command = "docker service create --name=${env.PL_SWARM_SERVICE_NAME} \
                   ${networkOptions} \
                   ${portOptions} \
                   ${envOptions} \
                   ${loggingOptions} \
                   ${options} \
                   ${env.HJ_DOCKER_REMOTE_REPO}/${env.PL_DOCKER_IMAGE}:${tag}"
  
  def result = executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "")
  println result
}

def deployImageToSwarm (def tag, def createOptions, def updateOptions) {
  if (isServiceExist(env.PL_SWARM_SERVICE_NAME)) {
    updateSwarmService(tag, updateOptions)
  } else {
    createSwarmService(tag, createOptions)
  }

  // TODO: Is the service really running? Check with similar functionality with the following.
  //docker inspect -f {{.State.Running}} vedo-$SERVICE_NAME
}

def createOverlayNetworkIfDoesNotExist (def network) {  
  def command = "docker network create -d overlay ${network}"
  def result = ""
  try {
    // If network exists, following command fails and catch block is executed.
    result = executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "")
  } catch (e) { 
    // do nothing 
  }  
  println result
}

/*
  Executes "docker system prune" command on each node: https://docs.docker.com/engine/reference/commandline/system_prune/     
 */
def doCleanupOnSwarm () {
  def nodes = getSwarmNodes()
  def command = "docker system prune -af"
  for (node in nodes) {
    result = executeSshCommandOnHost (command, node, "", "")
    println result
  }
}

/*
  Returns following values as service status:
    * 1 (STATUS.SERVICE_DOES_NOT_EXIST): If service does not exist.
    * 2 (STATUS.SERVICE_RUNNING): If service is running.     
    * 3 (STATUS.SERVICE_IS_NOT_RUNNING): If service exists but is not running.
 */
def getServiceStatus (def service) {
  def command = "docker service ls | grep -w ${service} | awk '{print \\\$4 }' | cut -d/ -f1"
  def result = executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "").trim()
  if (result == "") {
    return STATUS.SERVICE_DOES_NOT_EXIST
  } else if (result == "0") {
    return STATUS.SERVICE_IS_NOT_RUNNING
  } else {
    return STATUS.SERVICE_RUNNING
  }
}

def isServiceRunning (def service) {
  return (getServiceStatus(service) == STATUS.SERVICE_RUNNING)
}

def isServiceExist (def service) {
  return (getServiceStatus(service) != STATUS.SERVICE_DOES_NOT_EXIST)
}

def isDependentServicesRunning (def dependentServices) {
  for (service in dependentServices) {
    if (!isServiceRunning(service)) {
      println "Dependent service [${service}] is not running."
      return false
    }
  }
  return true    
}

def failIfDependentServicesAreNotRunning (def dependentServices) {
  if (!isDependentServicesRunning(dependentServices)) {
    error("Build failed because at least one dependent service is not running. Dependent services: ${dependentServices}")
  }
}

/*
  Starts given service. The service must have already be created.
  If replicas parameter is not provided, 1 replica is created. 
*/
def startService (def service, def replicas) {  
  if (replicas == null || replicas == "") {
    replicas = 1
  }
  def command = "docker service scale ${service}=${replicas}"
  executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "")
}

def stopService (def service) {  
  def command = "docker service update --detach=false --replicas 0 ${service}"
  executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "")
}

def deleteService (def service) {
  def command = "docker service rm ${service}"
  executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "")
}

def stopAllServices () {  
  def command = "for i in $(docker service ls -q); do docker service update --detach=false --replicas 0 $i; done"
  executeSshCommandOnHost (command, env.PL_SWARM_MANAGER_NODE, "", "")
}

initialize ()

return this;