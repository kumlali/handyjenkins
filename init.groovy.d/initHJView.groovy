import jenkins.model.*
import hudson.model.ListView

// Variables
def env = System.getenv ()

// Constants
def hjJobs = ["color-test",
              "docker-image-inside-test", 
              "docker-test",
              "flyway-test",
              "git-checkout-test", 
              "maven-test", 
              "remote-file-loader-plugin-test",
              "ssh-test",
              "do-swarm-cleanup", 
              "print-installed-plugins",
              "regional-settings-test"]


def deleteJobs (def jobs) {
  for (jobName in jobs) {
    job = Jenkins.instance.getItemByFullName(jobName)
    if (job != null) {
      job.delete ()
      println "[HJ] --> Job '" + jobName + "' deleted."
    }
  }
}

def addView (def viewName, def jobs) {
  if (Jenkins.instance.getView (viewName) == null) {
    Jenkins.instance.addView(new ListView(viewName))
    
    boolean atLeastOneJobAdded = false
    for (jobName in jobs) {
      job = Jenkins.instance.getItemByFullName(jobName)
      if (job != null) {
        Jenkins.instance.getView (viewName).add(job)
        atLeastOneJobAdded = true
      }
    }
  
    if (atLeastOneJobAdded) {
      println "[HJ] --> View '" + viewName + "' added."
    } else {
      Jenkins.instance.deleteView(instance.getView (viewName))
    } 
  }
}

Thread.start {
  if (env['HJ_CREATE_HJ_VIEW']=="true") {
    println "[HJ] --> Creating HJ view and adding pre-installed jobs to it..."
    addView ("[HJ]", hjJobs)
  } else {
    deleteJobs (hjJobs)
    println "[HJ] --> HJ_CREATE_HJ_VIEW is not true. Therefore, HJ view was not created and pre-installed jobs were deleted."
  }
}
