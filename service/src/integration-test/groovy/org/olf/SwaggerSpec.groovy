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
class SwaggerSpec extends BaseSpec {

  void "Test swagger docs" () {

    when: "we update proxy1"
      Map resp = doGet("/erm/swagger/api")

    then: 'we check that the expected number of log events are returned'
      log.debug("Got response ${resp}");
  }

}

