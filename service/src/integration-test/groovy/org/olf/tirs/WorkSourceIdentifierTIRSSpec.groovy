package org.olf.tirs

import org.olf.dataimport.internal.TitleInstanceResolverService
import org.olf.dataimport.internal.titleInstanceResolvers.WorkSourceIdentifierTIRSImpl
//import org.olf.dataimport.internal.titleInstanceResolvers.IdFirstTIRSImpl

import org.olf.dataimport.internal.titleInstanceResolvers.TIRSException

import org.springframework.context.annotation.Bean
import org.springframework.core.io.Resource

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.kb.RemoteKB
import org.olf.kb.Identifier
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.TitleInstance
import org.olf.kb.Work

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.transactions.Transactional
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import groovy.transform.CompileStatic

import spock.lang.*

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class WorkSourceIdentifierTIRSSpec extends TIRSSpec {
  @Shared PackageContentImpl brainOfTheFirm

  @Shared
  String pkg_id

  @Shared
  String resource_path = "src/integration-test/resources/packages/workSourceTIRS"

  @Shared
  String citation_path = "${resource_path}/citations"

  // Todo I can't work out how to inject WorkSourceTIRS directly...
  // not an issue for WorkSource tests because that's the default but it would be for other tests

  // Helper to avoid having to fill out package location every time
  @Ignore
  Map importPackageTest(String package_name) {
    return importPackageFromFileViaService(package_name, resource_path)
  }

  @Ignore
  PackageContentImpl citationFromFile(String citation_file_name) {
    return bindMapToCitationFromFile(citation_file_name, citation_path)
  }

  void 'Bind to content' () {
    when: 'Attempt the bind'
      brainOfTheFirm = citationFromFile('brain_of_the_firm.json')    
    then: 'Everything is good'
      noExceptionThrown()
  }

  // Test directly (but only if titleInstanceResolverService is as expected)
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test title creation' () {
    when: 'WorkSourceIdentifierTIRS is passed a title citation'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        String tiId = titleInstanceResolverService.resolve(brainOfTheFirm, true);
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
      brainOfTheFirm.sourceIdentifierNamespace = null;

      Long code
      String message
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          String tiId = titleInstanceResolverService.resolve(brainOfTheFirm, true);
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'We got an expected error'
      assert code == TIRSException.MISSING_MANDATORY_FIELD
      assert message == 'Missing source identifier namespace'
    when: 'WorkSourceIdentifierTIRS is passed a title citation without sourceIdentifier'
      brainOfTheFirm.sourceIdentifier = null;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          String tiId = titleInstanceResolverService.resolve(brainOfTheFirm, true);
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    
    then: 'We got an expected error'
      assert code == TIRSException.MISSING_MANDATORY_FIELD
      assert message == 'Missing source identifier'
    cleanup:
      brainOfTheFirm.sourceIdentifierNamespace = 'k-int';
      brainOfTheFirm.sourceIdentifier = 'botf-123';
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Ingest via package service works as expected' () {
    when: 'We ingest wsitirs_pkg'
      Map result = importPackageTest('wsitirs_pkg.json')

    then: 'Package imported'
      result.packageImported == true
    
    when: "Looked up package with name"
      List resp = doGet("/erm/packages", [filters: ['name==Work Source TIRS Package']])
      pkg_id = resp[0].id

    then: "Package found"
      resp.size() == 1
      resp[0].id != null
    when: "Looking up the number of TIs in the system"
      // Ignore the tis from the first test
      def tiGet = doGet("/erm/titles", [filters: ['name!=Brain of the firm'], stats: true]);
    then: "We have the expected number"
      assert tiGet.total == 18
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when matching multiple works' () {
    when: 'We create a work that duplicates one already in the system'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        // Grab existing identifier
        Identifier identifier = Identifier.executeQuery("""
          SELECT iden from Identifier as iden
            WHERE iden.value = :value and iden.ns.value = :ns
          """.toString(),
          [value:'aaa-001', ns:'k-int']
        )[0]

        IdentifierOccurrence sourceIdentifier = new IdentifierOccurrence([
          identifier: identifier,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ])

        Work duplicateWork = new Work([
          title: 'Duplicate work',
          sourceIdentifier: sourceIdentifier
        ]).save(failOnError: true, flush: true)
      }
    then: 'Everything saved as expected'
      noExceptionThrown()
    when: 'Looking up works for this sourceIdentifier' // There is no endpoint
    Integer workCount;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        workCount = Work.executeQuery("""
          SELECT COUNT(work.id) from Work as work
            WHERE work.sourceIdentifier.identifier.value = :value
          """.toString(),
          [value:'aaa-001']
        )[0]
      }
    then: 'We see two works'
      assert workCount == 2
    when: 'WorkSourceIdentifierTIRS attempts to match on this duplciated work'
      Long code
      String message
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          titleInstanceResolverService.resolve(citationFromFile('multiple_work_match.json'), true)
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'We get the expected error'
      assert message == 'Matched 2 with source identifier K-Int:aaa-001'
      assert code == TIRSException.MULTIPLE_WORK_MATCHES
  }
}
