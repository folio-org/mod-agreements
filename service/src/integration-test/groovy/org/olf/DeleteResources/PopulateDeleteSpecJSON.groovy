package org.olf.DeleteResources

import org.olf.kb.PackageContentItem
import org.olf.kb.TitleInstance
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll
import groovy.json.JsonOutput

class PopulateDeleteSpecJSON extends DeletionBaseSpec {
  // At the top of your class or in a helper/base class
  static final boolean SHOULD_REGENERATE_EXPECTED_JSON =
    Boolean.getBoolean("regenerateExpectedJson") || "true".equalsIgnoreCase(System.getenv("REGENERATE_EXPECTED_JSON"))


  @Shared
  Map<String, Map<String, Map<String, Map<String, Map<String, List<String>>>>>> nestedScenarios = [:];

  @IgnoreIf({ !SHOULD_REGENERATE_EXPECTED_JSON }) // Only run if the flag is true
  @Unroll
  void "populate data setup for #testCase.structure, inputs: #testCase.currentInputResources, agreements: #testCase.currentAgreementLines"() {

    setup:
    log.info("--- REGENERATING: Seeding DB for population: Structure: ${testCase.structure} ---")
    clearResources() // Ensure clean slate for each population iteration
    seedDatabaseWithStructure(testCase.structure)

    // Determine pciIds for mapping based on structure
    Set<String> pciIdsForMapping
    switch (testCase.structure) {
      case "simple":    pciIdsForMapping = [findPCIByPackageName(packageNameSimple1)?.id].findAll{it} ; break
      case "top-link":  pciIdsForMapping = [findPCIByPackageName(packageNameTopLink1)?.id, findPCIByPackageName(packageNameTopLink2)?.id].findAll{it} ; break
      case "ti-link":   pciIdsForMapping = [findPCIByPackageName(packageNameTiLink1)?.id, findPCIByPackageName(packageNameTiLink2)?.id].findAll{it} ; break
      case "work-link": pciIdsForMapping = [findPCIByPackageName(packageNameWorkLink1)?.id, findPCIByPackageName(packageNameWorkLink2)?.id].findAll{it} ; break
      default: throw new IllegalArgumentException("Unknown structure for pciIdsForMapping: ${testCase.structure}")
    }
    Map<String, String> pciMap = [:], ptiMap = [:], tiMap = [:], workMap = [:]
    if (pciIdsForMapping) buildResourceNameMaps(pciIdsForMapping, pciMap, ptiMap, tiMap, workMap)

    when: "MarkForDelete is called to get expected outcomes"
    log.info("REGENERATING: Structure: ${testCase.structure}, Inputs: ${testCase.currentInputResources.toListString()}, Agreements: ${testCase.currentAgreementLines.toListString()}, Type: ${testCase.resourceTypeToMark}")

    Set<String> idsForProcessing = findInputResourceIds(testCase.currentInputResources, testCase.structure)
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(testCase.currentAgreementLines, testCase.structure)

    String inputResourcesIdentifier = testCase.currentInputResources.isEmpty() ? EMPTY_IDENTIFIER : testCase.currentInputResources.sort(false).join(",")
    String agreementLinesIdentifier = testCase.currentAgreementLines.isEmpty() ? EMPTY_IDENTIFIER : testCase.currentAgreementLines.sort(false).join(",")

    try {
      createAgreement(agreementName) // Ensure agreement exists
    } catch (Exception e) {
      log.warn("Could not create/find agreement during population: ${e.message}")
      // Decide if this is fatal for population
    }
    // Flatten and iterate, assuming addEntitlementForAgreement can handle potential nulls from parseResource
    idsForAgreementLines.values().flatten().unique().each { String resourceId ->
      if (resourceId) addEntitlementForAgreement(agreementName, resourceId)
    }

    Map operationResponse = [:]
    if (!testCase.resourceTypeToMark.isEmpty() && !idsForProcessing.isEmpty()) {
      String endpoint = "/erm/hierarchicalDelete/markForDelete/${testCase.resourceTypeToMark}"
      String payloadKey = "resources" // As per your controller change
      try {
        operationResponse = doPost(endpoint, [(payloadKey): idsForProcessing])
        // Convert UUIDs in response back to "PCI1", "PTI1" style names
        if (pciIdsForMapping) mapResponseIdsToNames(operationResponse, pciMap, ptiMap, tiMap, workMap)
      } catch (Exception e) {
        log.error("Error during markForDelete for regeneration (Structure: ${testCase.structure}, Type: ${testCase.resourceTypeToMark}, IDs: ${idsForProcessing}): ${e.toString()}", e)
        operationResponse = [error: e.getMessage()] // Store error in expected output
      }
    } else {
      // No resources to mark, or resourceTypeToMark is empty
      operationResponse = [pci: [], pti: [], ti: [], work: []] // Consistent empty response
    }

    // Accumulate into the @Shared nestedScenarios map
      nestedScenarios
        .computeIfAbsent(testCase.structure, { [:] })
        .computeIfAbsent("inputResource", { [:] })
        .computeIfAbsent(inputResourcesIdentifier, { [:] })
        .computeIfAbsent("agreementLine", { [:] })
        .put(agreementLinesIdentifier, ["expectedValue": operationResponse])


    then: "Expected outcome is stored"
    log.info("REGENERATED and Stored for ${testCase.structure} [${inputResourcesIdentifier}] / [${agreementLinesIdentifier}]: ${operationResponse}")
    true // This test's purpose is side-effect (populating nestedScenarios)

    where:
    testCase << regenerationTestCases() // New method to provide data for regeneration
  }

  List<Map> regenerationTestCases() {
    List<Map> cases = []
    List<String> structuresToPopulate = ["simple", "top-link", "ti-link", "work-link"]

    // Define base resources for generating combinations for each structure
    Map<String, List<String>> baseResourcesForStructure = [
      "simple":    ["PCI1", "PTI1"],
      "top-link":  ["PCI1", "PCI2", "PTI1"],
      "ti-link":   ["PCI1", "PCI2", "PTI1", "PTI2"],
      "work-link": ["PCI1", "PCI2", "PTI1", "PTI2", "TI1", "TI2"],
    ]
    // Define the resource types that can be directly targeted for marking in each structure
    Map<String, List<String>> markableResourceTypesForStructure = [
      "simple":    ["pci", "pti"],
      "top-link":  ["pci", "pti"],
      "ti-link":   ["pci", "pti"],
      "work-link": ["pci", "pti", "ti"],
    ]

    structuresToPopulate.each { structure ->
      List<String> allPossibleResources = baseResourcesForStructure[structure]
      List<List<String>> inputResourceCombos = generateSingleTypeCombinations(
        allPossibleResources,
        markableResourceTypesForStructure[structure]
      )
      List<String> agreementLineBaseResources = (structure == "work-link") ?
        allPossibleResources.findAll { !it.startsWith("TI") } :
        allPossibleResources
      List<List<String>> agreementLineCombos = generateSubCombinations(agreementLineBaseResources)

      inputResourceCombos.each { inputCombo ->
        agreementLineCombos.each { agreementCombo ->
          String resourceType = ""
          if (!inputCombo.isEmpty()) {
            resourceType = parseResourceType(inputCombo[0])
          }
          cases.add([
            structure: structure,
            resourceTypeToMark: resourceType,
            currentInputResources: inputCombo,
            currentAgreementLines: agreementCombo
          ])
        }
      }
    }
    return cases
  }

// Helper to build the PCI1, PTI1, etc. maps
  private void buildResourceNameMaps(Set<String> pciIds, Map pciMap, Map ptiMap, Map tiMap, Map workMap) {
    pciIds.eachWithIndex { String id, Integer index ->
      PackageContentItem pci = findPCIById(id)
      if (!pci) {
        log.warn("Could not find PCI with id ${id} for mapping")
        return // or throw error
      }
      String suffix = (index + 1).toString()

      pciMap[id] = "PCI${suffix}"
      if (pci.pti) {
        if (!ptiMap[pci.pti.id]) {
          ptiMap[pci.pti.id] = "PTI${suffix}"
        }
        if (pci.pti.titleInstance) {
          if (!tiMap[pci.pti.titleInstance.id]) {
            tiMap[pci.pti.titleInstance.id] = "TI${suffix}"
          }
          if (pci.pti.titleInstance.work) {
            if (!workMap[pci.pti.titleInstance.work.id]) {
              workMap[pci.pti.titleInstance.work.id] = "Work${suffix}"
            }


            List<TitleInstance> allTisForWork = findTisByWorkId([pci.pti.titleInstance.work.id] as Set)
            log.info("While building resource map, all tis for work")
            allTisForWork.forEach{{
              log.info(it.id)
              log.info("TI Map: {}", tiMap)
            }}
            allTisForWork.eachWithIndex { TitleInstance tiInstance, tiIndex ->
              if (!tiMap.containsKey(tiInstance.id)) {
                tiMap[tiInstance.id] = "TI${tiIndex + 1}" // If we add multiple works, may need Work${suffix}_
              }
            }
          }
        }
      }
    }
  }



  @IgnoreIf({ !SHOULD_REGENERATE_EXPECTED_JSON })
  void "Save Populated Scenarios to JSON"() { // Renamed from "Scenario 2: Save JSON"
    setup:
    log.info("--- REGENERATING: Preparing to save populated scenarios to JSON ---")
    if (nestedScenarios.isEmpty() && SHOULD_REGENERATE_EXPECTED_JSON) {
      log.warn("`nestedScenarios` map is empty. Ensure population step ran successfully before saving.")
      // Potentially skip if empty and regeneration was intended, or throw error
    }

    when: "The nestedScenarios map is converted to JSON and written to file"
    log.debug("Final `nestedScenarios` content: {}", JsonOutput.toJson(nestedScenarios)) // Use toJson for compact log
    String jsonOutput = JsonOutput.prettyPrint(JsonOutput.toJson(nestedScenarios))
    new File(EXPECTED_SCENARIOS_JSON_PATH).write(jsonOutput)

    then: "The JSON file is created/updated"
    log.info("Successfully saved populated scenarios to ${EXPECTED_SCENARIOS_JSON_PATH}")
    new File(EXPECTED_SCENARIOS_JSON_PATH).exists()
  }

// Helper for generating single type combinations (from previous suggestions)
  List<List<String>> generateSingleTypeCombinations(List<String> allPossibleResourcesForStructure, List<String> targetTypes) {
    List<List<String>> singleTypeCombos = [] // Exclude empty input case []
    targetTypes.each { typePrefix ->
      List<String> resourcesOfType = allPossibleResourcesForStructure.findAll {
        it.toUpperCase().startsWith(typePrefix.toUpperCase()) // Case-insensitive prefix match
      }
      generateSubCombinations(resourcesOfType).each { combo ->
        if (!combo.isEmpty()) {
          singleTypeCombos.add(combo)
        }
      }
    }
    return singleTypeCombos.unique{it.sort()} // Unique based on sorted content
  }

  // Helper to map API response IDs back to PCI1, PTI1 style names
  private void mapResponseIdsToNames(Map operationResponse, Map pciMap, Map ptiMap, Map tiMap, Map workMap) {
    operationResponse.each { key, value ->
      if (value instanceof List) {
        operationResponse[key] = value.collect { id ->
          switch (key) {
            case 'pci': return pciMap[id] ?: id
            case 'pti': return ptiMap[id] ?: id
            case 'ti': return tiMap[id] ?: id
            case 'work': return workMap[id] ?: id
            default: return id
          }
        }.sort() // Sort for consistent comparison
      }
    }
  }
}
