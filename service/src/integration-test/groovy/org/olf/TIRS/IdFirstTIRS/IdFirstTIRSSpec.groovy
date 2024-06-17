package org.olf.TIRS.IdFirstTIRS

import org.olf.TIRS.TIRSSpec

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
import org.olf.kb.ErmTitleList
import org.olf.kb.Work

import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.transactions.Transactional
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import groovy.transform.CompileStatic

import spock.lang.*

import groovy.json.JsonOutput

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
@Transactional
class IdFirstTIRSSpec extends TIRSSpec {
  @Shared
  PackageContentImpl brainOfTheFirm

  @Shared
  String resource_path = "${base_resource_path}/idFirstTIRS"

  @Shared
  String citation_path = "${resource_path}/citations"

  @Shared
  String wsitirs_citation_path = "${base_resource_path}/workSourceTIRS/citations"

  @Ignore
  PackageContentImpl citationFromFile(String citation_file_name) {
    return bindMapToCitationFromFile(citation_file_name, citation_path)
  }

  // Helper to avoid having to fill out package location every time
  @Ignore
  Map importPackageTest(String package_name) {
    return importPackageFromFileViaService(package_name, resource_path)
  }

  void 'Bind to content' () {
    when: 'Attempt the bind'
      brainOfTheFirm = bindMapToCitationFromFile('brain_of_the_firm.json', wsitirs_citation_path)    
    then: 'Everything is good'
      noExceptionThrown()
  }

  // Test directly (but only if titleInstanceResolverService is as expected)
  @Requires({ instance.isIdTIRS() })
  void 'Test title creation' () {
    when: 'IdFirstTIRS is passed a title citation'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        titleInstanceResolverService.resolve(brainOfTheFirm, true);
      }
    then: 'All good'
      noExceptionThrown()
  }

  // Transaction needs to be different for subsequent lookup via API...
  // this won't be an issue in production systems -- but avoid hereafter
  // in this suite by doing DB lookups -- test title API elsewhere
  @Requires({ instance.isIdTIRS() })
  void 'Test title creation -- lookup' () {
    when: 'We lookup titles'
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

  @Requires({ instance.isIdTIRS() })
  void 'Ingest via package service works as expected' () {
    when: 'We ingest ifitirs_pkg'
      Map result = importPackageTest('ifitirs_pkg.json')

    then: 'Package imported'
      result.packageImported == true
  }

  // Transaction needs to be different for subsequent lookup via API...
  // this won't be an issue in production systems
  @Requires({ instance.isIdTIRS() })
  void 'Ingest via package service works as expected -- lookup' () {
    when: "Looked up package with name"
      List resp = doGet("/erm/packages", [filters: ['name==Id First TIRS Package']])

    then: "Package found"
      resp.size() == 1
      resp[0].id != null
    when: "Looking up the number of TIs in the system"
      // Ignore the tis from the first test
      def tiGet = doGet("/erm/titles", [filters: ['name!=Brain of the firm'], stats: true]);
    then: "We have the expected number"
      assert tiGet.total == 6
  }
}
