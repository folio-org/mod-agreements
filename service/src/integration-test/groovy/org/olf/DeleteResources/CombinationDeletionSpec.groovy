package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.olf.erm.Entitlement
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Work
import org.olf.kb.metadata.PackageIngressMetadata
import org.spockframework.runtime.SpecificationContext
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise
import groovy.json.JsonOutput
import spock.lang.Unroll

@Integration
@Stepwise
@Slf4j
class CombinationDeletionSpec extends DeletionBaseSpec {


  @Shared
  Map<String, Integer> expectedKbStatsData;

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
    File scenariosFile = new File(EXPECTED_SCENARIOS_JSON_PATH) // Use constants for paths
    File kbStatsFile = new File(EXPECTED_KBSTATS_JSON_PATH)

    if (!scenariosFile.exists() || !kbStatsFile.exists()) {
      log.error("CRITICAL: Expected JSON files not found. Running population step.")
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
                expectedMarkForDelete: normalizeExpectedResponse(expectedValue), // Normalize here
                initialKbStats: new HashMap<>(loadedKbStats[structure]) // Fresh copy
              ])
            }
          }
        }
      }
    }
    log.info("Loaded ${allVerificationTestCases.size()} verification test cases.")
    log.info("${allVerificationTestCases.toString()}")
  }

  Map normalizeExpectedResponse(Map response) {
    if (response == null) response = [:] // Handle null response from API or JSON
    return [
      pci:  (response.pci  ?: []).sort() as Set, // Convert to Set for easier comparison
      pti:  (response.pti  ?: []).sort() as Set,
      ti:   (response.ti   ?: []).sort() as Set,
      work: (response.work ?: []).sort() as Set,
      error: response.error // Preserve error if present
    ]
  }

  void setupDataForTest(String structure) {
    seedDatabaseWithStructure(structure) // This is crucial per iteration
    File jsonFile = new File(EXPECTED_KBSTATS_JSON_PATH)
    if (!jsonFile.exists()) {
      log.error("KB Stats JSON file not found at ${EXPECTED_KBSTATS_JSON_PATH}")
      // throw new FileNotFoundException("KB Stats JSON file not found")
      expectedKbStatsData = [:] // Default to empty to avoid NPE, but test will likely fail
      return
    }
    def jsonSlurper = new JsonSlurper()
    Map<String, Integer> allKbStats = jsonSlurper.parse(jsonFile)
    expectedKbStatsData = allKbStats.get(structure) // This is the initial state for the given structure
    if (expectedKbStatsData == null) {
      log.warn("No KB stats data found for structure '${structure}' in ${EXPECTED_KBSTATS_JSON_PATH}")
      expectedKbStatsData = [:]
    }
  }

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

//  @Ignore
  void "For #testCase.structure: marking #testCase.resourceTypeToMark (#testCase.currentInputResources) with agreements (#testCase.currentAgreementLines) and delete=#testCase.doDelete"() {
    setup:
    log.info("In combinatorial test---")
    clearResources()
    setupDataForTest(testCase.structure) // Seeds DB, loads initial expectedKbStatsData

    when: "Resources are marked for deletion and optionally deleted"
    log.info("VERIFYING: Structure: ${testCase.structure}, Type: ${testCase.resourceTypeToMark}, Inputs: ${testCase.currentInputResources.toListString()}, Agreements: ${testCase.currentAgreementLines.toListString()}, doDelete: ${testCase.doDelete}")

    Set<String> idsForProcessing = findInputResourceIds(testCase.currentInputResources, testCase.structure)
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(testCase.currentAgreementLines, testCase.structure)

    if (testCase.resourceTypeToMark == "pci" && !idsForProcessing.isEmpty()) {
      visualiseHierarchy(idsForProcessing)
    }

    createAgreementLines(idsForAgreementLines)

    log.info("IDs to process for ${testCase.resourceTypeToMark}: ${idsForProcessing}")

    Map operationResponse
    Exception operationError

    // Only make a call if there are IDs to process for the designated resource type
    if (!testCase.resourceTypeToMark.isEmpty() && !idsForProcessing.isEmpty()) {
      String endpoint = "/erm/hierarchicalDelete/markForDelete/${testCase.resourceTypeToMark}" // e.g., /pci, /pti, /ti
      String payloadKey = "resources"
      try {
        operationResponse = doPost(endpoint, [(payloadKey): idsForProcessing])
      } catch (Exception e) {
        operationError = e
        log.error("Error calling markForDelete endpoint ${endpoint}: ${e.toString()}", e)
      }
    } else {
      // No specific resources selected for marking, or resourceTypeToMark is empty
      operationResponse = [pci: [], pti: [], ti: [], work: []]
    }

    Map kbStatsBeforeActualDelete = doGet("/erm/statistics/kbCount")
    Map finalKbStats = kbStatsBeforeActualDelete
    Map actualDeleteResponse

    if (testCase.doDelete && !operationError && operationResponse && !(operationResponse.pci.isEmpty() && operationResponse.pti.isEmpty() && operationResponse.ti.isEmpty() && operationResponse.work.isEmpty()) ) {
      // Only attempt actual delete if markForDelete was successful (no error, non-empty response)
      // And if there were items actually marked by the previous step
      log.info("Proceeding with actual delete operation for marked items: ${operationResponse}")
      try {
        String deleteEndpoint = "/erm/hierarchicalDelete/delete/${testCase.resourceTypeToMark}"
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
    if (testCase.expectedMarkForDelete.error) { // If an error was expected from markForDelete
      assert operationError // An error must have occurred
      assert operationError.message.contains(testCase.expectedMarkForDelete.error) // Check if message matches (can be fragile)
    } else if (operationError && !testCase.expectedMarkForDelete.error) {
      fail("Unexpected error during markForDelete: ${operationError.message}")
    } else {
      assertIdsMatch(testCase.structure, operationResponse, operationError, finalKbStats, testCase.expectedMarkForDelete)
    }

    // 2. Assert KB stats
    if (testCase.doDelete && !operationError) {
      // Calculate expected stats after the items from `expectedMarkForDelete` are gone
      Map expectedStatsAfterDelete = calculateExpectedKbStatsAfterDelete(
        testCase.initialKbStats, // Initial stats for this structure
        testCase.expectedMarkForDelete  // Items that should have been deleted
      )
      assertKbStatsMatch(finalKbStats, expectedStatsAfterDelete)
    } else { // No actual delete OR an error occurred during markForDelete
      // Stats should be same as after markForDelete (which is initialKbStats if markForDelete doesn't change counts,
      assertKbStatsMatch(finalKbStats, testCase.initialKbStats)
    }

    where:
    testCase << allVerificationTestCases.collect { it }
  }

  @Ignore
  void "populate data setup"() {
    when:
    resourcesByStructure.keySet().each{structure ->
    List<List<String>> currentInputCombos = inputResourceCombinationsByStructure[structure]
    List<List<String>> currentAgreementCombos = agreementLineCombinationsByStructure[structure]

    if (!currentInputCombos || !currentAgreementCombos) {
      log.warn("Missing combination definitions for structure: ${structure} in setupSpec. Skipping.")
      return // continue to next structure
    }

    currentInputCombos.each { inputResourceCombo ->
      currentAgreementCombos.each { agreementLineCombo ->
          String inputKey = inputResourceCombo.isEmpty() ? EMPTY_IDENTIFIER : inputResourceCombo.sort(false).join(",")
          String agreementKey = agreementLineCombo.isEmpty() ? EMPTY_IDENTIFIER : agreementLineCombo.sort(false).join(",")
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
            ])
        }
      }
    }
    then:
    true
  }

  @Ignore
  void "Populate expected outcomes for #testCase.structure, input resources: #testCase.currentInputResources, agreement lines: #testCase.currentAgreementLines scenarios"() {
    setup:
    clearResources()
    log.info(" --- Seeding populate --- ")
    seedDatabaseWithStructure(testCase.structure)

    // Determine pciIds for mapping based on structure
    Set<String> pciIdsForMapping
    switch (testCase.structure) {
      case "simple":
        pciIdsForMapping = [findPCIByPackageName(packageNameSimple1).id]
        break
      case "top-link":
        pciIdsForMapping = [findPCIByPackageName(packageNameTopLink1).id, findPCIByPackageName(packageNameTopLink2).id]
        break
      case "ti-link":
        pciIdsForMapping = [findPCIByPackageName(packageNameTiLink1).id, findPCIByPackageName(packageNameTiLink2).id]
        break
      case "work-link":
        pciIdsForMapping = [findPCIByPackageName(packageNameWorkLink1).id, findPCIByPackageName(packageNameWorkLink2).id]
        break
      default:
        throw new IllegalArgumentException("Unknown structure: $structure")
    }
    Map<String, String> pciMap = [:]
    Map<String, String> ptiMap = [:]
    Map<String, String> tiMap = [:]
    Map<String, String> workMap = [:]

    // Build the resource name maps (PCI1, PTI1, etc.)
    // e.g. pciMap {PCI1: 12312-123123-123, PCI2: 542524-123423-1231}
    buildResourceNameMaps(pciIdsForMapping, pciMap, ptiMap, tiMap, workMap)

    log.info(" --- Resource Maps created --- ")

    when:
    log.info("POPULATING: Structure: ${testCase.structure}, Inputs: ${testCase.currentInputResources.toListString()}, Agreements: ${testCase.currentAgreementLines.toListString()}")
    Set<String> idsForProcessing = findInputResourceIds(testCase.currentInputResources, testCase.structure)
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(testCase.currentAgreementLines, testCase.structure)
    String inputResourcesIdentifier = testCase.currentInputResources.isEmpty() ? EMPTY_IDENTIFIER : testCase.currentInputResources.sort(false).join(",")
    String agreementLinesIdentifier = testCase.currentAgreementLines.isEmpty() ? EMPTY_IDENTIFIER : testCase.currentAgreementLines.sort(false).join(",")
    String resourceType = parseResourceType(testCase.currentInputResources.get(0))

    try {
      createAgreement(agreementName)
    } catch (Exception e) {
      log.info(e.toString())
    }
    idsForAgreementLines.values().flatten().each { String resourceId ->
      if (resourceId) addEntitlementForAgreement(agreementName, resourceId)
    }

    Map operationResponse = [:]
    if (idsForProcessing.any { !it.isEmpty() } ) { // Only call if there's something to process
      try {
//        List<String> tisToProcess = (structure == "work-link") ? idsForProcessing['ti'] : [] // Only work-link uses TIs directly
        operationResponse = doPost("/erm/hierarchicalDelete/markForDelete/${resourceType}", ['resources': idsForProcessing])
        mapResponseIdsToNames(operationResponse, pciMap, ptiMap, tiMap, workMap)
      } catch (Exception e) {
        log.error("Error during markForDelete for structure ${structure}: ${e.toString()}", e)
        // Decide how to handle errors in population phase, maybe store error info
        operationResponse = [error: e.getMessage()]
      }
    } else {
      // Ensure consistent empty response structure
      operationResponse = [pci: [], pti: [], ti: [], work: []]
    }

    nestedScenarios
      .computeIfAbsent(testCase.structure, { [:] })
      .computeIfAbsent("inputResource", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .computeIfAbsent("agreementLine", { [:] })
      .put(agreementLinesIdentifier, ["expectedValue": operationResponse])


    then:
    log.info("Stored for ${testCase.structure} [${inputResourcesIdentifier}] / [${agreementLinesIdentifier}]: ${operationResponse}")
    true // Assert success of this population step

    where:
    // This combines all previous where blocks
    // Note: The .collectMany structure can be complex. Consider a helper if it gets too nested.
    testCase << allVerificationTestCases.collect { it }
  }

  @Ignore
  void "Scenario 2: Save JSON "() {
    setup:
    log.info("In setup")
    when:
    log.info("LOG DEBUG - SCENARIOS {}", nestedScenarios.toMapString())
    String jsonOutput = JsonOutput.prettyPrint(JsonOutput.toJson(nestedScenarios))

    new File("src/integration-test/resources/packages/hierarchicalDeletion/nestedScenarios.json").write(jsonOutput)
    then:
    true
  }


  void assertIdsMatch(String structure, Map operationResponse, Exception operationError, Map kbStatsResp, Map expectedMarkForDelete) {

    if (operationResponse) {
      // If no TIs were deleted, or all are deleted, we don't need to check their IDs.
      if (expectedMarkForDelete.get("ti").size() == kbStatsResp.get("TitleInstance") || expectedMarkForDelete.get("ti").size() == 0) {
        assert true
      }

      if (expectedMarkForDelete.get("work").size() == kbStatsResp.get("Work") || expectedMarkForDelete.get("work").size() == 0) {
        assert true
      }

      Set<String>  expectedPcis = findInputResourceIds(expectedMarkForDelete.get("pci") as List, structure)
      Set<String>  expectedPtis = findInputResourceIds(expectedMarkForDelete.get("pti") as List, structure)
      log.info("expected PCIs: {}", expectedPcis)
      assert expectedPcis == operationResponse.get("pci") as Set
      assert expectedPtis == operationResponse.get("pti") as Set
    }

    if (operationError) {
      log.info("Exception message: {}", operationError.message)
      operationError.message == "Id list cannot be empty."
    }
  }
}
