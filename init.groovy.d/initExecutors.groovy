/*
 Based on https://github.com/jenkinsci/docker
*/

import jenkins.model.*

//Variables
def env = System.getenv ()
def executerCount = env['EXECUTOR_COUNT'] ?: "10"

//Constants
def instance = Jenkins.getInstance ()

println "[HJ] --> Configuring executor count..."

instance.setNumExecutors (executerCount.toInteger ())

// Save the state
instance.save ()

println "[HJ] --> Executor count updated to " + executerCount