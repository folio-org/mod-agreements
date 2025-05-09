package org.olf.DeleteResources

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.kb.ErmResource
import spock.lang.Shared
import spock.lang.Stepwise
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
    log.info("Test case in findInputResourceIds: {}", testCase.toString())
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

  String packageNameSimple1 = "K-Int Deletion Test Package 001"
  String packageNameTopLink1 = "K-Int Link - Deletion Test Package 001";
  String packageNameTopLink2 = "K-Int Link - Deletion Test Package 002"
  String packageNameTiLink1 = "K-Int TI Link - Deletion Test Package 001";
  String packageNameTiLink2 = "K-Int TI Link - Deletion Test Package 002"
  String agreementName = "test_agreement"

  @Unroll // Create a separate test report entry for each scenario
  void "Scenario: #currentScenario.description"() {
    setup:
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



    when: "The mark for delete action is performed (simulated)"
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentScenario)
    visualiseHierarchy(idsForProcessing.get("pci"))
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentScenario)

    String agreement_name = agreementName
    Map agreementResp = createAgreement(agreement_name)
    idsForAgreementLines.keySet().forEach{String resourceKey -> {
      if (!idsForAgreementLines.get(resourceKey).isEmpty()) {
        idsForAgreementLines.get(resourceKey).forEach{String id -> {
          log.info("agreemtn line resource id: {}", id)
          addEntitlementForAgreement(agreement_name, id)
        }}
      }
    }}

    log.info("IDs for processing: ${idsForProcessing}")
    log.info("IDs for agreement lines: ${idsForAgreementLines}")
    log.info("Simulating mark for delete action...")
    // TODO: Implement "doDelete" in where block.
//    String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
    Map operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    log.info("Operation Response: ${operationResponse}")
    log.info("KB Stats: ${kbStatsResp}")

    then: "The expected resources are marked and KB stats are correct"
    Map<String, Set<String>> expectedMarkedResourceIds = findExpectedResourceIds(currentScenario)

    log.info("Expected marked resource IDs: ${expectedMarkedResourceIds}")
    log.info("Expected KB stats after mark: ${currentScenario.expectedKbMarkForDelete}")

    assert kbStatsResp.get("PackageContentItem") == currentScenario.expectedKbMarkForDelete.get("pci")
    assert kbStatsResp.get("PlatformTitleInstance") == currentScenario.expectedKbMarkForDelete.get("pti")
    assert kbStatsResp.get("TitleInstance") == currentScenario.expectedKbMarkForDelete.get("ti")
    assert kbStatsResp.get("Work") == currentScenario.expectedKbMarkForDelete.get("work")
    assert operationResponse.get("pci") as Set == expectedMarkedResourceIds.get("pci") as Set
    assert operationResponse.get("pti") as Set == expectedMarkedResourceIds.get("pti") as Set
    assert operationResponse.get("ti") as Set == expectedMarkedResourceIds.get("ti") as Set
    assert operationResponse.get("work") as Set == expectedMarkedResourceIds.get("work") as Set

    when: "The delete action is performed (simulated)"
    log.info("Simulating delete action...")

    then: "The KB stats reflect the deletion"
    log.info("Expected KB stats after delete: ${currentScenario.expectedKbDelete}")

    // TODO: Add actual assertions for deletion


    cleanup:
    clearResources()

    where:
    currentScenario << allTestScenarios // Spock iterates here, injecting currentScenario

  }
}
