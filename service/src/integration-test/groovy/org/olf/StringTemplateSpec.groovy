package org.olf

import org.olf.general.StringTemplate
import org.olf.kb.Platform
import org.olf.kb.PlatformTitleInstance

import com.k_int.okapi.OkapiTenantResolver

import groovy.json.JsonSlurper

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class StringTemplateSpec extends BaseSpec {

  ImportService importService

  // Place to store the id of the PTI we load in the package for use in multiple tests
  
  void "Load Packages" (test_package_file) {
    when: 'File loaded'

      def jsonSlurper = new JsonSlurper()
      def package_data = jsonSlurper.parse(new File(test_package_file))
      int result = 0
      final String tenantid = currentTenant.toLowerCase()
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
        result = importService.importPackageUsingInternalSchema( package_data )
      }

    then: 'Package imported'
      result > 0

    where:
      test_package_file | _
      'src/integration-test/resources/packages/stringTemplating/simple_pkg.json' | _
  }

  void "Test creation of StringTemplates" (
    final String the_name,
    final String the_rule,
    final String the_context
  ) {

    final String tenantid = currentTenant.toLowerCase()
    when: "Post to create new string template"
      Map respMap = doPost("/erm/sts", {
        'name' the_name
        'rule' the_rule
        'context' the_context // This can be the value or id but not the label
      })
    then: "Response is good, we have a new ID, and name/rule/context match input"
      respMap.id != null
      respMap.name == the_name
      respMap.rule == the_rule
      respMap.context.value == the_context
    where:
      the_name   || the_rule                                                                                                  || the_context
      'proxy1'   || 'http://sub-hh-{{replace (replace (removeProtocol inputUrl) \"link.\" \"\") \".com\" \".co.uk\"}}/proxy1' || 'urlproxier'
      'proxy2'   || 'proxy-2-stuff-{{platformLocalCode}}'                                                                     || 'urlproxier'
  }


  void "Test StringTemplate url manipulation" () {

    when: "Fetch list of PTIs"
      def pti = fetchPTI()
    then: "PTI exists with non-null url--and templatedUrls are empty"
      pti != null
      pti.id != null
      pti.url != null
      pti.templatedUrls.size() == 0

    when: "templating task is called"
      pti = fetchPTIWithRefresh()
      def templatedUrls = pti.templatedUrls

    then: "templatedUrls should now have 3 entries, one for each proxy and one defaultUrl"
      templatedUrls.size() == 3
    then: "templatedUrls should have the correctly manipulated urls in them"
      def proxy1TemplatedUrl = templatedUrls.findAll { tu ->
        tu.name == 'proxy1'
      }[0]
      def proxy2TemplatedUrl = templatedUrls.findAll { tu ->
        tu.name == 'proxy2'
      }[0]
      def defaultTemplatedUrl = templatedUrls.findAll { tu ->
        tu.name == 'defaultUrl'
      }[0]

      proxy1TemplatedUrl.url == 'http://sub-hh-springer.co.uk/10.1007/978-3-319-55227-9/proxy1'
      proxy2TemplatedUrl.url == 'proxy-2-stuff-'
      defaultTemplatedUrl.url == 'http://link.springer.com/10.1007/978-3-319-55227-9'
      // This is sufficient to check that the manipulation part is working for ALL contexts, since they're the same mechanism
  }

  void "Test TemplatedUrl refreshing" () {
     when: "templating task is called"

      def pti = fetchPTIWithRefresh()
      def templatedUrls = pti.templatedUrls
    then: "PTI should have 3 templates still, not 6"
      templatedUrls.size() ==3

    when: 'We edit the rule of proxy1'
      def sts = doGet("/erm/sts")
      def proxy1STS = sts.findAll { st ->
        st.name == 'proxy1'
      }[0]

      proxy1STS = doPut("/erm/sts/${proxy1STS.id}", {
        'rule' 'http://ethan-{{replace (removeProtocol inputUrl) \".com\" \".co.uk\"}}'
      })

      pti = fetchPTIWithRefresh()
      templatedUrls = pti.templatedUrls

    then: 'PTI proxy1 templatedUrl has changed'
      def proxy1TU = templatedUrls.findAll { tu ->
        tu.name == 'proxy1'
      }[0]

      proxy1TU.url == 'http://ethan-link.springer.co.uk/10.1007/978-3-319-55227-9'


    when: 'we edit proxy 1 to include the PTI platform as a scope.'

      proxy1STS = doPut("/erm/sts/${proxy1STS.id}", {
        'idScopes' ([pti.platform.id])
      })
      pti = fetchPTIWithRefresh()
      templatedUrls = pti.templatedUrls
    then: 'templated urls should not include proxy1'
      templatedUrls.any{ tu ->
        tu.name == 'proxy1'
      } == false

    when: 'we create a url customiser NOT linked to the platform'
      doPost("/erm/sts", {
        'name' 'customiser1'
        'rule' "http://customise-me:{{replace inputUrl \"a\" \"b\"}}"
        'context' 'urlcustomiser' // This can be the value or id but not the label
      })
      pti = fetchPTIWithRefresh()
      templatedUrls = pti.templatedUrls
    then: 'templated urls should not change length'
      // Should only contain proxy2, defaultUrl, size() == 2
      templatedUrls.size() == 2
  }

  void "Test customiser-proxy interaction" () {
    def sts = doGet("/erm/sts")
    def customiser = sts.findAll { st ->
      st.name == 'customiser1'
    }[0]
    def pti = fetchPTIWithRefresh()

    when: "we add the PTI to the customiser's idScopes"
      customiser = doPut("/erm/sts/${customiser.id}", {
        'idScopes' ([pti.platform.id])
      })
      pti = fetchPTIWithRefresh()
      def templatedUrls = pti.templatedUrls
    then: 'expect to see 4 templatedUrls defaultUrl, proxy 2, customiser1 and proxy2-customiser1'
      templatedUrls.size() == 4
      // customiser1 exists
      templatedUrls.any{ tu ->
        tu.name == 'customiser1'
      } == true
      // customiser1 is proxied by proxy2
      templatedUrls.any{ tu ->
        tu.name == 'proxy2-customiser1'
      } == true
    
    when: "we remove the PTI from proxy1's idScopes"
      def proxy1STS = sts.findAll { st ->
        st.name == 'proxy1'
      }[0]
      proxy1STS = doPut("/erm/sts/${proxy1STS.id}", {
        'idScopes' ([""])
        // TODO right now there's a bug where we can't PUT an empty list to a @BindImmutably field and have it overwrite
        // This serves as a workaround,since empty string won't match any ids
      })
      pti = fetchPTIWithRefresh()
      templatedUrls = pti.templatedUrls

    then: 'expect to see 6 templatedUrls defaultUrl, proxy1, proxy 2, customiser1, proxy1-customiser1 and proxy2-customiser1'
      templatedUrls.size() == 6
      // customiser1 is proxied by proxy1
      templatedUrls.any{ tu ->
        tu.name == 'proxy1-customiser1'
      } == true
  }


  void "Test smart templatedUrl comparison" () {
    // Test that we're not deleting those templatedUrls which don't change

    def sts = doGet("/erm/sts")
    def proxy1STS = sts.findAll { st ->
      st.name == 'proxy1'
    }[0]
    def pti = fetchPTIWithRefresh()

    proxy1STS = doPut("/erm/sts/${proxy1STS.id}", {
      'idScopes' ([pti.platform.id])
    })
    pti = fetchPTIWithRefresh()
    def templatedUrlIds = pti.templatedUrls.collect { tu -> tu.id }

    when: "we update proxy1"
      proxy1STS = doPut("/erm/sts/${proxy1STS.id}", {
        'name' 'proxy-1-test'
      })
      pti = fetchPTIWithRefresh()
      def templatedUrlIds2 = pti.templatedUrls.collect { tu -> tu.id }

    then: "the original templated urls don't get updated"
      // Make sure they're sorted in the same order
      templatedUrlIds.sort() == templatedUrlIds2.sort()
  }

  def fetchPTIWithRefresh() {
    doGet("/erm/sts/template")
    // Wait for 5 seconds for this to be done
    //TODO this is not great -- need a way to programatically tell if templating is finished?
    Thread.sleep(5000);

    return fetchPTI()
  }

  def fetchPTI() {
      // Find the PTI we imported from the package ingest
      def ptis = doGet("/erm/pti", [
        filters:[
          "titleInstance.name=i=Arithmetic of Finite Fields" // Case insensitive match
        ]
      ])
      return ptis[0]
  }
}

