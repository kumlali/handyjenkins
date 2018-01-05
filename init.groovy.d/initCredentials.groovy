/*
 Based on https://github.com/Accenture/adop-jenkins/blob/master/resources/init.groovy.d/adop_general.groovy
*/
import jenkins.model.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

def env = System.getenv ()
def instance = Jenkins.getInstance ()

Thread.start {
  def systemCredsProvider = SystemCredentialsProvider.getInstance ()


  // ------------------ Authentication credentials ----------------------
  println "[HJ] --> Registering auth-credentials..."

  def authCredsId = "auth-credentials"
  def authCredsExist = false
  systemCredsProvider.getCredentials ().each {
    creds = (com.cloudbees.plugins.credentials.Credentials) it
    if (creds.getId () == authCredsId) {
      authCredsExist = true
      println "[HJ] --> auth-credentials is already registered."    
    }
  }

  if (!authCredsExist) {    
    def authCredsDescription = "Credentials for authenticating to SVN, Gogs, JIRA, FishEye, etc."
    def authCredsScope = CredentialsScope.GLOBAL
    def authCredsUsername = env['HJ_AUTH_CREDENTIALS_USERNAME']
    def authCredsPassword = env['HJ_AUTH_CREDENTIALS_PASSWORD']
    
    if (authCredsUsername == null || authCredsUsername == "") {
      println "[HJ] --> auth-credentials was not registered because HJ_AUTH_CREDENTIALS_USERNAME has not been defined."
    } else {
      def authCredsDomain = com.cloudbees.plugins.credentials.domains.Domain.global ()
      def authCredsUsernamePassCreds = new UsernamePasswordCredentialsImpl (authCredsScope, 
                                                                            authCredsId, 
                                                                            authCredsDescription, 
                                                                            authCredsUsername, 
                                                                            authCredsPassword)
      systemCredsProvider.addCredentials (authCredsDomain, authCredsUsernamePassCreds)
      println "[HJ] --> auth-credentials registered."
    }
  }


  // ------------------ SSH credentials ----------------------
  println "[HJ] --> Registering ssh-credentials..."
  
  def sshCredsId = "ssh-credentials"
  def sshCredsExist = false
  systemCredsProvider.getCredentials ().each {
    creds = (com.cloudbees.plugins.credentials.Credentials) it
    if (creds.getId () == sshCredsId) {
      sshCredsExist = true
      println "[HJ] --> ssh-credentials is already registered."    
    }
  }
  
  if (!sshCredsExist) {
    def sshCredsDescription = "Credentials for ssh."
    def sshCredsScope = CredentialsScope.GLOBAL
    def sshCredsUsername = env['HJ_SSH_CREDENTIALS_USERNAME']
    def sshCredsPrivateKeyFile = "/hj/ssh/id_rsa"
    if (sshCredsUsername == null || sshCredsUsername == "" || sshCredsPrivateKeyFile == null || sshCredsPrivateKeyFile == "") {
      println "[HJ] --> ssh-credentials was not registered because HJ_SSH_CREDENTIALS_USERNAME and/or HJ_SSH_CREDENTIALS_PRIVATE_KEY_FILE has not been defined."
    } else {
      def sshCredsPrivateKeySource = new BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource (sshCredsPrivateKeyFile)    
      def sshCredsPrivateKeyPassphrase = null
      def sshCredsDomain = com.cloudbees.plugins.credentials.domains.Domain.global()
      def sshCredsBasicSSHUserPrivateKey = new BasicSSHUserPrivateKey (sshCredsScope, 
                                                                      sshCredsId,
                                                                      sshCredsUsername, 
                                                                      sshCredsPrivateKeySource, 
                                                                      sshCredsPrivateKeyPassphrase, 
                                                                      sshCredsDescription)
      systemCredsProvider.addCredentials (sshCredsDomain, sshCredsBasicSSHUserPrivateKey)
      println "[HJ] --> ssh-credentials registered."    
    }
  }
    
  // Save the state
  instance.save ()
}
