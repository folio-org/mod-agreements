package org.olf

import org.olf.kb.Platform

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration
@Stepwise
class PlatformSpec extends BaseSpec {

  @Unroll
  void "Test Platform #name creation" ( final String platformUrl, final String name ) {
    final String tenantid = currentTenant.toLowerCase()
    when: 'Resolve platform from url #platformUrl'
    
      def platform = null
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
        platform = Platform.resolve(platformUrl)
      }

    then: 'Name is #name'
      platform.name == name
    then: 'Local code is #localCode'
      platform.localCode == localCode

    where:
      platformUrl                             || name                 || localCode
      'http://content.apa.org/journals/str'   || 'content.apa.org'    || 'ciando'

  }
}

