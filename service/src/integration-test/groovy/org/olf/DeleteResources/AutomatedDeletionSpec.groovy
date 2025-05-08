package org.olf.DeleteResources

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.spockframework.runtime.SpecificationContext
import spock.lang.Shared
import spock.lang.Stepwise
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import groovy.json.JsonOutput
import spock.lang.Unroll

@Integration
@Stepwise
@Rollback
@Slf4j
class AutomatedDeletionSpec extends DeletionBaseSpec {

  @Shared
  static List<Scenario> allTestScenarios = ScenarioCsvReader.loadScenarios("packages/hierarchicalDeletion/deletion_test_matrix.csv")

  Scenario currentScenario

  def seedDatabaseWithStructure(String structure) {
    if (structure == "Simple") {
      importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
      doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    }

    if (structure == "Top-Link") {
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion.json')
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion_link.json')

      List resp = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 001']])
      List resp2 = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 002']])
    }

    if (structure == "Ti-Link") {
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_1.json')
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_2.json')

      List resp = doGet("/erm/packages", [filters: ['name==K-Int TI Link - Deletion Test Package 001']])
      List resp2 = doGet("/erm/packages", [filters: ['name==K-Int TI Link - Deletion Test Package 002']])
    }
  }

  ErmResource parseResource(String resource, String structure) {
    if (structure == "Simple") {
      if (resource == "PCI1") {
        return findPCIByPackageName(packageNameSimple1)
      }

      if (resource == "PTI1") {
        return findPCIByPackageName(packageNameSimple1).pti
      }
    }

    if (structure == "Top-Link") {
      if (resource == "PCI1") {
        return findPCIByPackageName(packageNameTopLink1)
      }

      if (resource == "PCI2") {
        return findPCIByPackageName(packageNameTopLink2)
      }

      if (resource == "PTI1" || resource == "PTI2") {
        return findPCIByPackageName(packageNameTopLink1).pti
      }
    }

    if (structure == "Ti-Link") {
      if (resource == "PCI1") {
        return findPCIByPackageName(packageNameTiLink1)
      }

      if (resource == "PCI2") {
        return findPCIByPackageName(packageNameTiLink2)
      }

      if (resource == "PTI1") {
        return findPCIByPackageName(packageNameTiLink1).pti
      }

      if (resource == "PTI2") {
        return findPCIByPackageName(packageNameTiLink2).pti
      }

    }
    return null;
  }

  String parseResourceType(String resource) {
    if (resource.startsWith("PCI")) {
      return "pci"
    }

    if (resource.startsWith("PTI")) {
      return "pti"
    }
  }

  def findInputResourceIds(Scenario testCase) {
    Map<String, Set<String>> allResources = new HashMap<String, Set<String>>();
    allResources.put("pci", new HashSet<String>());
    allResources.put("pti", new HashSet<String>());
    allResources.put("ti", new HashSet<String>());
    allResources.put("work", new HashSet<String>());

    testCase.inputResources.forEach{resource -> {
      log.info(resource.toString())
      String resourceType = parseResourceType(resource)
      log.info(resourceType.toString())
      allResources.get(resourceType).add(parseResource(resource, testCase.structure).id)
    }}

    return allResources;
  }

  def findAgreementLineResourceIds(Scenario testCase) {
    Map<String, Set<String>> allResources = new HashMap<String, Set<String>>();
    allResources.put("pci", new HashSet<String>());
    allResources.put("pti", new HashSet<String>());
    allResources.put("ti", new HashSet<String>());
    allResources.put("work", new HashSet<String>());

    if (testCase.agreementLines.get(0) == "None") {
      return allResources;
    }
    if (testCase.agreementLines.get(0) == "All") {
      allResources.put("pci", collectIDs(getPCIs()))
      allResources.put("pti", collectIDs(getPTIs()))
      allResources.put("ti", collectIDs(getTIs()))
      allResources.put("work", getWorkIds())
      return allResources;
    }

    testCase.agreementLines.forEach{resource -> {
      String resourceType = parseResourceType(resource)
      allResources.get(resourceType).add(parseResource(resource, testCase.structure).id)
    }}

    return allResources;
  }

  def findExpectedResourceIds(Scenario testCase) {
    Map<String, Set<String>> allResources = new HashMap<String, Set<String>>();
    allResources.put("pci", new HashSet<String>());
    allResources.put("pti", new HashSet<String>());
    allResources.put("ti", new HashSet<String>());
    allResources.put("work", new HashSet<String>());

    if (testCase.markExpectedIds.get(0) == "None") {
      return allResources;
    }
    if (testCase.markExpectedIds.get(0) == "All") {
      allResources.put("pci", collectIDs(getPCIs()))
      allResources.put("pti", collectIDs(getPTIs()))
      allResources.put("ti", collectIDs(getTIs()))
      allResources.put("work", getWorkIds())
      return allResources;
    }
    testCase.markExpectedIds.forEach{resource -> {
      String resourceType = parseResourceType(resource)
      allResources.get(resourceType).add(parseResource(resource, testCase.structure).id)
    }}

    return allResources;
  }

  def runTestCase(Scenario testCase) {
    seedDatabaseWithStructure(testCase.toString())
    Map<String, Set<String>> idsMarkedForProcessing = findInputResourceIds(testCase)
    Map<String, Set<String>> idsExpectedFromMarkForDelete = findExpectedResourceIds(testCase)
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(testCase)

    log.info(testCase.description.toString())
    log.info("Ids marked for processing: {}", idsMarkedForProcessing.toMapString())
    log.info("Ids expected for markForDelete: {}", idsExpectedFromMarkForDelete.toMapString())
    log.info("Ids with agreement lines: {}", idsForAgreementLines.toMapString())
  }

  @Shared
  String pkg_id

  List<String> pciIds;
  List<String> ptiIds;
  List<String> tiIds;
  String packageNameSimple1 = "K-Int Deletion Test Package 001"
  String packageNameTopLink1 = "K-Int Link - Deletion Test Package 001";
  String packageNameTopLink2 = "K-Int Link - Deletion Test Package 002"
  String packageNameTiLink1 = "K-Int TI Link - Deletion Test Package 001";
  String packageNameTiLink2 = "K-Int TI Link - Deletion Test Package 002"
  String agreementName = "test_agreement"

//  setupSpec() {
//    testCaseData = readTestCases()
//  }

//  setup() {
//    SpecificationContext currentSpecInfo = specificationContext;
//    if (!specificationContext.currentFeature?.name.contains("Scenario")) {
//      // If not in a SimpleDeletionSpec Scenario (i.e. in a tenant purge/ensure test tenant), don't try to load packages yet.
//      log.info("--- Skipping Setup for tenant setup tests: ${currentSpecInfo.currentSpec.displayName} (Feature: ${currentSpecInfo.currentFeature?.name}) ---")
//      return;
//    }
//  }

  static {
    if (allTestScenarios == null) {
      System.err.println("AutomatedDeletionSpec: allTestScenarios is NULL after loading!")
    } else {
      System.out.println("AutomatedDeletionSpec: Loaded ${allTestScenarios.size()} scenarios into allTestScenarios.")
      if (allTestScenarios.isEmpty()) {
        System.err.println("AutomatedDeletionSpec: allTestScenarios IS EMPTY after loading!")
      } else {
        System.out.println("AutomatedDeletionSpec: First scenario description: ${allTestScenarios.first().description}")
      }
    }
  }

  def setup() {
    log.info("Current Scenario in setup: ${currentScenario}")
    if (currentScenario) {
      log.info("Current Scenario description in setup: ${currentScenario.description}")
    } else {
      log.warn("currentScenario IS NULL in setup()")
    }
    if (!currentScenario) {
      return
    }
    log.info("--- setup: Preparing for scenario: ${currentScenario.description} ---")
    log.info("--- Seeding DB for structure: ${currentScenario.structure} ---")
    seedDatabaseWithStructure(currentScenario.structure)
  }

//  void "readFile"() {
//    given:
//
//    when:
//    readTestCases();
//
//    then:
//    true
//  }

  @Unroll // This will create a separate test report entry for each scenario
  void "Scenario: #currentScenario.description"() {
    given: "The database is seeded for the current scenario's structure"
    // This is now handled by the `setup()` method automatically by Spock
    log.info("Executing test for scenario: ${currentScenario.description}")
    log.info("Input Resources from CSV: ${currentScenario.inputResources}")
    log.info("Agreement Lines from CSV: ${currentScenario.agreementLines}")
    log.info("Expected Mark IDs from CSV: ${currentScenario.markExpectedIds}")

    and: "Input parameters are determined"
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentScenario)
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentScenario)

    log.info("IDs for processing: ${idsForProcessing}")
    log.info("IDs for agreement lines: ${idsForAgreementLines}")

    when: "The mark for delete action is performed (simulated)"
    log.info("Simulating mark for delete action...")

    then: "The expected resources are marked and KB stats are correct"
    Map<String, Set<String>> expectedMarkedResourceIds = findExpectedResourceIds(currentScenario)
    List<Integer> expectedKbStatsAfterMark = currentScenario.expectedKbMarkForDelete.collect { it.toInteger() }

    log.info("Expected marked resource IDs: ${expectedMarkedResourceIds}")
    log.info("Expected KB stats after mark: ${expectedKbStatsAfterMark}")

    // TODO: Add actual assertions against the database state or service results
    // Example:
    // def actualMarkedResources = getCurrentlyMarkedResourcesFromDB() // Implement this
    // assertMapsOfSetsEqual(actualMarkedResources, expectedMarkedResourceIds)
    //
    // def actualKbStats = getActualKbStatsFromDB() // Implement this
    // actualKbStats.pci == expectedKbStatsAfterMark[0]
    // actualKbStats.pti == expectedKbStatsAfterMark[1]
    // actualKbStats.ti == expectedKbStatsAfterMark[2]
    // actualKbStats.work == expectedKbStatsAfterMark[3]

    // Placeholder assertion
    1 == 1 // Replace with real assertions

    // If testing the actual deletion as well:
    when: "The delete action is performed (simulated)"
    log.info("Simulating delete action...")
    // yourService.deleteMarkedItems() // Actual call

    then: "The KB stats reflect the deletion"
    List<Integer> expectedKbStatsAfterDelete = currentScenario.expectedKbDelete.collect { it.toInteger() }
    log.info("Expected KB stats after delete: ${expectedKbStatsAfterDelete}")

    // TODO: Add actual assertions for deletion
    // def actualKbStatsAfterDelete = getActualKbStatsFromDB()
    // actualKbStatsAfterDelete.pci == expectedKbStatsAfterDelete[0]
    // ...

    // Placeholder assertion
    1 == 1 // Replace with real assertions

    where:
    currentScenario << allTestScenarios // Spock iterates here, injecting currentScenario
  }
}
