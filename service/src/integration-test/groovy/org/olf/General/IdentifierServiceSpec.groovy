package org.olf.General

// Services
import org.olf.IdentifierService

// Domain classes
import org.olf.kb.Identifier
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.IdentifierNamespace
import org.olf.kb.ErmTitleList

// Others
import org.olf.kb.IdentifierException

// Test classes
import org.olf.BaseSpec

// Testing classes
import spock.lang.*
import grails.testing.mixin.integration.Integration

// Utilities
import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import org.springframework.transaction.annotation.Propagation
import groovy.util.logging.Slf4j

@Integration
@Stepwise
@Slf4j
class IdentifierServiceSpec extends BaseSpec {
  def identifierService;
  
  // Map from namespace -> list of identifiers we're going to set up
  @Shared
  Map<String, List<String>> bootstrappedIdentifiers = [
    'test': ['a'],
    'test-1': ['a'],
    'test-1x': ['a'],
    'test-2': ['a', 'b', 'c', 'c2'],
    'test-3': ['wibble']
  ]

  @Shared
  Set<String> expectedIdentifiersInSystem = new HashSet();

  @Shared
  Set<String> identifiersInSystem = new HashSet();

  @Shared
  Set<Identifier> identifierObjectsInSystem = new HashSet();

  @Ignore
  void lookupIdentifiersInSystem() {
    List<Identifier> identifiers = Identifier.executeQuery("""
        SELECT iden FROM Identifier AS iden
      """)

    identifiersInSystem = identifiers.collect { "${it.ns.value}:${it.value}" } as Set;
    identifierObjectsInSystem = identifiers as Set;
  }
  
  def 'Creating identifiers works as expected' () {
    when: 'We set up a bunch of identifiers';
      // I don't love doing this manually but it works...
      withTenantNewTransaction {
        bootstrappedIdentifiers.keySet().each { idns ->
          bootstrappedIdentifiers[idns].each { val ->
            identifierService.lookupOrCreateIdentifier(val, idns)
          }
        }
      }
          
      bootstrappedIdentifiers.keySet().each { idns ->
        expectedIdentifiersInSystem.addAll(bootstrappedIdentifiers[idns].collect{ "${idns}:${it}" })
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We subsequently look up identifiers'
      withTenant {
        lookupIdentifiersInSystem();
      }
    then: 'We see the expected identifiers in the system';
      assert identifiersInSystem.equals(expectedIdentifiersInSystem);
  }

  def 'Fix equivalent ids' () {
    when: 'We set up a bunch of IdentifierOccurrence for Identifiers in the system';
      withTenantNewTransaction {
        ErmTitleList mockTitleList1 = new ErmTitleList().save(failOnError: true);
        ErmTitleList mockTitleList2 = new ErmTitleList().save(failOnError: true);
        ErmTitleList mockTitleList3 = new ErmTitleList().save(failOnError: true);

        IdentifierOccurrence io1 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test' && id.value == 'a' },
          resource: mockTitleList1,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);

        IdentifierOccurrence io2 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-1' && id.value == 'a' },
          resource: mockTitleList2,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true)

        IdentifierOccurrence io3 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-1x' && id.value == 'a' },
          resource: mockTitleList3,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true)
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We lookup domain objects'
      List<IdentifierOccurrence> ios;
      List<ErmTitleList> etls;
      withTenant {
        ios = IdentifierOccurrence.executeQuery("""
          SELECT io FROM IdentifierOccurrence AS io
        """)

        etls = ErmTitleList.executeQuery("""
          SELECT etl FROM ErmTitleList AS etl
        """)
      }
    then: 'We have what we expect'
      assert etls.size() == 3;
      assert ios.size() == 3;
      assert ios.findAll { io -> io.identifier.ns.value == 'test' && io.identifier.value == 'a' }.size() == 1;
      assert ios.findAll { io -> io.identifier.ns.value == 'test-1' && io.identifier.value == 'a' }.size() == 1;
      assert ios.findAll { io -> io.identifier.ns.value == 'test-1x' && io.identifier.value == 'a' }.size() == 1;
    when: 'We call fixEquivalentIds'
      String primeIdId;
      withTenantNewTransaction {
        primeIdId = identifierService.fixEquivalentIds(
          identifierObjectsInSystem.findAll { id ->
            id.ns.value == 'test' ||
            id.ns.value == 'test-1' ||
            id.ns.value == 'test-1x'
          }.collect { it.id }, // Send in the ids for the method to work with
          'test'
        )
      }
    then: 'All good'
      noExceptionThrown();
    when: 'We subsequently look up identifiers and identifierOccurrences'
      Identifier primeId;
      withTenant {
        ios = IdentifierOccurrence.executeQuery("""
          SELECT io FROM IdentifierOccurrence AS io
        """)
        primeId = identifierObjectsInSystem.find { id -> id.ns.value == 'test' && id.value == 'a' }

        lookupIdentifiersInSystem();
      }
    then: 'We see that certain identifiers have merged together'
      assert identifiersInSystem.find { id -> id == "test:a"} != null;
      assert identifiersInSystem.find { id -> id == "test-1:a"} == null;
      assert identifiersInSystem.find { id -> id == "test-1x:a"} == null;

      assert primeId != null;
      assert primeId.id == primeIdId;

      assert ios.size() == 3;
      assert ios.every { io -> io.identifier.id == primeId.id };
      // TODO test prime occurrence wrangling
   /*  cleanup:
      withTenantNewTransaction {
        // Remove all IdentifierOccurrences and ErmTitleLists
        ErmTitleList.executeUpdate("""
          DELETE FROM ErmTitleList AS etl
        """.toString())
      } */
  }
}
