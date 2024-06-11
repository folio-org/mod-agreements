package org.olf.tirs

import org.olf.dataimport.internal.TitleInstanceResolverService
import org.olf.dataimport.internal.titleInstanceResolvers.WorkSourceIdentifierTIRSImpl
import org.olf.dataimport.internal.titleInstanceResolvers.IdFirstTIRSImpl

import org.olf.dataimport.internal.titleInstanceResolvers.TIRSException

import org.springframework.context.annotation.Bean
import org.springframework.core.io.Resource

import org.olf.kb.RemoteKB
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.kb.TitleInstance

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.transactions.Transactional
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import grails.web.databinding.DataBindingUtils
import groovy.transform.CompileStatic

import spock.lang.*

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class WorkSourceIdentifierTIRSSpec extends TIRSSpec {
  @Shared PackageContentImpl content

  // Todo I can't work out how to inject WorkSourceTIRS directly...
  // not an issue for WorkSource tests because that's the default but it would be for other tests

  void 'Bind to content' () {
    when: 'Attempt the bind'
      content = new PackageContentImpl()
      DataBindingUtils.bindObjectToInstance(content, [
        'title':'Brain of the firm',
        'instanceMedium': 'electronic',
        'instanceMedia': 'monograph',
        'instanceIdentifiers': [ 
          [
            'namespace': 'eisbn',
            'value': '0713902191'
          ],
          [
            'namespace': 'eisbn',
            'value': '9780713902198'
          ] 
        ],
        'siblingInstanceIdentifiers': [ 
          [
            // 2e - print
            'namespace': 'isbn',
            'value': '047194839X'
          ]
        ],
        'sourceIdentifierNamespace': 'k-int',
        'sourceIdentifier': 'botf-123'
      ])
    
    then: 'Everything is good'
      noExceptionThrown()
  }

  // Test directly (but only if titleInstanceResolverService is as expected)
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test title creation' () {
    when: 'WorkSourceIdentifierTIRS is passed a title citation'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        String tiId = titleInstanceResolverService.resolve(content, true);
      }
      def tiGet = doGet("/erm/titles", [filters: ['name==Brain of the firm'], stats: true]);
    then: 'We get the expected TIs'
      assert tiGet.total == 2 // One print, one electronic
    when: 'We inspect electronic and print tis'
      def electronicTI = tiGet.results?.find {ti -> ti.subType.value == 'electronic'}
      def printTI = tiGet.results?.find {ti -> ti.subType.value == 'print'}
    then: 'We have expected results'
      assert electronicTI != null;
      assert printTI != null;

      assert electronicTI.identifiers.size() == 2;
      assert printTI.identifiers.size() == 1;
  }


  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test rejection without sourceIdentifier fields' () {
    when: 'WorkSourceIdentifierTIRS is passed a title citation without sourceIdentifierNamespace'
      content.sourceIdentifierNamespace = null;

      Long code
      String message
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          String tiId = titleInstanceResolverService.resolve(content, true);
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'We got an expected error'
      assert code == TIRSException.MISSING_MANDATORY_FIELD
      assert message == 'Missing source identifier namespace'
    when: 'WorkSourceIdentifierTIRS is passed a title citation without sourceIdentifier'
      content.sourceIdentifier = null;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          String tiId = titleInstanceResolverService.resolve(content, true);
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    
    then: 'We got an expected error'
      assert code == TIRSException.MISSING_MANDATORY_FIELD
      assert message == 'Missing source identifier'
    cleanup:
      content.sourceIdentifierNamespace = 'k-int';
      content.sourceIdentifier = 'botf-123';
  }
}
