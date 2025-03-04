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
import org.olf.kb.IdentifierNamespace
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.TitleInstance
import org.olf.kb.ErmTitleList
import org.olf.kb.Work

import com.k_int.web.toolkit.refdata.RefdataValue

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

  @Requires({ instance.isIdTIRS() })
  void 'Fix equivalent identifiers in IdFirstTIRS' () {
    when: 'We check we have the expected setup'
      String workSourceId1 = 'fei-001'; // Making sure this is the same throughout the test
      String workSourceId2 = 'fei-002'; // Making sure this is the same throughout the test

      String issn1 = 'fei-123-456';
      String issn2 = 'fei-abc-def';

      List<TitleInstance> tis1;
      TitleInstance electronicTi1;
      TitleInstance printTi1;
      String originalTiId1;

      List<TitleInstance> tis2;
      TitleInstance electronicTi2;
      TitleInstance printTi2;
      String originalTiId2;
      withTenant {
        tis1 = getFullTIsForWork(getWorkFromSourceId(workSourceId1).id);
        electronicTi1 = tis1.find(ti -> ti.subType.value == 'electronic');
        printTi1 = tis1.find(ti -> ti.subType.value == 'print');
        originalTiId1 = electronicTi1.id;

        tis2 = getFullTIsForWork(getWorkFromSourceId(workSourceId2).id);
        electronicTi2 = tis2.find(ti -> ti.subType.value == 'electronic');
        printTi2 = tis2.find(ti -> ti.subType.value == 'print');
        originalTiId2 = electronicTi2.id;
      }
    then: 'We have the expected TIs and an originalTiId'
      assert tis1.size() == 2
      assert electronicTi1.name == 'fixEquivalentIds-test1'
      assert electronicTi1.identifiers.size() == 1;
      assert electronicTi1.identifiers.find { io -> io.identifier.ns.value == 'eissn' } == null;
      assert electronicTi1.identifiers.find { io -> io.identifier.ns.value == 'issn' } != null;

      assert printTi1.name == 'fixEquivalentIds-test1'
      assert printTi1.identifiers.size() == 1;
      assert printTi1.identifiers.find { io -> io.identifier.ns.value == 'pissn' } == null;
      assert printTi1.identifiers.find { io -> io.identifier.ns.value == 'issn' } != null;
      assert originalTiId1 != null;

      assert tis2.size() == 1
      assert electronicTi2.name == 'fixEquivalentIds-test2'
      assert electronicTi2.identifiers.size() == 1;
      assert electronicTi2.identifiers.find { io -> io.identifier.ns.value == 'eissn' } == null;
      assert electronicTi2.identifiers.find { io -> io.identifier.ns.value == 'issn' } != null;

      assert printTi2 == null
      assert originalTiId2 != null;
    when: 'We set up an equivalent identifier for fei-123-456 and add said identifier to test1 and test2 (error)'
      withTenant {
        Identifier existingIssn = Identifier.executeQuery("""
          SELECT iden FROM Identifier AS iden
          WHERE iden.ns.value = 'issn' AND
          iden.value = :issn
        """.toString(), [issn: issn1])[0];

        IdentifierNamespace pissnNs = IdentifierNamespace.findOrCreateByValue('pissn').save(failOnError:true)
        IdentifierNamespace eissnNs = IdentifierNamespace.findOrCreateByValue('eissn').save(failOnError:true)

        Identifier newPissn = new Identifier(
          ns: pissnNs,
          value: issn1
        ).save(failOnError: true);

        IdentifierOccurrence pissnIO = new IdentifierOccurrence(
          identifier: newPissn,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved'),
          resource: printTi1
        ).save(failOnError: true)

        Identifier newEissn = new Identifier(
          ns: eissnNs,
          value: issn1
        ).save(failOnError: true);

        IdentifierOccurrence eissnIO1 = new IdentifierOccurrence(
          identifier: newEissn,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved'),
          resource: electronicTi1
        ).save(failOnError: true);

        IdentifierOccurrence eissnIO2 = new IdentifierOccurrence(
          identifier: newEissn,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved'),
          resource: electronicTi2
        ).save(failOnError: true, flush: true); // Flushing here because transactions are normally per test method.
      }
    then: 'All good'
      noExceptionThrown();
    when: 'We subsequently fetch the titleInstances'
      withTenant {
        tis1 = getFullTIsForWork(getWorkFromSourceId(workSourceId1).id);
        electronicTi1 = tis1.find(ti -> ti.subType.value == 'electronic');
        printTi1 = tis1.find(ti -> ti.subType.value == 'print');

        tis2 = getFullTIsForWork(getWorkFromSourceId(workSourceId2).id);
        electronicTi2 = tis2.find(ti -> ti.subType.value == 'electronic');
        printTi2 = tis2.find(ti -> ti.subType.value == 'print');
      }
    then: 'We see the expected broken issn data'
      assert tis1.size() == 2
      assert electronicTi1.name == 'fixEquivalentIds-test1'
      assert electronicTi1.identifiers.size() == 2;
      assert electronicTi1.identifiers.find { io -> io.identifier.ns.value == 'eissn' } != null;
      assert electronicTi1.identifiers.find { io -> io.identifier.ns.value == 'issn' } != null;

      assert printTi1.name == 'fixEquivalentIds-test1'
      assert printTi1.identifiers.size() == 2;
      assert printTi1.identifiers.find { io -> io.identifier.ns.value == 'pissn' } != null;
      assert printTi1.identifiers.find { io -> io.identifier.ns.value == 'issn' } != null;
      assert originalTiId1 != null;

      assert tis2.size() == 1
      assert electronicTi2.name == 'fixEquivalentIds-test2'
      assert electronicTi2.identifiers.size() == 2;
      assert electronicTi2.identifiers.find { io -> io.identifier.ns.value == 'eissn' } != null;
      assert electronicTi2.identifiers.find { io -> io.identifier.ns.value == 'issn' } != null;
    when: 'We look up identifiers'
      List<Identifier> identifiersInSystem
      withTenant {
        identifiersInSystem = Identifier.executeQuery("""
          SELECT iden FROM Identifier AS iden
          WHERE iden.ns.value LIKE '%issn' AND
          iden.value = :issn
        """.toString(), [issn: issn1]);
      }
    then: 'We see "duplicate" identifiers in the system'
      assert identifiersInSystem.size() == 3
      assert identifiersInSystem.findAll { io -> io.ns.value == 'issn' }.size() == 1;
      assert identifiersInSystem.findAll { io -> io.ns.value == 'pissn' }.size() == 1;
      assert identifiersInSystem.findAll { io -> io.ns.value == 'eissn' }.size() == 1;

    when: 'We subsequently attempt to resolve a citation with fei-123-456'
      String resolvedTiId;
      withTenant {
        resolvedTiId = titleInstanceResolverService.resolve(citationFromFile('fix_equivalent_ids.json'), true);
      }
    then: 'We have the expected title(s)'
      noExceptionThrown()
      assert resolvedTiId == electronicTi1.id
    when: 'We look up identifiers'
      withTenant {
        identifiersInSystem = Identifier.executeQuery("""
          SELECT iden FROM Identifier AS iden
          WHERE iden.ns.value LIKE '%issn' AND
          iden.value = :issn
        """.toString(), [issn: issn1]);
      }
    then: 'We do not see duplicate identifiers in the system'
      assert identifiersInSystem.size() == 1
      assert identifiersInSystem.findAll { io -> io.ns.value == 'issn' }.size() == 1;
      assert identifiersInSystem.findAll { io -> io.ns.value == 'pissn' }.size() == 0;
      assert identifiersInSystem.findAll { io -> io.ns.value == 'eissn' }.size() == 0;
  }

  @Requires({ instance.isIdTIRS() })
  void 'Fix equivalent identifiers in IdFirstTIRS -- non issn/isbn' () {
    when: "We fetch electronic TI from previous test" // Bit dodgy, we should probably have a new test case TI here instead
      String workSourceId = 'fei-003'; // Making sure this is the same throughout the test
      List<TitleInstance> tis;
      TitleInstance electronicTi;
      String originalTiId;

      withTenant {
        tis = getFullTIsForWork(getWorkFromSourceId(workSourceId).id);
        electronicTi = tis.find(ti -> ti.subType.value == 'electronic');
        originalTiId = electronicTi.id;
      }
    then: 'We have the title in hand'
      assert tis.size() == 1
      assert electronicTi.name == 'fixEquivalentIds-test3'
      assert electronicTi.identifiers.size() == 1;
    when: "We set up a duplicate identifier out of scope of the issn/isbn fixing"
      String testValue = 'testing-identifier-123'
      String doiString = 'doi'
      withTenant {
        IdentifierNamespace testNs = IdentifierNamespace.findOrCreateByValue(doiString).save(failOnError:true)

        Identifier testIdentifier1 = new Identifier(
          ns: testNs,
          value: testValue
        ).save(failOnError: true);

        IdentifierOccurrence testIO1 = new IdentifierOccurrence(
          identifier: testIdentifier1,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved'),
          resource: electronicTi
        ).save(failOnError: true);

        Identifier testIdentifier2 = new Identifier(
          ns: testNs,
          value: testValue
        ).save(failOnError: true);

        IdentifierOccurrence testIO2 = new IdentifierOccurrence(
          identifier: testIdentifier2,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved'),
          resource: electronicTi
        ).save(failOnError: true, flush: true); // Flushing to force transaction flush for next "then"
      }
    then: 'All good'
      noExceptionThrown();
    when: 'We subsequently fetch the titleInstances'
      withTenant {
        tis = getFullTIsForWork(getWorkFromSourceId(workSourceId).id);
        electronicTi = tis.find(ti -> ti.subType.value == 'electronic');
      }
    then: 'We see the expected broken identifier data'
      assert tis.size() == 1
      assert electronicTi.name == 'fixEquivalentIds-test3'
      assert electronicTi.id == originalTiId;
      assert electronicTi.identifiers.size() == 3;
      assert electronicTi.identifiers.find { io -> io.identifier.ns.value == 'issn' } != null;
      assert electronicTi.identifiers.findAll { io -> io.identifier.ns.value == doiString && io.identifier.value == testValue }.size() == 2;
    when: 'We subsequently attempt to resolve a citation with fei-321-abc (broken identifier not on citation)'
      String resolvedTiId;
      TitleInstance resolvedTi;
      Set<IdentifierOccurrence> resolvedApprovedIdentifiers;

      withTenant {
        resolvedTiId = titleInstanceResolverService.resolve(citationFromFile('fix_equivalent_ids_2.json'), true);
        resolvedTi = TitleInstance.get(resolvedTiId)
        
        resolvedApprovedIdentifiers = resolvedTi.approvedIdentifierOccurrences
      }
    then: 'We have the expected title(s)'
      noExceptionThrown()
      assert resolvedTiId == electronicTi.id
    when: 'We subsequently attempt to resolve a citation with fei-321-abc (broken identifier on citation)'
      Long code
      String message
      withTenant {
        try {
        titleInstanceResolverService.resolve(citationFromFile('fix_equivalent_ids_3.json'), true);
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'We throw the expected exception'
      assert code == TIRSException.MULTIPLE_IDENTIFIER_MATCHES
      assert message == 'Multiple (2) matches found for identifier doi::testing-identifier-123'
  }
}
