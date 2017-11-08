import jenkins.model.*
import hudson.model.ListView

// Variables

// Constants
def mngJobs = ["do-swarm-cleanup", "print-installed-plugins"]
def testJobs = ["color-test",
                "docker-image-inside-test", 
                "docker-test",
                "flyway-test",
                "git-checkout-test", 
                "maven-test", 
                "remote-file-loader-plugin-test",
                "ssh-test"]


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
  addView ("[HJ] - Setup Tests", testJobs)
  addView ("[HJ] - Management", mngJobs)  
}
