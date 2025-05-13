package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.olf.erm.Entitlement
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Work
import org.spockframework.runtime.SpecificationContext
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise
import groovy.json.JsonOutput

@Integration
@Stepwise
@Slf4j
class CombinationDeletionSpec extends DeletionBaseSpec {

  String packageNameSimple1 = "K-Int Deletion Test Package 001"
  String packageNameTopLink1 = "K-Int Link - Deletion Test Package 001";
  String packageNameTopLink2 = "K-Int Link - Deletion Test Package 002"
  String packageNameTiLink1 = "K-Int TI Link - Deletion Test Package 001";
  String packageNameTiLink2 = "K-Int TI Link - Deletion Test Package 002"
  String packageNameWorkLink1 = "K-Int Work Link - Deletion Test Package 001";
  String packageNameWorkLink2 = "K-Int Work Link - Deletion Test Package 002"
  String agreementName = "test_agreement"


  @Shared
  List<List<String>> simpleCombinations;

  @Shared
  List<List<String>> topLinkInputResourceCombinations;

  @Shared
  List<List<String>> topLinkAgreementLineCombinations;

  @Shared
  List<List<String>> tiLinkInputResourceCombinations;

  @Shared
  List<List<String>> tiLinkAgreementLineCombinations;

  @Shared
  List<List<String>> workLinkInputResourceCombinations;

  @Shared
  List<List<String>> workLinkAgreementLineCombinations;

  def seedDatabaseWithStructure(String structure) {
    if (structure == "simple") {
      importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
      doGet("/erm/packages", [filters: ["name==${packageNameSimple1}"]])
    }

    if (structure == "top-link") {
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion.json')
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion_link.json')

      List resp = doGet("/erm/packages", [filters: ["name==${packageNameTopLink1}"]])
      List resp2 = doGet("/erm/packages", [filters: ["name==${packageNameTopLink2}"]])
    }

    if (structure == "ti-link") {
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_1.json')
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_2.json')

      List resp = doGet("/erm/packages", [filters: ["name==${packageNameTiLink1}"]])
      List resp2 = doGet("/erm/packages", [filters: ["name==${packageNameTiLink2}"]])
    }

    if (structure == "work-link") {
      importPackageFromFileViaService('hierarchicalDeletion/work_link_deletion_1.json')
      importPackageFromFileViaService('hierarchicalDeletion/work_link_deletion_2.json')

      List resp = doGet("/erm/packages", [filters: ["name==${packageNameWorkLink1}"]])
      List resp2 = doGet("/erm/packages", [filters: ["name==${packageNameWorkLink2}"]])

      PackageContentItem pci1 = findPCIByPackageName(packageNameWorkLink1) // Use the work from this package as base.
      PackageContentItem pci2 = findPCIByPackageName(packageNameWorkLink2)

      String targetWorkId = pci1.pti.titleInstance.work.id
      String titleInstanceIdToUpdate = pci2.pti.titleInstance.id

      withTenant {
        Work targetWork = Work.get(targetWorkId)
        if (!targetWork) {
          log.error("Could not find target Work with ID {}.", targetWorkId)
          throw new IllegalStateException("Test setup error: Work ${targetWorkId} not found.")
        }

        int rowsAffected = TitleInstance.executeUpdate(
          """
            UPDATE TitleInstance ti 
            SET ti.work = :newWork 
            WHERE ti.id = :tiId
            """.toString(),
          [ newWork: targetWork, tiId: titleInstanceIdToUpdate ]
        )
      }
    }
  }

  String parseResourceType(String resource) {
    if (resource.startsWith("PCI")) {
      return "pci"
    }

    if (resource.startsWith("PTI")) {
      return "pti"
    }

    if (resource.startsWith("TI")) {
      return "ti"
    }
  }

  Map<String, Set<String>>  findInputResourceIds(List<String> inputResources, String structure) {
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

    if (structure == "work-link") {
      if (resource == "PCI1") {
        return findPCIByPackageName(packageNameWorkLink1)
      }

      if (resource == "PCI2") {
        return findPCIByPackageName(packageNameWorkLink2)
      }

      if (resource == "PTI1") {
        return findPCIByPackageName(packageNameWorkLink1).pti
      }

      if (resource == "PTI2") {
        return findPCIByPackageName(packageNameWorkLink2).pti
      }

      if (resource == "TI1") {
        return findPCIByPackageName(packageNameWorkLink1).pti.titleInstance
      }

      if (resource == "TI2") {
        return findPCIByPackageName(packageNameWorkLink2).pti.titleInstance
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

  private static String swapElementSuffix(String element) {
    if (element == null) return null
    if (element.endsWith("1")) {
      return element.substring(0, element.length() - 1) + "2"
    } else if (element.endsWith("2")) {
      return element.substring(0, element.length() - 1) + "1"
    }
    return element // No change if it doesn't end with 1 or 2
  }

  // Your trimSubCombinations modified with the "hasSisterForm" check
  List<List<String>> trimSubCombinations(List<List<String>> allSubCombinations) {
    Set<List<String>> formsAddedToSet = new HashSet<>() // Using a Set to store what we've decided to keep

    allSubCombinations.each { subCombination ->
      List<String> currentCandidate = new ArrayList<>(subCombination)
      List<String> formAfterInitialRule; // This is what your code called canonicalForm

      // Apply your original "2-only" transformation
      boolean isTwoOnly = !currentCandidate.isEmpty() &&
        currentCandidate.every { it.endsWith("2") };
      if (isTwoOnly) {
        formAfterInitialRule = currentCandidate.collect {
          if (it.length() > 0 && it.endsWith("2")) {
            return it.substring(0, it.length() - 1) + "1";
          }
          return it; // Should not happen if every element endsWith("2") but good for safety
        };
      } else {
        formAfterInitialRule = currentCandidate;
      }

      // Sort this form as it's a candidate for adding or comparison
      Collections.sort(formAfterInitialRule);

      // Now, generate its "fully swapped sister" form
      List<String> sisterForm = formAfterInitialRule.collect { swapElementSuffix(it) };
      Collections.sort(sisterForm); // The sister form must also be sorted for consistent Set checking

      // THE "hasSisterForm" CHECK:
      // If the sister form is already in our set of chosen forms, then we don't add the current one.
      if (formsAddedToSet.contains(sisterForm)) {
        // Sister is already present, so this formAfterInitialRule is redundant. Do nothing.
      } else {
        // Sister is not present. So, we add the current formAfterInitialRule.
        // This makes the choice dependent on processing order if formAfterInitialRule and sisterForm
        // are different (e.g. ["PCI1","PTI2"] vs ["PCI2","PTI1"]).
        // The one processed first whose sister isn't in the set will be added.
        formsAddedToSet.add(formAfterInitialRule);
      }
    }

    return new ArrayList<>(formsAddedToSet);
  }


  // Helper method to combine both steps
  List<List<String>> generateAndTrimSubCombinations(List<String> originalList) {
    List<List<String>> allCombinations = generateSubCombinations(originalList)
    return trimSubCombinations(allCombinations)
  }


  @Shared
  Map<String, Map<String, Map<String, List<String>>>> nestedScenarios = [:];

  void "Populate test" () {
    setup:
//      seedDatabaseWithStructure("simple")

    when:
    List<String> structures = ["simple", "top-link", "ti-link", "work-link"]

    Map<String, List<String>> resourcesByStructure = [
      "simple": ["PCI1", "PTI1"],
      "top-link": ["PCI1", "PCI2", "PTI1"],
      "ti-link": ["PCI1", "PCI2", "PTI1", "PTI2"],
      "work-link": ["PCI1", "PCI2", "PTI1", "PTI2", "TI1", "TI2"],
      "work-link-agreements": ["PCI1", "PCI2", "PTI1", "PTI2"]
    ]
    simpleCombinations = generateSubCombinations(resourcesByStructure.get("simple"))
    topLinkInputResourceCombinations = generateAndTrimSubCombinations(resourcesByStructure.get("top-link"))
    topLinkAgreementLineCombinations = generateSubCombinations(resourcesByStructure.get("top-link"))
    tiLinkInputResourceCombinations = generateAndTrimSubCombinations(resourcesByStructure.get("ti-link"))
    tiLinkAgreementLineCombinations = generateSubCombinations(resourcesByStructure.get("ti-link"))
    workLinkInputResourceCombinations = generateAndTrimSubCombinations(resourcesByStructure.get("work-link"))
    workLinkAgreementLineCombinations = generateSubCombinations(resourcesByStructure.get("work-link-agreements"))
    then:
      log.info(workLinkInputResourceCombinations.toListString())
     true
  }

  @Ignore
  void "Scenario 1a: simple-populate"() {
    setup:
    seedDatabaseWithStructure("simple")


    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "simple")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "simple")
    String inputResourcesIdentifier = currentInputResources.sort(false).join(",")
    String agreementLinesIdentifier = currentAgreementLines.sort(false).join(",")


    visualiseHierarchy(idsForProcessing.get("pci"))
    Map operationResponse = new HashMap();

    String agreement_name = agreementName
    Map agreementResp = createAgreement(agreement_name)
    Set<String> pciIds = [findPCIByPackageName("K-Int Deletion Test Package 001").id]
    Map<String, String> tiMap = [:]
    Map<String, String> workMap = [:]
    Map<String, String> pciMap = [:]
    Map<String, String> ptiMap = [:]

//            pciIds.eachWithIndex { pciId, index ->
//              pciMap[pciId] = "PCI${index + 1}"
//            }
    pciIds.eachWithIndex {String id, Integer index -> {
      PackageContentItem pci = findPCIById(id)
      pciMap[id] = "PCI${index + 1}"
      if (!ptiMap[pci.pti.id]) {
        ptiMap[pci.pti.id] = "PTI${index + 1}"
      }
      if (!tiMap[pci.pti.titleInstance.id]) {
        tiMap[pci.pti.titleInstance.id] = "TI${index + 1}"
      }
      if (!workMap[pci.pti.titleInstance.work.id]) {
        workMap[pci.pti.titleInstance.work.id] = "Work${index + 1}"
      }

      List<TitleInstance> titleInstanceList = findTisByWorkId([pci.pti.titleInstance.work.id] as Set)
      titleInstanceList.forEach {TitleInstance ti -> {
        int startIndex = tiMap.keySet().size();
        if (!tiMap[ti.id]) {
          tiMap[ti.id] = "TI${startIndex + 1}"
          startIndex += 1
        }
      }}



      idsForAgreementLines.keySet().forEach{String resourceKey -> {
        if (!idsForAgreementLines.get(resourceKey).isEmpty()) {
          idsForAgreementLines.get(resourceKey).forEach{String resourceId -> {
            log.info("agreement line resource id: {}", resourceId)
            addEntitlementForAgreement(agreement_name, resourceId)
          }}
        }
      }}



      log.info("PCI Map: ${pciMap}")
      log.info("PTI Map: ${ptiMap}")
      log.info("TI Map: ${tiMap}")
      log.info("Work Map: ${workMap}")

      log.info("IDs for processing: ${idsForProcessing}")
      log.info("IDs for agreement lines: ${idsForAgreementLines}")

      if (idsForProcessing.isEmpty()) {
        operationResponse = new HashMap();
        return
      }

      try {
        operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])

      } catch (Exception e) {
        log.info(e.toString())
      }

      log.info("Operation Response: ${operationResponse}")
      operationResponse.each { key, value ->
        if (key == 'pci') {
          operationResponse[key] = value.collect { pciId ->
            pciMap[pciId] ?: pciId
          }
        }

        if (key == 'pti') {
          operationResponse[key] = value.collect { ptiId ->
            ptiMap[ptiId] ?: ptiId
          }
        }

        if (key == 'ti') {
          operationResponse[key] = value.collect { tiId ->
            tiMap[tiId] ?: tiId
          }
        }

        if (key == 'work') {
          operationResponse[key] = value.collect { workId ->
            workMap[workId] ?: workId
          }
        }


      }
      log.info("Operation Response: ${operationResponse}")


    }
    }
    nestedScenarios
      .computeIfAbsent("simple", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .put(agreementLinesIdentifier, operationResponse)
    then:
    log.info(nestedScenarios.toMapString())

    assert true

    where:
    [currentInputResources, currentAgreementLines] <<
      simpleCombinations.collectMany { inputResourceCombo ->
        simpleCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }

  }

  @Ignore
  void "Scenario 1b: ti-link-populate"() {
    setup:
    seedDatabaseWithStructure("ti-link")


    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "ti-link")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "ti-link")
    String inputResourcesIdentifier = currentInputResources.sort(false).join(",")
    String agreementLinesIdentifier = currentAgreementLines.sort(false).join(",")


    visualiseHierarchy(idsForProcessing.get("pci"))
    Map operationResponse = new HashMap();

    String agreement_name = agreementName
    Map agreementResp = createAgreement(agreement_name)
    Set<String> pciIds = [findPCIByPackageName("K-Int TI Link - Deletion Test Package 001").id, findPCIByPackageName("K-Int TI Link - Deletion Test Package 002").id]
    Map<String, String> tiMap = [:]
    Map<String, String> workMap = [:]
    Map<String, String> pciMap = [:]
    Map<String, String> ptiMap = [:]

//            pciIds.eachWithIndex { pciId, index ->
//              pciMap[pciId] = "PCI${index + 1}"
//            }
    pciIds.eachWithIndex {String id, Integer index -> {
      PackageContentItem pci = findPCIById(id)
      pciMap[id] = "PCI${index + 1}"
      if (!ptiMap[pci.pti.id]) {
        ptiMap[pci.pti.id] = "PTI${index + 1}"
      }
      if (!tiMap[pci.pti.titleInstance.id]) {
        tiMap[pci.pti.titleInstance.id] = "TI${index + 1}"
      }
      if (!workMap[pci.pti.titleInstance.work.id]) {
        workMap[pci.pti.titleInstance.work.id] = "Work${index + 1}"
      }

      List<TitleInstance> titleInstanceList = findTisByWorkId([pci.pti.titleInstance.work.id] as Set)
      titleInstanceList.forEach {TitleInstance ti -> {
        int startIndex = tiMap.keySet().size();
        if (!tiMap[ti.id]) {
          tiMap[ti.id] = "TI${startIndex + 1}"
          startIndex += 1
        }
      }}



      idsForAgreementLines.keySet().forEach{String resourceKey -> {
        if (!idsForAgreementLines.get(resourceKey).isEmpty()) {
          idsForAgreementLines.get(resourceKey).forEach{String resourceId -> {
            log.info("agreement line resource id: {}", resourceId)
            addEntitlementForAgreement(agreement_name, resourceId)
          }}
        }
      }}



      log.info("PCI Map: ${pciMap}")
      log.info("PTI Map: ${ptiMap}")
      log.info("TI Map: ${tiMap}")
      log.info("Work Map: ${workMap}")

      log.info("IDs for processing: ${idsForProcessing}")
      log.info("IDs for agreement lines: ${idsForAgreementLines}")

      if (idsForProcessing.isEmpty()) {
        operationResponse = new HashMap();
        return
      }

      try {
        operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])

      } catch (Exception e) {
        log.info(e.toString())
      }

      log.info("Operation Response: ${operationResponse}")
      operationResponse.each { key, value ->
        if (key == 'pci') {
          operationResponse[key] = value.collect { pciId ->
            pciMap[pciId] ?: pciId
          }
        }

        if (key == 'pti') {
          operationResponse[key] = value.collect { ptiId ->
            ptiMap[ptiId] ?: ptiId
          }
        }

        if (key == 'ti') {
          operationResponse[key] = value.collect { tiId ->
            tiMap[tiId] ?: tiId
          }
        }

        if (key == 'work') {
          operationResponse[key] = value.collect { workId ->
            workMap[workId] ?: workId
          }
        }


      }
      log.info("Operation Response: ${operationResponse}")


    }
    }
    nestedScenarios
      .computeIfAbsent("ti-link", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .put(agreementLinesIdentifier, operationResponse)
    then:
    log.info(nestedScenarios.toMapString())

    assert true

    where:
    [currentInputResources, currentAgreementLines] <<
      tiLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        tiLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }

  }

  @Ignore
  void "Scenario 1c: top-link-populate"() {
    setup:
    seedDatabaseWithStructure("top-link")


    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "top-link")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "top-link")
    String inputResourcesIdentifier = currentInputResources.sort(false).join(",")
    String agreementLinesIdentifier = currentAgreementLines.sort(false).join(",")


    visualiseHierarchy(idsForProcessing.get("pci"))
    Map operationResponse = new HashMap();

    String agreement_name = agreementName
    Map agreementResp = createAgreement(agreement_name)
    Set<String> pciIds = [findPCIByPackageName("K-Int Link - Deletion Test Package 001").id, findPCIByPackageName("K-Int Link - Deletion Test Package 002").id]
    Map<String, String> tiMap = [:]
    Map<String, String> workMap = [:]
    Map<String, String> pciMap = [:]
    Map<String, String> ptiMap = [:]

//            pciIds.eachWithIndex { pciId, index ->
//              pciMap[pciId] = "PCI${index + 1}"
//            }
    pciIds.eachWithIndex {String id, Integer index -> {
      PackageContentItem pci = findPCIById(id)
      pciMap[id] = "PCI${index + 1}"
      if (!ptiMap[pci.pti.id]) {
        ptiMap[pci.pti.id] = "PTI${index + 1}"
      }
      if (!tiMap[pci.pti.titleInstance.id]) {
        tiMap[pci.pti.titleInstance.id] = "TI${index + 1}"
      }
      if (!workMap[pci.pti.titleInstance.work.id]) {
        workMap[pci.pti.titleInstance.work.id] = "Work${index + 1}"
      }

      List<TitleInstance> titleInstanceList = findTisByWorkId([pci.pti.titleInstance.work.id] as Set)
      titleInstanceList.forEach {TitleInstance ti -> {
        int startIndex = tiMap.keySet().size();
        if (!tiMap[ti.id]) {
          tiMap[ti.id] = "TI${startIndex + 1}"
          startIndex += 1
        }
      }}



      idsForAgreementLines.keySet().forEach{String resourceKey -> {
        if (!idsForAgreementLines.get(resourceKey).isEmpty()) {
          idsForAgreementLines.get(resourceKey).forEach{String resourceId -> {
            log.info("agreement line resource id: {}", resourceId)
            addEntitlementForAgreement(agreement_name, resourceId)
          }}
        }
      }}



      log.info("PCI Map: ${pciMap}")
      log.info("PTI Map: ${ptiMap}")
      log.info("TI Map: ${tiMap}")
      log.info("Work Map: ${workMap}")

      log.info("IDs for processing: ${idsForProcessing}")
      log.info("IDs for agreement lines: ${idsForAgreementLines}")

      if (idsForProcessing.isEmpty()) {
        operationResponse = new HashMap();
        return
      }

      try {
        operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])

      } catch (Exception e) {
        log.info(e.toString())
      }

      log.info("Operation Response: ${operationResponse}")
      operationResponse.each { key, value ->
        if (key == 'pci') {
          operationResponse[key] = value.collect { pciId ->
            pciMap[pciId] ?: pciId
          }
        }

        if (key == 'pti') {
          operationResponse[key] = value.collect { ptiId ->
            ptiMap[ptiId] ?: ptiId
          }
        }

        if (key == 'ti') {
          operationResponse[key] = value.collect { tiId ->
            tiMap[tiId] ?: tiId
          }
        }

        if (key == 'work') {
          operationResponse[key] = value.collect { workId ->
            workMap[workId] ?: workId
          }
        }


      }
      log.info("Operation Response: ${operationResponse}")


    }
    }
    nestedScenarios
      .computeIfAbsent("top-link", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .put(agreementLinesIdentifier, operationResponse)
    then:
    log.info(nestedScenarios.toMapString())

    assert true

    where:
    [currentInputResources, currentAgreementLines] <<
      topLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        topLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }
  }

//  @Ignore
  void "Scenario 1d: work-link-populate"() {
    setup:
    seedDatabaseWithStructure("work-link")


    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "work-link")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "work-link")
    String inputResourcesIdentifier = currentInputResources.sort(false).join(",")
    String agreementLinesIdentifier = currentAgreementLines.sort(false).join(",")


    visualiseHierarchy(idsForProcessing.get("pci"))
    Map operationResponse = new HashMap();

    String agreement_name = agreementName
    Map agreementResp = createAgreement(agreement_name)
    Set<String> pciIds = [findPCIByPackageName(packageNameWorkLink1).id, findPCIByPackageName(packageNameWorkLink2).id]
    Map<String, String> tiMap = [:]
    Map<String, String> workMap = [:]
    Map<String, String> pciMap = [:]
    Map<String, String> ptiMap = [:]

//            pciIds.eachWithIndex { pciId, index ->
//              pciMap[pciId] = "PCI${index + 1}"
//            }
    pciIds.eachWithIndex {String id, Integer index -> {
      PackageContentItem pci = findPCIById(id)
      pciMap[id] = "PCI${index + 1}"
      if (!ptiMap[pci.pti.id]) {
        ptiMap[pci.pti.id] = "PTI${index + 1}"
      }
      if (!tiMap[pci.pti.titleInstance.id]) {
        tiMap[pci.pti.titleInstance.id] = "TI${index + 1}"
      }
      if (!workMap[pci.pti.titleInstance.work.id]) {
        workMap[pci.pti.titleInstance.work.id] = "Work${index + 1}"
      }

      List<TitleInstance> titleInstanceList = findTisByWorkId([pci.pti.titleInstance.work.id] as Set)
      titleInstanceList.forEach {TitleInstance ti -> {
        int startIndex = tiMap.keySet().size();
        if (!tiMap[ti.id]) {
          tiMap[ti.id] = "TI${startIndex + 1}"
          startIndex += 1
        }
      }}



      idsForAgreementLines.keySet().forEach{String resourceKey -> {
        if (!idsForAgreementLines.get(resourceKey).isEmpty()) {
          idsForAgreementLines.get(resourceKey).forEach{String resourceId -> {
            log.info("agreement line resource id: {}", resourceId)
            addEntitlementForAgreement(agreement_name, resourceId)
          }}
        }
      }}



      log.info("PCI Map: ${pciMap}")
      log.info("PTI Map: ${ptiMap}")
      log.info("TI Map: ${tiMap}")
      log.info("Work Map: ${workMap}")

      log.info("IDs for processing: ${idsForProcessing}")
      log.info("IDs for agreement lines: ${idsForAgreementLines}")

      if (idsForProcessing.isEmpty()) {
        operationResponse = new HashMap();
        return
      }

      try {
        operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])

      } catch (Exception e) {
        log.info(e.toString())
      }

      log.info("Operation Response: ${operationResponse}")
      operationResponse.each { key, value ->
        if (key == 'pci') {
          operationResponse[key] = value.collect { pciId ->
            pciMap[pciId] ?: pciId
          }
        }

        if (key == 'pti') {
          operationResponse[key] = value.collect { ptiId ->
            ptiMap[ptiId] ?: ptiId
          }
        }

        if (key == 'ti') {
          operationResponse[key] = value.collect { tiId ->
            tiMap[tiId] ?: tiId
          }
        }

        if (key == 'work') {
          operationResponse[key] = value.collect { workId ->
            workMap[workId] ?: workId
          }
        }


      }
      log.info("Operation Response: ${operationResponse}")


    }
    }
    nestedScenarios
      .computeIfAbsent("work-link", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .put(agreementLinesIdentifier, operationResponse)
    then:
    log.info(nestedScenarios.toMapString())

    assert true

    where:
    [currentInputResources, currentAgreementLines] <<
      workLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        workLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }

  }

//  @Ignore
  void "Scenario 4: Save JSON "() {
    setup:
    log.info("In setup")
    when:
      log.info("LOG DEBUG - SCENARIOS {}", nestedScenarios.toMapString())
        String jsonOutput = JsonOutput.prettyPrint(JsonOutput.toJson(nestedScenarios))

        new File("src/integration-test/resources/packages/hierarchicalDeletion/nestedScenarios.json").write(jsonOutput)
    then:
      true
  }

  @Ignore
  void "Scenario 1: simple"() {
    setup:
      seedDatabaseWithStructure("simple")
      File jsonFile = new File("src/integration-test/resources/packages/hierarchicalDeletion/expectedKbStats.json")
      File scenarios = new File("src/integration-test/resources/packages/hierarchicalDeletion/nestedScenarios.json")
      def jsonSlurper = new JsonSlurper()

      // Parse JSON into a nested Map
      Map<String, Integer> expectedKbStatsData = jsonSlurper.parse(jsonFile).get("simple")
      Map<String, Map<String, Map<String, List<String>>>> nestedScenarios = jsonSlurper.parse(scenarios)
      Map<String, Map<String, List<String>>> simpleScenarios = nestedScenarios.get("simple")

    when: "The PCI created during setup is marked for deletion"
      log.info("Nested scenarios: {}", nestedScenarios.toMapString())
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
      Map operationResponse;
      Exception operationError;
      try {
        operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])
      } catch (Exception e) {
        operationError = e;
        log.info(e.toString())
      }
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")
      log.info("Expected KB Stats: ${expectedKbStatsData}")
      log.info("Expected marked for deletion: {}", simpleScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(",")))
      Map<String, List<String>> expectedMarkForDelete = simpleScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(","));
    then:
      expectedKbStatsData.get("PackageContentItem") == kbStatsResp.get("PackageContentItem")
      expectedKbStatsData.get("PlatformTitleInstance") == kbStatsResp.get("PlatformTitleInstance")
      expectedKbStatsData.get("TitleInstance") == kbStatsResp.get("TitleInstance")
      expectedKbStatsData.get("Work") == kbStatsResp.get("Work")

      if (operationResponse) {
        Map<String, Set<String>>  expectedPcis = findInputResourceIds(expectedMarkForDelete.get("pci"), "simple")
        Map<String, Set<String>>  expectedPtis = findInputResourceIds(expectedMarkForDelete.get("pti"), "simple")
        expectedPcis.get("pci") == operationResponse.get("pci") as Set
        expectedPtis.get("pti") == operationResponse.get("pti") as Set

        // Works and Tis are either all deleted or all kept, so no need to check IDs. Can assert false if this is not true.
        if (expectedMarkForDelete.get("ti").size() == kbStatsResp.get("TitleInstance") || expectedMarkForDelete.get("ti").size() == 0) {
          assert true
        } else {
          assert false
        }

        if (expectedMarkForDelete.get("work").size() == kbStatsResp.get("Work") || expectedMarkForDelete.get("work").size() == 0) {
          assert true
        } else {
          assert false
        }

        if (operationError) {
          log.info("Exception message: {}", operationError.message)
          operationError.message == "Id list cannot be empty."
        }

        log.info("PCIS {}", expectedPcis.toMapString())
        log.info("PTIS {}", expectedPtis.toMapString())
      }



    where:
      [currentInputResources, currentAgreementLines] <<
        simpleCombinations.collectMany { inputResourceCombo ->
          simpleCombinations.collect { agreementLineCombo ->
            [inputResourceCombo, agreementLineCombo]
          }
        }

  }

  @Ignore
  void "Scenario 2: top-link"() {
    setup:
    seedDatabaseWithStructure("top-link")
    File jsonFile = new File("src/integration-test/resources/packages/hierarchicalDeletion/expectedKbStats.json")
    File scenarios = new File("src/integration-test/resources/packages/hierarchicalDeletion/nestedScenarios.json")
    def jsonSlurper = new JsonSlurper()

    // Parse JSON into a nested Map
    Map<String, Integer> expectedKbStatsData = jsonSlurper.parse(jsonFile).get("top-link")
    Map<String, Map<String, Map<String, List<String>>>> nestedScenarios = jsonSlurper.parse(scenarios)
    Map<String, Map<String, List<String>>> topLinkScenarios = nestedScenarios.get("top-link")

    when: "The PCI created during setup is marked for deletion"
    log.info("Nested scenarios: {}", nestedScenarios.toMapString())
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "top-link")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "top-link")
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
    log.info("Expected KB Stats: ${expectedKbStatsData}")
    log.info("Expected marked for deletion: {}", topLinkScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(",")))
    Map<String, List<String>> expectedMarkForDelete = topLinkScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(","));
    then:
    expectedKbStatsData.get("PackageContentItem") == kbStatsResp.get("PackageContentItem")
    expectedKbStatsData.get("PlatformTitleInstance") == kbStatsResp.get("PlatformTitleInstance")
    expectedKbStatsData.get("TitleInstance") == kbStatsResp.get("TitleInstance")
    expectedKbStatsData.get("Work") == kbStatsResp.get("Work")

    if (expectedMarkForDelete.get("ti").size() == expectedKbStatsData.get("TitleInstance") || expectedMarkForDelete.get("ti").size() == 0) {
      assert true
    }

    if (expectedMarkForDelete.get("work").size() == expectedKbStatsData.get("Work") || expectedMarkForDelete.get("work").size() == 0) {
      assert true
    }

    Map<String, Set<String>>  expectedPcis = findInputResourceIds(expectedMarkForDelete.get("pci"), "top-link")
    Map<String, Set<String>>  expectedPtis = findInputResourceIds(expectedMarkForDelete.get("pti"), "top-link")
    expectedPcis.get("pci") == operationResponse.get("pci") as Set
    expectedPtis.get("pti") == operationResponse.get("pti") as Set
    log.info("PCIS {}", expectedPcis.toMapString())
    log.info("PTIS {}", expectedPtis.toMapString())

    where:
    [currentInputResources, currentAgreementLines] <<
      topLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        topLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }
  }

  @Ignore
  void "Scenario 3: work-link"() {
    setup:
    seedDatabaseWithStructure("work-link")
    File jsonFile = new File("src/integration-test/resources/packages/hierarchicalDeletion/expectedKbStats.json")
    File scenarios = new File("src/integration-test/resources/packages/hierarchicalDeletion/nestedScenarios.json")
    def jsonSlurper = new JsonSlurper()

    // Parse JSON into a nested Map
    Map<String, Integer> expectedKbStatsData = jsonSlurper.parse(jsonFile).get("work-link")
    Map<String, Map<String, Map<String, List<String>>>> nestedScenarios = jsonSlurper.parse(scenarios)
    Map<String, Map<String, List<String>>> workLinkScenarios = nestedScenarios.get("work-link")

    when: "The PCI created during setup is marked for deletion"
    log.info("Nested scenarios: {}", nestedScenarios.toMapString())
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "work-link")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "work-link")
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
    log.info("Expected KB Stats: ${expectedKbStatsData}")
    log.info("Expected marked for deletion: {}", workLinkScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(",")))
    Map<String, List<String>> expectedMarkForDelete = workLinkScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(","));
    then:
    expectedKbStatsData.get("PackageContentItem") == kbStatsResp.get("PackageContentItem")
    expectedKbStatsData.get("PlatformTitleInstance") == kbStatsResp.get("PlatformTitleInstance")
    expectedKbStatsData.get("TitleInstance") == kbStatsResp.get("TitleInstance")
    expectedKbStatsData.get("Work") == kbStatsResp.get("Work")

    if (expectedMarkForDelete.get("ti").size() == expectedKbStatsData.get("TitleInstance") || expectedMarkForDelete.get("ti").size() == 0) {
      assert true
    }

    if (expectedMarkForDelete.get("work").size() == expectedKbStatsData.get("Work") || expectedMarkForDelete.get("work").size() == 0) {
      assert true
    }

    Map<String, Set<String>>  expectedPcis = findInputResourceIds(expectedMarkForDelete.get("pci"), "top-link")
    Map<String, Set<String>>  expectedPtis = findInputResourceIds(expectedMarkForDelete.get("pti"), "top-link")
    expectedPcis.get("pci") == operationResponse.get("pci") as Set
    expectedPtis.get("pti") == operationResponse.get("pti") as Set
    log.info("PCIS {}", expectedPcis.toMapString())
    log.info("PTIS {}", expectedPtis.toMapString())

    where:
    [currentInputResources, currentAgreementLines] <<
      workLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        workLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }

  }

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
