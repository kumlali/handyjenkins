/*
 Based on https://github.com/Accenture/adop-jenkins/blob/master/resources/init.groovy.d/adop_general.groovy
*/
import jenkins.model.*

def env = System.getenv ()
def rootUrl = env['HJ_ROOT_URL']

Thread.start {  
  if (rootUrl != null) {
    println "[HJ] --> Configuring Jenkins URL..."
    jlc = JenkinsLocationConfiguration.get ()
    jlc.setUrl (rootUrl)
    jlc.save ()  
    println "[HJ] --> Jenkins URL has been set to: " + rootUrl
  }
}
