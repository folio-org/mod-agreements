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
import com.k_int.web.toolkit.utils.GormUtils

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
    'test': ['a', 'b', 'c', 'd'],
    'test-2': ['a', 'b', 'c', 'c2'],
    'test-3': ['wibble']
  ]

  @Shared
  Set<String> expectedIdentifiersInSystem = new HashSet();
  
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
      List<Identifier> identifiers;

      withTenant {
        identifiers = Identifier.executeQuery("""
          SELECT iden FROM Identifier AS iden
        """)
      }

      Set<String> identifiersInSystem = identifiers.collect { "${it.ns.value}:${it.value}" } as Set
    then: 'We see the expected identifiers in the system';
      assert identifiersInSystem.equals(expectedIdentifiersInSystem);
  }

  /* def 'Fix equivalent ids where prime identifier doesn\'t exist' () {
    when: 'We set up a bunch of IdentifierOccurrences for Identifiers in the system';
      withTenant {
        ErmTitleList mockTitleList1 = new ErmTitleList().save(failOnError: true);
        ErmTitleList mockTitleList2 = new ErmTitleList().save(failOnError: true);
      }
  } */
}
