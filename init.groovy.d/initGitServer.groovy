import jenkins.model.*
import org.jenkinsci.plugin.gitea.servers.GiteaServer
import org.jenkinsci.plugin.gitea.servers.GiteaServers

def env = System.getenv ()
def instance = Jenkins.getInstance ()

Thread.start {
  if (instance.pluginManager.activePlugins.find {it.shortName == "gitea"} != null) {
    println "[HJ] --> gitea-plugin found. Gitea server will be configured..."
    GiteaServer giteaServer = new GiteaServer ("gitea", env['HJ_GIT_SERVER'], false, "auth-credentials")
    GiteaServers.get().addServer(giteaServer)
    instance.save ()
    println "[HJ] --> Gitea server has been configured."
  } else {
    println "[HJ] --> gitea-plugin could not be found. Gitea server will not be configured."
  }
}
