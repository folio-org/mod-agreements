package org.olf

import com.k_int.okapi.OkapiHeaders
import com.k_int.web.toolkit.testing.HttpSpec
import org.olf.dataimport.internal.TitleInstanceResolverService

import groovyx.net.http.HttpException
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

@Stepwise
abstract class BaseSpec extends HttpSpec {
  def setupSpec() {
    httpClientConfig = {
      client.clientCustomizer { HttpURLConnection conn ->
        conn.connectTimeout = 3000
        conn.readTimeout = 20000
      }
    }
    addDefaultHeaders(
      (OkapiHeaders.TENANT): "${this.class.simpleName}",
      (OkapiHeaders.USER_ID): "${this.class.simpleName}_user"
    ) 
  }
  
  Map<String, String> getAllHeaders() {
    specDefaultHeaders + headersOverride
  }
  
  String getCurrentTenant() {
    allHeaders?.get(OkapiHeaders.TENANT)
  }
  
  // Call do delete - it doesn't matter if it doesn't work because the tenant doesn't exist
  void 'Pre purge tenant' () {
    boolean resp = false
    when: 'Purge the tenant'
      try {
        resp = doDelete('/_/tenant', null)
        resp = true
      } 
      catch (HttpException ex) { 
        resp = true 
      }
      catch (Throwable t) { 
        resp = true 
      }
      
    then: 'Response obtained'
      resp == true
  }
  
  void 'Ensure test tenant' () {
    
    when: 'Create the tenant'
      def resp = doPost('/_/tenant', {
      parameters ([["key": "loadReference", "value": true]])
    })

    then: 'Response obtained'
    resp != null

    and: 'Refdata added'

      List list
      // Wait for the refdata to be loaded.
      def conditions = new PollingConditions(timeout: 10)
      conditions.eventually {
        (list = doGet('/erm/refdata')).size() > 0
      }
  }

  private static String baseTIRSPath = "org.olf.dataimport.internal.titleInstanceResolvers"
  static String TITLE_TIRS = "${baseTIRSPath}.TitleFirstTIRSImpl"
  static String ID_TIRS = "${baseTIRSPath}.IdFirstTIRSImpl"

  // TIRS gets injected as a spring bean, this can help figure out which is being used
  // Used to work out which TIRS we're using
  TitleInstanceResolverService titleInstanceResolverService
  def injectedTIRS() {
    titleInstanceResolverService?.class?.name
  }
}
