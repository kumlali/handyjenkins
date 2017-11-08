#!groovy

node {

  timestamps {

    stage ("Initialize") {

      // We intentionally load the script from SCM instead of from /tools/scripts directory.
      // Otherwise it would be too difficult and cumbursome to correct mistakes in utilities.groovy 
      // that breaks handyjenkins's build.
      UTILITIES = fileLoader.fromGit("scripts/utilities.groovy", "https://github.com/kumlali/handyjenkins.git", "master", "", "")

      // Set project &/ service specific definitions
      env.PL_PROJECT_NAME = ""
      env.PL_SERVICE_NAME = "handyjenkins"  
      env.PL_SERVICE_PRIVATE_PORT = ""
      // If public port does not change depending on the environment(DEV, 
      // ALPHA, ...) set following variable. (See UTILITIES.getPublicPort ())
      env.PL_SERVICE_PUBLIC_PORT = ""
      // If public port must be different for each environment(DEV, ALPHA, ...)
      // set following variable. (See UTILITIES.getPublicPort ())
      env.PL_SERVICE_PUBLIC_PORT_BASE = ""
      
      env.PL_MAVEN_GROUP = ""
      env.PL_MAVEN_ARTIFACT = ""
      env.PL_MAVEN_ARTIFACT_PATH = ""
      env.PL_DOCKER_IMAGE = "kumlali/handyjenkins"
      env.PL_SWARM_MANAGER_NODE = ""
      env.PL_SWARM_SERVICE_NAME = ""
      env.PL_SWARM_NETWORK = ""
      env.PL_GRAYLOG_SERVER = ""
      env.PL_GRAYLOG_PORT = ""

      checkout scm
      UTILITIES.setPipelineVersion ()
      UTILITIES.printEnvironmentVariables ()
    }

    stage ("Build Docker Image") {
      def options = ""
      UTILITIES.buildDockerImage (options)
      UTILITIES.pushDockerImageToArtifactory ()
      UTILITIES.discardImagesOnArtifactory()
    }

  }

}