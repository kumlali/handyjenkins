/*
 Based on https://github.com/geerlingguy/ansible-role-jenkins/issues/28

 We use URL() method to parse given proxy url. Therefore, userName and password
 can be used in proxy url. Sample urls:
   
   * http://user:pass@proxy.example.com
   * http://proxy.example.com  
*/
import jenkins.model.*

def env = System.getenv ()
def instance = Jenkins.getInstance ()

if (instance.proxy == null) {
  println "[HJ] --> Configuring proxy settings..."

  def httpProxy = env['HTTP_PROXY'] ?: env['http_proxy']
  if (httpProxy == null || httpProxy == "") {
    println "[HJ] --> Proxy has not been configured as there is no HTTP_PROXY defined."
  } else {
  
    URL url = new URL(httpProxy);

    def name = url.getHost()
    def port = url.getPort()
    def noProxyHost = env['NO_PROXY'] ?: env['no_proxy']

    def userName, password
    String userInfo = url.getUserInfo();
    if (userInfo != null && userInfo.length() > 0) {
      String[] split = userInfo.split(':');
      if (split != null) {
        if (split.length >= 1) {
          userName = split[0];
        }
        if (split.length >= 2) {
          password = split[1];
        }
      }
    }

    def pc = new hudson.ProxyConfiguration(name, port.toInteger (), userName, password, noProxyHost)
    pc.save () // needed to persist proxy configuration as proxy.xml

    instance.proxy = pc
    instance.save()

    println "[HJ] --> Proxy settings configured." 
  }
}

