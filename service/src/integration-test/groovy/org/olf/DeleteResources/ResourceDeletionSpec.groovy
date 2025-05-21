package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
@Slf4j
class ResourceDeletionSpec extends DeletionBaseSpec {

  @Shared
  Map<String, List<String>> resourcesByStructure = [
    "simple": ["PCI1", "PTI1"],
    "top-link": ["PCI1", "PCI2", "PTI1"],
    "ti-link": ["PCI1", "PCI2", "PTI1", "PTI2"],
    "work-link": ["PCI1", "PCI2", "PTI1", "PTI2"],
  ]

  @Shared
  Map<String, List<List<String>>> inputResourceCombinationsByStructure = [
    "simple":    [["PCI1"], ["PTI1"]],
    "top-link": [["PCI1"], ["PTI1"], ["PCI1", "PCI2"]],
    "ti-link":   [["PCI1"], ["PTI1"], ["PCI1", "PCI2"], ["PTI1", "PTI2"]],
    "work-link": [["PCI1"], ["PTI1"], ["PCI1", "PCI2"], ["PTI1", "PTI2"]],
  ]

  @Shared
  Map<String, List<List<String>>> agreementLineCombinationsByStructure = [
    "simple":    generateSubCombinations(resourcesByStructure.get("simple")), // Original simpleCombinations
    "top-link":  generateSubCombinations(resourcesByStructure.get("top-link")), // Original topLinkAgreementLineCombinations
    "ti-link":   generateSubCombinations(resourcesByStructure.get("ti-link")), // Original tiLinkAgreementLineCombinations
    "work-link": generateSubCombinations(resourcesByStructure.get("work-link").findAll{!it.startsWith("TI")}), // Agreements typically on PCI/PTI
  ]

  @Shared List<Map> allVerificationTestCases = []

  def setupSpec() {
    log.info("--- CombinationDeletionSpec: setupSpec ---")
    File scenariosFile = new File(EXPECTED_SCENARIOS_JSON_PATH)
    File kbStatsFile = new File(EXPECTED_KBSTATS_JSON_PATH)

    if (!scenariosFile.exists() || !kbStatsFile.exists()) {
      log.error("Expected Test Case files ${EXPECTED_SCENARIOS_JSON_PATH} and ${EXPECTED_KBSTATS_JSON_PATH} not found.")
      return
    }

    def jsonSlurper = new JsonSlurper()
    Map<String, Map<String, Map<String, Map<String, Map<String, List<String>>>>>> loadedScenarios = jsonSlurper.parse(scenariosFile)
    Map<String, Map<String, Integer>> loadedKbStats = jsonSlurper.parse(kbStatsFile)

    loadedScenarios.each { structure, structureData ->
      List<List<String>> currentInputCombos = inputResourceCombinationsByStructure[structure]
      List<List<String>> currentAgreementCombos = agreementLineCombinationsByStructure[structure]

      if (!currentInputCombos || !currentAgreementCombos) {
        log.warn("Missing combination definitions for structure: ${structure} in setupSpec. Skipping.")
        return // continue to next structure
      }

      currentInputCombos.each { inputResourceCombo ->
        currentAgreementCombos.each { agreementLineCombo ->
          [false, true].each { doDeleteFlag -> // Iterate over the boolean flags for actual deletion

            // Create keys for accessing the expected values for each test case.
            String inputKey = inputResourceCombo.isEmpty() ? EMPTY_IDENTIFIER : inputResourceCombo.sort(false).join(",")
            String agreementKey = agreementLineCombo.isEmpty() ? EMPTY_IDENTIFIER : agreementLineCombo.sort(false).join(",")

            // Get the expected outcome from the loaded JSON
            Map expectedValue = structureData.inputResource?.get(inputKey)?.agreementLine?.get(agreementKey)?.expectedValue
            if (expectedValue == null) {
              log.warn("Missing expected value in JSON for structure '${structure}', input '${inputKey}', agreement '${agreementKey}'. Skipping test case.")
            } else {
              // Determine resourceType from the first element of inputResourceCombo if not empty
              String resourceTypeToMark = ""
              if (!inputResourceCombo.isEmpty()) {
                resourceTypeToMark = parseResourceType(inputResourceCombo[0]) // PCI, PTI, or TI
              }

              allVerificationTestCases.add([
                structure: structure,
                resourceTypeToMark: resourceTypeToMark, // pci, pti, ti
                currentInputResources: inputResourceCombo,
                currentAgreementLines: agreementLineCombo,
                doDelete: doDeleteFlag,
                expectedMarkForDelete: expectedValue,
                initialKbStats: new HashMap<>(loadedKbStats[structure])
              ])
            }
          }
        }
      }
    }
    log.info("Loaded ${allVerificationTestCases.size()} verification test cases.")
    log.info("${allVerificationTestCases.toString()}")
  }

  void setupDataForTest(String structure) {
    seedDatabaseWithStructure(structure)
  }

//  @Ignore
  void "For #testCase.structure: marking #testCase.resourceTypeToMark (#testCase.currentInputResources) with agreements (#testCase.currentAgreementLines) and delete=#testCase.doDelete"() {
    setup:
      log.info("In combinatorial test---")
      clearResources()
      setupDataForTest(testCase.structure)

    when: "Resources are marked for deletion and optionally deleted"
      log.info("VERIFYING: Structure: ${testCase.structure}, Type: ${testCase.resourceTypeToMark}, Inputs: ${testCase.currentInputResources.toListString()}, Agreements: ${testCase.currentAgreementLines.toListString()}, doDelete: ${testCase.doDelete}")

      Set<String> idsForProcessing = findInputResourceIds(testCase.currentInputResources, testCase.structure)
      Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(testCase.currentAgreementLines, testCase.structure)
      createAgreementLines(idsForAgreementLines)

      log.info("IDs to process for ${testCase.resourceTypeToMark}: ${idsForProcessing}")

      Map operationResponse
      Exception operationError

    // Only make a call if there are IDs to process for the designated resource type
    if (!testCase.resourceTypeToMark.isEmpty() && !idsForProcessing.isEmpty()) {
      String endpoint = "/erm/resource/markForDelete/${testCase.resourceTypeToMark}" // e.g., /pci, /pti, /ti
      String payloadKey = "resources"
      try {
        operationResponse = doPost(endpoint, [(payloadKey): idsForProcessing])
      } catch (Exception e) {
        operationError = e
        log.error("Error calling markForDelete endpoint ${endpoint}: ${e.toString()}", e)
      }
    } else {
      operationResponse = [pci: [], pti: [], ti: [], work: []]
    }

    Map kbStatsBeforeActualDelete = doGet("/erm/statistics/kbCount")
    Map finalKbStats = kbStatsBeforeActualDelete
    Map actualDeleteResponse

    if (testCase.doDelete && !operationError && operationResponse && !(operationResponse.pci.isEmpty() && operationResponse.pti.isEmpty() && operationResponse.ti.isEmpty() && operationResponse.work.isEmpty()) ) {
      // Only attempt delete if markForDelete was successful (no error, non-empty response)
      // And if there were items actually marked by the previous step
      log.info("Proceeding with delete operation for marked items: ${operationResponse}")
      try {
        String deleteEndpoint = "/erm/resource/delete/${testCase.resourceTypeToMark}"
        String deletePayloadKey = "resources"

        if (!idsForProcessing.isEmpty()) {
          actualDeleteResponse = doPost(deleteEndpoint, [(deletePayloadKey): idsForProcessing])
          finalKbStats = doGet("/erm/statistics/kbCount") // Get stats *after* actual delete
        } else {
          log.info("Skipping actual delete as no IDs were initially processed for this type.")
        }
      } catch (Exception e) {
        log.error("Error during actual delete operation: ${e.toString()}", e)
      }
    }

    log.info("MarkForDelete Operation Response: ${operationResponse}")
    if (testCase.doDelete) log.info("ActualDelete Operation Response: ${actualDeleteResponse}")
    log.info("Final KB Stats: ${finalKbStats}")
    log.info("Expected outcome from markForDelete (JSON): ${testCase.expectedMarkForDelete}")

    then: "The system state matches the expected outcome"
    // 1. Assert the `markForDelete` operation's response (or error)
    if (testCase.doDelete) {
      // TODO: Check Ids deleted (could return from /delete endpoint) match those from MarkForDelete?
    } else {
     if (operationError) {
       // TODO: Do we need to verify error scenarios?
       log.info("Exception message: {}", operationError.message)
//       assert operationError.message == "Id list cannot be empty."
        fail("Unexpected error during markForDelete: ${operationError.message}")
      } else {
        assertIdsMatch(testCase.structure, operationResponse, testCase.expectedMarkForDelete)
      }
    }

    // 2. Assert KB stats
    if (testCase.doDelete && !operationError) {
      Map expectedStatsAfterDelete = calculateExpectedKbStatsAfterDelete(
        testCase.initialKbStats,
        testCase.expectedMarkForDelete
      )
      assertKbStatsMatch(finalKbStats, expectedStatsAfterDelete)
    } else { // if no error and just markForDelete check KB stats are unchanged.
      assertKbStatsMatch(finalKbStats, testCase.initialKbStats)
    }

    where:
    testCase << allVerificationTestCases.collect { it }
  }


  // Assertion methods:

  Map calculateExpectedKbStatsAfterDelete(Map initialStats, Map itemsExpectedToBeDeleted) {
    Map expectedStats = new HashMap<>(initialStats)
    if (itemsExpectedToBeDeleted && !itemsExpectedToBeDeleted.error) {
      expectedStats.PackageContentItem    -= (itemsExpectedToBeDeleted.pci?.size()  ?: 0)
      expectedStats.PlatformTitleInstance -= (itemsExpectedToBeDeleted.pti?.size()  ?: 0)
      expectedStats.TitleInstance         -= (itemsExpectedToBeDeleted.ti?.size()   ?: 0)
      expectedStats.Work                  -= (itemsExpectedToBeDeleted.work?.size() ?: 0)
    }
    return expectedStats
  }

  void assertKbStatsMatch(Map actualKbStats, Map expectedKbStats) {
    log.info("Asserting KB Stats: Actual=${actualKbStats}, Expected=${expectedKbStats}")
    assert expectedKbStats.PackageContentItem    == actualKbStats.PackageContentItem
    assert expectedKbStats.PlatformTitleInstance == actualKbStats.PlatformTitleInstance
    assert expectedKbStats.TitleInstance         == actualKbStats.TitleInstance
    assert expectedKbStats.Work                  == actualKbStats.Work
  }

  void assertIdsMatch(String structure, Map operationResponse, Map expectedMarkForDelete) {

    if (operationResponse) {
      Set<String>  expectedPcis = findInputResourceIds(expectedMarkForDelete.get("pci") as List, structure)
      Set<String>  expectedPtis = findInputResourceIds(expectedMarkForDelete.get("pti") as List, structure)
      Set<String>  expectedTis = findInputResourceIds(expectedMarkForDelete.get("ti") as List, structure)
      Set<String>  expectedWorks = findInputResourceIds(expectedMarkForDelete.get("work") as List, structure)

      log.info("expected PCIs: {}", expectedPcis)
      assert expectedPcis == operationResponse.get("pci") as Set
      assert expectedPtis == operationResponse.get("pti") as Set
      assert expectedTis == operationResponse.get("ti") as Set
      assert expectedWorks == operationResponse.get("work") as Set
    }

  }
}
