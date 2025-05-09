package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.spockframework.runtime.SpecificationContext
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
@Slf4j
class CombinationDeletionSpec extends DeletionBaseSpec {

  String packageNameSimple1 = "K-Int Deletion Test Package 001"
  String packageNameTopLink1 = "K-Int Link - Deletion Test Package 001";
  String packageNameTopLink2 = "K-Int Link - Deletion Test Package 002"
  String packageNameTiLink1 = "K-Int TI Link - Deletion Test Package 001";
  String packageNameTiLink2 = "K-Int TI Link - Deletion Test Package 002"
  String agreementName = "test_agreement"


  @Shared
  List<List<String>> simpleCombinations;

  @Shared
  List<List<String>> topLinkCombinations;

  @Shared
  List<List<String>> tiLinkCombinations;

  def seedDatabaseWithStructure(String structure) {
    if (structure == "simple") {
      importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
      doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    }

    if (structure == "top-link") {
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion.json')
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion_link.json')

      List resp = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 001']])
      List resp2 = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 002']])
    }

    if (structure == "ti-link") {
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_1.json')
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_2.json')

      List resp = doGet("/erm/packages", [filters: ['name==K-Int TI Link - Deletion Test Package 001']])
      List resp2 = doGet("/erm/packages", [filters: ['name==K-Int TI Link - Deletion Test Package 002']])
    }
  }

  String parseResourceType(String resource) {
    if (resource.startsWith("PCI")) {
      return "pci"
    }

    if (resource.startsWith("PTI")) {
      return "pti"
    }
  }

  def findInputResourceIds(List<String> inputResources, String structure) {
    Map<String, Set<String>> allResources = new HashMap<String, Set<String>>();
    allResources.put("pci", new HashSet<String>());
    allResources.put("pti", new HashSet<String>());
    allResources.put("ti", new HashSet<String>());
    allResources.put("work", new HashSet<String>());

    inputResources.forEach{resource -> {
      log.info(resource.toString())
      String resourceType = parseResourceType(resource)
      log.info(resourceType.toString())
      allResources.get(resourceType).add(parseResource(resource, structure).id)
    }}

    return allResources;
  }

  def findAgreementLineResourceIds(List<String> agreementLines, String structure) {
    Map<String, Set<String>> allResources = new HashMap<String, Set<String>>();
    allResources.put("pci", new HashSet<String>());
    allResources.put("pti", new HashSet<String>());
    allResources.put("ti", new HashSet<String>());
    allResources.put("work", new HashSet<String>());

    if (agreementLines.isEmpty()) {
      return allResources;
    }

    agreementLines.forEach{resource -> {
      String resourceType = parseResourceType(resource)
      allResources.get(resourceType).add(parseResource(resource, structure).id)
    }}

    return allResources;
  }


  ErmResource parseResource(String resource, String structure) {
    if (structure == "simple") {
      if (resource == "PCI1") {
        return findPCIByPackageName(packageNameSimple1)
      }

      if (resource == "PTI1") {
        return findPCIByPackageName(packageNameSimple1).pti
      }
    }

    if (structure == "top-link") {
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

    if (structure == "ti-link") {
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


  def cleanup() {
    // Used to clear resources from DB between tests.
    // Specification logic is needed to ensure clearResources is not run for BaseSpec tests (which will cause it to fail).
    SpecificationContext currentSpecInfo = specificationContext;
    if (specificationContext.currentFeature?.name.contains("Scenario")) {
      log.info("--- Running Cleanup for test: ${currentSpecInfo.currentIteration?.name ?: currentSpecInfo.currentFeature?.name ?: currentSpecInfo.currentSpec.name} in ${CombinationDeletionSpec.simpleName} ---")
      try {
        clearResources()
      } catch (Exception e) {
        log.error("--- Error during SimpleDeletionSpec cleanup: ${e.message}", e)
      }
      log.info("--- ${CombinationDeletionSpec.simpleName} Cleanup Complete ---")
    } else {
      log.info("--- Skipping SimpleDeletionSpec-specific cleanup for BaseSpec feature run in: ${currentSpecInfo.currentSpec.displayName} (Feature: ${currentSpecInfo.currentFeature?.name}) ---")
    }
  }

  List<List<String>> generateSubCombinations(List<String> originalList) {
    // Start with a list containing just the empty list, as it's a valid combination.
    List<List<String>> powerSet = [[]]

    // For each element in the original list...
    originalList.each { element ->
      // For every combination already found so far...
      // We need to iterate over a copy of powerSet at this point,
      // because we are modifying powerSet inside the loop.
      List<List<String>> newCombinationsForThisElement = []
      powerSet.each { existingCombination ->
        // Create a new combination by adding the current element
        // to the existing combination.
        newCombinationsForThisElement.add(new ArrayList<>(existingCombination) + element)
      }
      powerSet.addAll(newCombinationsForThisElement)
    }
    return powerSet
  }

  void "Test populate"() {
    when:
    List<String> structures = ["simple", "top-link", "ti-link"]

    Map<String, List<String>> resourcesByStructure = [
      "simple": ["PCI1", "PTI1"],
      "top-link": ["PCI1", "PCI2", "PTI1"],
      "ti-link": ["PCI1", "PCI2", "PTI1", "PTI2"]
    ]
    simpleCombinations = generateSubCombinations(resourcesByStructure.get("simple"))
    topLinkCombinations = generateSubCombinations(resourcesByStructure.get("top-link"))
    tiLinkCombinations = generateSubCombinations(resourcesByStructure.get("ti-link"))
    then:
    true
  }

  void "Scenario 1: simple"() {
    setup:
    seedDatabaseWithStructure("simple")



    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "simple")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "simple")
    visualiseHierarchy(idsForProcessing.get("pci"))

    String agreement_name = agreementName
    Map agreementResp = createAgreement(agreement_name)
    idsForAgreementLines.keySet().forEach{String resourceKey -> {
      if (!idsForAgreementLines.get(resourceKey).isEmpty()) {
        idsForAgreementLines.get(resourceKey).forEach{String id -> {
          log.info("agreement line resource id: {}", id)
          addEntitlementForAgreement(agreement_name, id)
        }}
      }
    }}
    log.info("IDs for processing: ${idsForProcessing}")
    log.info("IDs for agreement lines: ${idsForAgreementLines}")

    // TODO: Implement "doDelete" in where block.
//    String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
    Map operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    log.info("Operation Response: ${operationResponse}")
    log.info("KB Stats: ${kbStatsResp}")

    then:
      assert true

    where:
    [currentInputResources, currentAgreementLines] <<
      simpleCombinations.collectMany { inputResourceCombo ->
        simpleCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }

  }
//
//  void "Scenario 2: top-link"() {
//    setup:
//    seedDatabaseWithStructure("top-link")
//
//    when: "The PCI created during setup is marked for deletion"
//    log.info("CURRENT ITERATION:")
//    log.info(currentInputResources.toListString())
//    log.info(currentAgreementLines.toListString())
//    then:
//    assert true
//
//    where:
//    [currentInputResources, currentAgreementLines] <<
//      topLinkCombinations.collectMany { inputResourceCombo ->
//        topLinkCombinations.collect { agreementLineCombo ->
//          [inputResourceCombo, agreementLineCombo]
//        }
//      }
//
//  }
//
//  void "Scenario 3 ti-link"() {
//    setup:
//    seedDatabaseWithStructure("ti-link")
//    when: "The PCI created during setup is marked for deletion"
//    log.info("CURRENT ITERATION:")
//    log.info(currentInputResources.toListString())
//    log.info(currentAgreementLines.toListString())
//    then:
//    assert true
//
//    where:
//    [currentInputResources, currentAgreementLines] <<
//      tiLinkCombinations.collectMany { inputResourceCombo ->
//        tiLinkCombinations.collect { agreementLineCombo ->
//          [inputResourceCombo, agreementLineCombo]
//        }
//      }
//
//  }

}
