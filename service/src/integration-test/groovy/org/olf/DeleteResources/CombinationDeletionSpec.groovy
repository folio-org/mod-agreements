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

  @Shared
  Map<String, Integer> expectedKbStatsData;

  @Shared
  Map<String, Map<String, List<String>>> featureScenarios;



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

      // Attach the Electronic ti from package 2 to the work from package 1. Then cleanup the orphaned work + ti, and the print ti on package 1.
      withTenant {
        PackageContentItem pci1 = findPCIByPackageName(packageNameWorkLink1) // Use the work from this package as base.
        PackageContentItem pci2 = findPCIByPackageName(packageNameWorkLink2)
        String targetWorkId = pci1.pti.titleInstance.work.id
        String titleInstanceIdToUpdate = pci2.pti.titleInstance.id
        Work oldWorkInstance = pci2.pti.titleInstance.work

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

        PackageIngressMetadata.executeUpdate("DELETE FROM PackageIngressMetadata")


        IdentifierOccurrence.executeUpdate("DELETE FROM IdentifierOccurrence")


        ErmResource.executeUpdate(
          """DELETE FROM Period"""
        )

        // Delete orphaned TI
        TitleInstance.executeUpdate(
          """
        DELETE FROM TitleInstance ti
        WHERE ti.work = :oldWorkEntity
        """.toString(),
          [oldWorkEntity: oldWorkInstance] // Pass the entity instance
        )

        // Delete orphaned work.
        Work.executeUpdate(
          """
        DELETE FROM Work w
        WHERE w.id = :workId
        """.toString(),
          [workId: oldWorkInstance.id]
        )

        // Delete print TI remaining to leave 2 TIs total.
        TitleInstance.executeUpdate(
          """
        DELETE FROM TitleInstance ti
        WHERE ti.id NOT IN (
            SELECT pti.titleInstance.id 
            FROM PlatformTitleInstance pti 
            WHERE pti.titleInstance.id IS NOT NULL 
        )
        """.toString()
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
    // Taking a list of elements e.g. [a, b]
    // Output a list of lists of all combinations (ignoring order) including empties, e.g. [[], [a], [b], [a,b]]

    List<List<String>> powerSet = [[]]

    // For each element in the original list...
    // Will construct the above example like [[]] --> [[], [a]] --> [[], [a], [b], [a,b]]
    originalList.each { element ->
      List<List<String>> newCombinationsForThisElement = []
      powerSet.each { existingCombination ->
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
    return element
  }

  List<List<String>> trimSubCombinations(List<List<String>> allSubCombinations) {
    Set<List<String>> formsAddedToSet = new HashSet<>()

    allSubCombinations.each { subCombination ->
      List<String> currentCandidate = new ArrayList<>(subCombination)
      List<String> formAfterInitialRule;

      // Check if all resources belong on one branch (e.g. [PCI2, PTI2])
      // If so, convert them to the "1" form.
      boolean isTwoOnly = !currentCandidate.isEmpty() &&
        currentCandidate.every { it.endsWith("2") };
      if (isTwoOnly) {
        formAfterInitialRule = currentCandidate.collect {
          if (it.length() > 0 && it.endsWith("2")) {
            return it.substring(0, it.length() - 1) + "1";
          }
          return it;
        };
      } else {
        formAfterInitialRule = currentCandidate;
      }

      // Sort this form as it's a candidate for adding or comparison
      Collections.sort(formAfterInitialRule);

      List<String> sisterForm = formAfterInitialRule.collect { swapElementSuffix(it) };
      Collections.sort(sisterForm);

      // Now we have the "canonical" form for single-branch forms: i.e. [PCI2, PTI2] converted to [PCI1, PTI1]
      // but we could instead have [PCI1, PTI2]. Because this is equivalent to checking [PCI2, PTI1], we can
      // check if the "opposite/sister form already exists in the final set of combinations. If it does, skip.
      if (formsAddedToSet.contains(sisterForm)) {
        // Do nothing.
      } else {
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

//  @Ignore
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
    if (operationResponse.isEmpty()) {
      operationResponse.put("pci", [])
      operationResponse.put("pti", [])
      operationResponse.put("ti", [])
      operationResponse.put("work", [])
    }

    nestedScenarios
      .computeIfAbsent("simple", { [:] })
      .computeIfAbsent("inputResource", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .computeIfAbsent("agreementLine", { [:] })
      .put(agreementLinesIdentifier, ["expectedValue": operationResponse])
//    nestedScenarios
//      .computeIfAbsent("simple", { [:] })
//      .computeIfAbsent(inputResourcesIdentifier, { [:] })
//      .put(agreementLinesIdentifier, operationResponse)
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
      .computeIfAbsent("inputResource", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .computeIfAbsent("agreementLine", { [:] })
      .put(agreementLinesIdentifier, ["expectedValue": operationResponse])
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
      .computeIfAbsent("inputResource", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .computeIfAbsent("agreementLine", { [:] })
      .put(agreementLinesIdentifier, ["expectedValue": operationResponse])
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

  @Ignore
  void "Scenario 1d: work-link-populate"() {
    setup:
    seedDatabaseWithStructure("work-link")


    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION:")
    log.info(currentInputResources.toListString())
    log.info(currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, "work-link")
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, "work-link")
    String inputResourcesIdentifier = currentInputResources.isEmpty() ? "Empty" : currentInputResources.sort(false).join(",")
    String agreementLinesIdentifier = currentAgreementLines.isEmpty() ? "Empty" : currentAgreementLines.sort(false).join(",")


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
        operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti'], 'tis': idsForProcessing['ti']])

      } catch (Exception e) {
        log.info(e.toString())
      }

      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")

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
      .computeIfAbsent("inputResource", { [:] })
      .computeIfAbsent(inputResourcesIdentifier, { [:] })
      .computeIfAbsent("agreementLine", { [:] })
      .put(agreementLinesIdentifier, ["expectedValue": operationResponse])
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

  void setupDataForTest(String structure) {
    seedDatabaseWithStructure(structure)
    File jsonFile = new File("src/integration-test/resources/packages/hierarchicalDeletion/expectedKbStats.json")
    File scenarios = new File("src/integration-test/resources/packages/hierarchicalDeletion/nestedScenarios.json")
    def jsonSlurper = new JsonSlurper()

    // Parse JSON into a nested Map
    expectedKbStatsData = jsonSlurper.parse(jsonFile).get(structure)
    Map<String, Map<String, Map<String, List<String>>>> nestedScenarios = jsonSlurper.parse(scenarios)
    featureScenarios = nestedScenarios.get(structure)
  }

  void createAgreementLines(Map<String, Set<String>> idsForAgreementLines) {
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
  }

  Map calculateDoDeleteKbStats(Map expectedMarkForDelete) {
    Map expectedKbStatsForDelete = new HashMap<>();
    expectedKbStatsForDelete.put("PackageContentItem", expectedKbStatsData.get("PackageContentItem") - expectedMarkForDelete.get("pci").size())
    expectedKbStatsForDelete.put("PlatformTitleInstance", expectedKbStatsData.get("PlatformTitleInstance") - expectedMarkForDelete.get("pti").size())
    expectedKbStatsForDelete.put("TitleInstance", expectedKbStatsData.get("TitleInstance") - expectedMarkForDelete.get("ti").size())
    expectedKbStatsForDelete.put("Work", expectedKbStatsData.get("Work") - expectedMarkForDelete.get("work").size())

    return expectedKbStatsForDelete
  }

  void assertKbStatsMatch(Map kbStatsResp) {
    assert expectedKbStatsData.get("PackageContentItem") == kbStatsResp.get("PackageContentItem")
    assert expectedKbStatsData.get("PlatformTitleInstance") == kbStatsResp.get("PlatformTitleInstance")
    assert expectedKbStatsData.get("TitleInstance") == kbStatsResp.get("TitleInstance")
    assert expectedKbStatsData.get("Work") == kbStatsResp.get("Work")
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

      Map<String, Set<String>>  expectedPcis = findInputResourceIds(expectedMarkForDelete.get("pci"), structure)
      Map<String, Set<String>>  expectedPtis = findInputResourceIds(expectedMarkForDelete.get("pti"), structure)
      assert expectedPcis.get("pci") == operationResponse.get("pci") as Set
      assert expectedPtis.get("pti") == operationResponse.get("pti") as Set
    }

    if (operationError) {
      log.info("Exception message: {}", operationError.message)
      operationError.message == "Id list cannot be empty."
    }
  }

  @Ignore
  void "Scenario 1: simple"() {
    setup:
      String structure = "simple"
      setupDataForTest(structure)

    when: "The PCI created during setup is marked for deletion"
      log.info("CURRENT ITERATION: inputs - {} agreements - {}", currentInputResources.toListString(), currentAgreementLines.toListString())
      Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, structure)
      Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, structure)

      visualiseHierarchy(idsForProcessing.get("pci"))
      createAgreementLines(idsForAgreementLines)

      log.info("IDs found for processing: ${idsForProcessing}")
      log.info("IDs found for agreement lines: ${idsForAgreementLines}")

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
      log.info("Expected marked for deletion: {}", featureScenarios.get("inputResource").get(currentInputResources.sort(false).join(",")).get("agreementLine").get(currentAgreementLines.sort(false).join(",")).get("expectedValue"))
    String currentInputResourcesKey = currentInputResources.isEmpty() ? "Empty" : currentInputResources.sort(false).join(",")
    String currentAgreementLinesKey = currentAgreementLines.isEmpty() ? "Empty" : currentAgreementLines.sort(false).join(",")
    Map<String, List<String>> expectedMarkForDelete = featureScenarios.get("inputResource").get(currentInputResourcesKey).get("agreementLine").get(currentAgreementLinesKey).get("expectedValue");
      Map doDeleteExpectedKbStats = new HashMap();
      if (doDelete) {
//        doDeleteExpectedKbStats = calculateDoDeleteKbStats(expectedMarkForDelete)
      }
    then:
      assertIdsMatch(structure, operationResponse, operationError, kbStatsResp, expectedMarkForDelete)
      if (doDelete) {
//        assertKbStatsMatch(doDeleteExpectedKbStats)
      } else {
        assertKbStatsMatch(kbStatsResp)
      }
    where:
      [currentInputResources, currentAgreementLines, doDelete] <<
        simpleCombinations.collectMany { inputResourceCombo ->
          simpleCombinations.collectMany { agreementLineCombo ->
            [true, false].collect { deleteFlag -> // Iterate over the boolean flags
              [inputResourceCombo, agreementLineCombo, deleteFlag]   // Create a new list with the flag appended
            }
          }
        }

  }

  @Ignore
  void "Scenario 2: top-link"() {
    setup:
      String structure = "top-link"
      setupDataForTest(structure)

    when: "The PCI created during setup is marked for deletion"
      log.info("CURRENT ITERATION: inputs - {} agreements - {}", currentInputResources.toListString(), currentAgreementLines.toListString())
      Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, structure)
      Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, structure)

      visualiseHierarchy(idsForProcessing.get("pci"))
      createAgreementLines(idsForAgreementLines)

      log.info("IDs found for processing: ${idsForProcessing}")
      log.info("IDs found for agreement lines: ${idsForAgreementLines}")

    // TODO: Implement "doDelete" in where block.
    //    String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
    Exception operationError;
    Map operationResponse;
    try {
      operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])
    } catch (Exception e) {
      operationError = e;
    }
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    log.info("Operation Response: ${operationResponse}")
    log.info("KB Stats: ${kbStatsResp}")
    log.info("Expected KB Stats: ${expectedKbStatsData}")
    log.info("Expected marked for deletion: {}", featureScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(",")))
    Map<String, List<String>> expectedMarkForDelete = featureScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(","));
    then:
      assertIdsMatch(structure, operationResponse, operationError, kbStatsResp, expectedMarkForDelete)
      assertKbStatsMatch(kbStatsResp)

    where:
    [currentInputResources, currentAgreementLines] <<
      topLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        topLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }
  }

  @Ignore
  void "Scenario 3 ti-link"() {
    setup:
    String structure = "ti-link"
    setupDataForTest(structure)

    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION: inputs - {} agreements - {}", currentInputResources.toListString(), currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, structure)
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, structure)

    visualiseHierarchy(idsForProcessing.get("pci"))
    createAgreementLines(idsForAgreementLines)

    log.info("IDs found for processing: ${idsForProcessing}")
    log.info("IDs found for agreement lines: ${idsForAgreementLines}")

    // TODO: Implement "doDelete" in where block.
    //    String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
    Exception operationError;
    Map operationResponse;
    try {
      operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti']])
    } catch (Exception e) {
      operationError = e;
    }
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    log.info("Operation Response: ${operationResponse}")
    log.info("KB Stats: ${kbStatsResp}")
    log.info("Expected KB Stats: ${expectedKbStatsData}")
    log.info("Expected marked for deletion: {}", featureScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(",")))
    Map<String, List<String>> expectedMarkForDelete = featureScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(","));
    then:
    assertIdsMatch(structure, operationResponse, operationError, kbStatsResp, expectedMarkForDelete)
    assertKbStatsMatch(kbStatsResp)
    where:
    [currentInputResources, currentAgreementLines] <<
      tiLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        tiLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }

  }

  @Ignore
  void "Scenario 4: work-link"() {
    setup:
      String structure = "work-link"
      setupDataForTest(structure)

    when: "The PCI created during setup is marked for deletion"
    log.info("CURRENT ITERATION: inputs - {} agreements - {}", currentInputResources.toListString(), currentAgreementLines.toListString())
    Map<String, Set<String>> idsForProcessing = findInputResourceIds(currentInputResources, structure)
    Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(currentAgreementLines, structure)

    visualiseHierarchy(idsForProcessing.get("pci"))
    createAgreementLines(idsForAgreementLines)

    log.info("IDs found for processing: ${idsForProcessing}")
    log.info("IDs found for agreement lines: ${idsForAgreementLines}")

    // TODO: Implement "doDelete" in where block.
    //    String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
    Exception operationError;
    Map operationResponse;
    try {
      operationResponse = doPost("/erm/hierarchicalDelete/markForDelete", ['pcis': idsForProcessing['pci'], 'ptis': idsForProcessing['pti'], 'tis': idsForProcessing['ti']])
    } catch (Exception e) {
      operationError = e;
    }

    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    log.info("Operation Response: ${operationResponse}")
    log.info("KB Stats: ${kbStatsResp}")
    log.info("Expected KB Stats: ${expectedKbStatsData}")
    log.info("Expected marked for deletion: {}", featureScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(",")))
    Map<String, List<String>> expectedMarkForDelete = featureScenarios.get(currentInputResources.sort(false).join(",")).get(currentAgreementLines.sort(false).join(","));
    then:
    assertKbStatsAndIds(structure, operationResponse, operationError, kbStatsResp, expectedMarkForDelete)

    where:
    [currentInputResources, currentAgreementLines] <<
      workLinkInputResourceCombinations.collectMany { inputResourceCombo ->
        workLinkAgreementLineCombinations.collect { agreementLineCombo ->
          [inputResourceCombo, agreementLineCombo]
        }
      }

  }
}
