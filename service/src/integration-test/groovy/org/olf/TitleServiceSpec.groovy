package org.olf

import org.olf.dataimport.internal.InternalPackageImpl.PackageContentImpl
import org.olf.kb.TitleInstance

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import grails.web.databinding.DataBindingUtils
import spock.lang.*

@Integration
@Stepwise
class TitleServiceSpec extends BaseSpec {

  def titleInstanceResolverService
  
  @Shared PackageContentImpl content
  
  void 'Bind to content' () {
    when: 'Attempt the bind'
      content = new PackageContentImpl()
      DataBindingUtils.bindObjectToInstance(content, [
        'title':'Brain of the firm',
        'instanceMedium': 'print',
        'instanceMedia': 'BKM',
        'instanceIdentifiers': [ 
          [
            'namespace': 'isbn',
            'value': '0713902191'
          ],
          [
            'namespace': 'isbn',
            'value': '9780713902198'
          ] 
        ],
        'siblingInstanceIdentifiers': [ 
          [
            // 2e - print
            'namespace': 'isbn',
            'value': '047194839X'
          ] ]
  
      ])
      
    
    then: 'Everything is good'
      noExceptionThrown()
  }

  void 'Test Title Resolution'() {

    when: 'Resolve title'

      def title_instance = null
      def num_identifiers = 0
      final String tenantid = currentTenant.toLowerCase()

      // We are exercising the service directly, normally a transactional context will
      // be supplied by the HTTPRequest, but we fake it here to talk directly to the service
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
        // N.B. This is a groovy MAP, not a JSON document.
        title_instance = titleInstanceResolverService.resolve(content)
        num_identifiers = title_instance.identifiers.size()
      }

    then: 'New title created and identifier count matches'
      title_instance.name == 'Brain of the firm'
      title_instance.id != null
      // It would be nice to do this. but DON'T. Our session is terminated in the withId block above, so doing
      // this will cause the test to blow up as the session has gone away. Use the approach take, where we count
      // inside the block and check the count below.
      // title_instance.identifiers.size() == 2
      num_identifiers == 2
  }

  void "Second time around don't create a new title" () {

    when: 'Resolve the same title again'
      def title_instance = null
      def num_identifiers = 0
      def num_titles = 0
      final String tenantid = currentTenant.toLowerCase()

      // We are exercising the service directly, normally a transactional context will
      // be supplied by the HTTPRequest, but we fake it here to talk directly to the service
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
        // N.B. This is a groovy MAP, not a JSON document.
        title_instance = titleInstanceResolverService.resolve(content)
        num_identifiers = title_instance.identifiers.size()
        num_titles = TitleInstance.findAllByName('Brain of the firm').size()
      }

    then: 'Same title is returned and not duplicated'
      title_instance.name == 'Brain of the firm'
      title_instance.id != null
      // It would be nice to do this. but DON'T. Our session is terminated in the withId block above, so doing
      // this will cause the test to blow up as the session has gone away. Use the approach take, where we count
      // inside the block and check the count below.
      // title_instance.identifiers.size() == 2
      num_identifiers == 2
      num_titles == 1
  }

}

