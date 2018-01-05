import hudson.model.*
import hudson.security.*
import hudson.security.csrf.DefaultCrumbIssuer
import hudson.util.Secret
import jenkins.model.*
import jenkins.security.plugins.ldap.*
import jenkins.security.s2m.*
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import com.cloudbees.plugins.credentials.SystemCredentialsProvider

def env = System.getenv ()
def instance = Jenkins.getInstance ()

/* 
 * Configure Project-based Matrix Authorization Strategy
 * 
 * Based on https://gist.github.com/xbeta/e5edcf239fcdbe3f1672#file-00-set-authorization-groovy     
 */

// JVM did not like 'hypen' in the class name, it will crap out saying it is
// illegal class name.
class BuildPermission {
  static buildNewAccessList (userOrGroup, permissions) {
    def newPermissionsMap = [:]
    permissions.each {
      newPermissionsMap.put (Permission.fromId (it), userOrGroup)
    }
    return newPermissionsMap
  }
}


Thread.start {

  sleep 10000
  
  if (env['HJ_ENABLE_SECURITY'] != "true") {
    instance.disableSecurity ()
    println "[HJ] --> Security disabled."
  } else {
    /* 
     * Configure LDAP
     * 
     * Based on https://github.com/Accenture/adop-jenkins/blob/master/resources/init.groovy.d/adop_ldap.groovy   
     */
    if (instance.pluginManager.activePlugins.find {it.shortName == "ldap"} != null) {
      println "[HJ] --> Configuring LDAP..."
  
      def ldapServer = env['HJ_LDAP_SERVER']
      def ldapRootDN = env['HJ_LDAP_ROOTDN']
      def ldapUserSearchBase = env['HJ_LDAP_USER_SEARCH_BASE']
      def ldapUserSearch = env['HJ_LDAP_USER_SEARCH']
      def ldapGroupSearchBase = env['HJ_LDAP_GROUP_SEARCH_BASE']
      def ldapGroupSearchFilter = env['HJ_LDAP_GROUP_SEARCH_FILTER']
      def ldapGroupMembershipFilter = env['HJ_LDAP_GROUP_MEMBERSHIP_FILTER']
      def ldapManagerDN = env['HJ_LDAP_MANAGER_DN']
      def ldapManagerPassword = env['HJ_LDAP_MANAGER_PASSWORD']
      def ldapInhibitInferRootDN = env['HJ_LDAP_INHIBIT_INFER_ROOTDN']
      def ldapDisableMailAddressResolver = env['HJ_LDAP_DISABLE_MAIL_ADDRESS_RESOLVER']
      def ldapDisplayNameAttributeName = env['HJ_LDAP_DISPLAY_NAME_ATTRIBUTE_NAME']
      def ldapMailAddressAttributeName = env['HJ_LDAP_MAIL_ADDRESS_ATTRIBUTE_NAME']
      
      if (ldapServer == null || ldapServer == "") {
        println "[HJ] --> LDAP was not configured because HJ_LDAP_SERVER has not been defined."
      } else {
        def ldapRealm = new LDAPSecurityRealm (
          ldapServer, //String server
          ldapRootDN, //String rootDN
          ldapUserSearchBase, //String userSearchBase
          ldapUserSearch, //String userSearch
          ldapGroupSearchBase, //String groupSearchBase
          ldapGroupSearchFilter, //String groupSearchFilter
          new FromGroupSearchLDAPGroupMembershipStrategy (ldapGroupMembershipFilter), //LDAPGroupMembershipStrategy groupMembershipStrategy
          ldapManagerDN, //String managerDN
          Secret.fromString (ldapManagerPassword), //Secret managerPasswordSecret
          ldapInhibitInferRootDN.toBoolean () , //boolean inhibitInferRootDN
          ldapDisableMailAddressResolver.toBoolean (), //boolean disableMailAddressResolver
          null, //CacheConfiguration cache
          null, //EnvironmentProperty[] environmentProperties
          ldapDisplayNameAttributeName, //String displayNameAttributeName
          ldapMailAddressAttributeName, //String mailAddressAttributeName
          IdStrategy.CASE_INSENSITIVE, //IdStrategy userIdStrategy
          IdStrategy.CASE_INSENSITIVE //IdStrategy groupIdStrategy >> defaults
        )

        instance.setSecurityRealm (ldapRealm)
        println "[HJ] --> LDAP configured."

      }
      
    }

    /* 
     * Enable Slave -> Master Access Control
     * 
     * Based on http://stackoverflow.com/a/41588568/5903564   
     */
    println "[HJ] --> Enabling master access control..."  
    instance.injector.getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)
    println "[HJ] --> Master access control enabled."  
 
  
    /* 
     * Configure Project-based Matrix Authorization Strategy
     * 
     * Based on https://gist.github.com/xbeta/e5edcf239fcdbe3f1672#file-00-set-authorization-groovy     
     */
    if (instance.pluginManager.activePlugins.find {it.shortName == "matrix-auth"} != null) {
      println "[HJ] --> Configuring project matrix authorization strategy..."
  
      strategy = new hudson.security.ProjectMatrixAuthorizationStrategy ()
  
      //---------------------------- anonymous ----------------------------------
      // NOTE: It is very bad to let anonymous to install/upload plugins, but
      // that's how our chef run as to install plugins. :-/
      anonymousPermissions = [
        "hudson.model.Hudson.Read",
        "hudson.model.Item.Read"
      ]
      anonymous = BuildPermission.buildNewAccessList ("anonymous", anonymousPermissions)
      anonymous.each {p, u -> strategy.add (p, u)}
  
      //------------------- administrator permissions --------------------------------------
      administrators = env['HJ_ADMINISTRATORS']
      if (administrators != null && administrators != "") {
        administorPermissions = [
          "hudson.model.Hudson.Administer"
        ]
        administrator = BuildPermission.buildNewAccessList (administrators, administorPermissions)
        administrator.each {p, u -> strategy.add (p, u)}
      }  
  
      //------------------- developer permissions --------------------------------------
      developers = env['HJ_DEVELOPERS']
      if (developers != null && developers != "") {
        developerPermissions = [
          "hudson.model.Hudson.Read",
          "hudson.model.Hudson.RunScripts",
          "hudson.model.Item.Build",
          "hudson.model.Item.Read"
        ]
        developer = BuildPermission.buildNewAccessList (developers, developerPermissions)
        developer.each {p, u -> strategy.add (p, u)}
      }  
  
      // Set the strategy globally
      instance.setAuthorizationStrategy (strategy)
      println "[HJ] --> Project matrix authorization strategy configured."
    }
  }
  
  /*
   * This configuration prevents some problems especially when a load balancer exists in front of Jenkins.
   * 
   * Based on https://github.com/samrocketman/jenkins-bootstrap-jervis/blob/master/scripts/configure-csrf-protection.groovy
   * 
   * Manuel steps to do the same thing: 
   * - Select "Configure Global Security"
   * - Enable "Prevent Cross Site Request Forgery exploits"
   * - Select "Default Crumb Issuer" as "Crumb Algorithm"
   * - Enable "Enable proxy compatibility"
   */  
  println "[HJ] --> Enabling CSRF protection and proxy compability..."
  instance.setCrumbIssuer (new DefaultCrumbIssuer (true))
  println "[HJ] --> CSRF protection and proxy compability enabled."  

  /* 
   * Disable CLI over remoting
   * 
   * Based on https://support.cloudbees.com/hc/en-us/articles/234709648-Disable-Jenkins-CLI4   
   */
  println "[HJ] --> Disabling CLI over remoting..."  
  jenkins.CLI.get().setEnabled(false)
  println "[HJ] --> CLI over remoting disabled."  

  /* 
   * Disable old Non-Encrypted agent protocols 
   * 
   * Based on https://issues.jenkins-ci.org/browse/JENKINS-45841 and https://git.io/vbbM5
   */
  HashSet<String> newProtocols = new HashSet<>(instance.getAgentProtocols())
  newProtocols.removeAll(Arrays.asList(
    "JNLP3-connect", "JNLP2-connect", "JNLP-connect", "CLI-connect"   
  ))
  instance.setAgentProtocols(newProtocols)


  // Save the state
  instance.save ()

}