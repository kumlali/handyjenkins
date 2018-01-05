/*
 Based on https://github.com/jenkinsci/docker
*/
import jenkins.model.*

def env = System.getenv ()
def executerCount = env['EXECUTOR_COUNT'] ?: "10"
def instance = Jenkins.getInstance ()

println "[HJ] --> Configuring executor count..."
instance.setNumExecutors (executerCount.toInteger ())
instance.save ()
println "[HJ] --> Executor count updated to " + executerCount