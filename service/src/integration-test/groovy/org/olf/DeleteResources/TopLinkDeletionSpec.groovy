package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.junit.Assert
import org.olf.ErmResourceService
import org.olf.kb.*
import org.spockframework.runtime.SpecificationContext
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
@Slf4j
class TopLinkDeletionSpec extends DeletionBaseSpec{

  ErmResourceService ermResourceService;

  @Shared
  String pkg_id

  @Shared
  String pkg_id2

  List resp;
  List resp2;
  List<String> pciIds;
  List<String> ptiIds;
  List<String> tiIds;

  String packageName1 = "K-Int Link - Deletion Test Package 001";
  String packageName2 = "K-Int Link - Deletion Test Package 002"
  String agreementName = "test_agreement"

  def setup() {
    SpecificationContext currentSpecInfo = specificationContext;
    if (!specificationContext.currentFeature?.name.contains("Scenario")) {
      // If not in a SimpleDeletionSpec Scenario (i.e. in a tenant purge/ensure test tenant), don't try to load packages yet.
      log.info("--- Skipping Setup for tenant setup tests: ${currentSpecInfo.currentSpec.displayName} (Feature: ${currentSpecInfo.currentFeature?.name}) ---")
      return;
    }
    log.info("--- Running Setup for test: ${specificationContext.currentIteration?.name ?: specificationContext.currentFeature?.name} ---")

    importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion.json')
    importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion_link.json')

    resp = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 001']])
    resp2 = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 002']])
    pkg_id = resp[0].id
    pkg_id2 = resp[0].id

    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    Map sasStatsResp = doGet("/erm/statistics/sasCount")

    // Fetch PCIs and save IDs to list
    List pciResp = doGet("/erm/pci")
    List ptiResp = doGet("/erm/pti")

    pciIds = []
    pciResp?.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id.toString())
      }
    }

    ptiIds = []
    ptiResp?.forEach { Map item ->
      if (item?.id) {
        ptiIds.add(item.id.toString())
      }
    }

    log.info("Found PCI IDs (in setup): {}", pciIds)
    log.info("Found PTI IDs (in setup): {}", ptiIds)
    log.info("KB Counts (in setup): {}", kbStatsResp?.toString())
    log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    if (!pciIds.isEmpty()) {
      visualiseHierarchy(pciIds)
    }
    log.info("--- Setup Complete ---")
  }

  def cleanup() {
    // Used to clear resources from DB between tests.
    // Specification logic is needed to ensure clearResources is not run for BaseSpec tests (which will cause it to fail).
    SpecificationContext currentSpecInfo = specificationContext;
    if (specificationContext.currentFeature?.name.contains("Scenario")) {
      log.info("--- Running Cleanup for test: ${currentSpecInfo.currentIteration?.name ?: currentSpecInfo.currentFeature?.name ?: currentSpecInfo.currentSpec.name} in ${SimpleDeletionSpec.simpleName} ---")
      try {
        clearResources()
      } catch (Exception e) {
        log.error("--- Error during SimpleDeletionSpec cleanup: ${e.message}", e)
      }
      log.info("--- ${SimpleDeletionSpec.simpleName} Cleanup Complete ---")
    } else {
      log.info("--- Skipping SimpleDeletionSpec-specific cleanup for BaseSpec feature run in: ${currentSpecInfo.currentSpec.displayName} (Feature: ${currentSpecInfo.currentFeature?.name}) ---")
    }
  }

  void "Scenario: Two PCIs which reference the same PTI both marked for deletion."() {
    given: "Setup has found PCI IDs"
      PackageContentItem pci1 = findPCIByPackageName(packageName1)
      PackageContentItem pci2 = findPCIByPackageName(packageName2)
      Set<PackageContentItem> pciSet = [pci1, pci2] as Set
      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set
      Map resourceIds = collectResourceIds(getAllResourcesForPCIs(pciSet));
    when: "Both PCIs found during setup is marked for deletion"
      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
        'pcis' pcisToDelete
      })
      log.info("Delete Response: {}", deleteResp.toString())
      log.info(resourceIds.toString())

    then: "All resources are deleted."
      verifySetSizes(deleteResp, 2, 1, 2, 1)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))
  }

  void "Scenario: Two PCIs which reference the same PTI are correctly processed when marked for deletion or deleted"(
    boolean doDelete, String resourceType, int expectationCount
  ) {
    given: "Two specific PCIs sharing a PTI are identified, and their expected related resource IDs are collected"

    PackageContentItem pci1 = findPCIByPackageName(packageName1)
    PackageContentItem pci2 = findPCIByPackageName(packageName2)

    assert pci1 != null : "PCI for package '$packageName1' must exist"
    assert pci2 != null : "PCI for package '$packageName2' must exist"
    assert pci1.pti.id == pci2.pti.id : "PCIs must share the same PTI for this scenario (pti1_id: ${pci1.pti.id}, pti2_id: ${pci2.pti.id})"

    Set<PackageContentItem> pciSet = [pci1, pci2] as Set
    Set<String> pcisToProcess = [pci1.id, pci2.id] as Set

    Map<String, Set<String>> allExpectedResourceIds = collectResourceIds(getAllResourcesForPCIs(pciSet))

    // 'when' and 'then' blocks run FOR EACH 'where' iteration.
    when: "A request is made to either mark for delete or delete these PCIs based on 'doDelete' flag"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      log.info("WHEN: Iteration: doDelete={}, resourceType={}, expectationCount={}. Posting to URL: {}",
        doDelete, resourceType, expectationCount, url)

      Map markForDeletionResponse = doPost(url, [pcis: pcisToProcess])
      log.info("markForDeletionResponse: {}", markForDeletionResponse)

    then: "The resource IDs marked for deletion match the expected resource IDs"
      Set<String> expectedIdsForThisResourceType = allExpectedResourceIds.get(resourceType)

      expectedIdsForThisResourceType.size() == expectationCount

      verifyResourceIds(markForDeletionResponse, resourceType, expectedIdsForThisResourceType)

    where: "The operation (mark/delete), resource type, and expected count are varied"
      doDelete | resourceType | expectationCount
      false    | "pci"        | 2
      false    | "pti"        | 1
      false    | "ti"         | 2
      false    | "work"       | 1
//    true     | "pci"        | 2
//    true     | "pti"        | 1
    // true     | "ti"         | 2
    // true     | "work"       | 1
  }
//
//  void "Scenario: Only one PCI marked for deletion."() {
//    given: "Setup has found PCI IDs"
//      PackageContentItem pci1 = findPCIByPackageName(packageName1)
//      Set<String> pcisToDelete = [pci1.id] as Set
//    when: "Only one PCI found during setup is marked for deletion"
//      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
//      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
//        'pcis' pcisToDelete
//      })
//      log.info("Delete Response: {}", deleteResp.toString())
//
//    then: "Only one PCI resource is deleted."
//      verifySetSizes(deleteResp, 1, 0, 0,0)
//      verifyPciIds(deleteResp, pcisToDelete)
//  }
//
//  void "Scenario: Both PCIs marked for deletion and one has an agreement line attached."() {
//    given: "Setup has found PCI IDs"
//      PackageContentItem pci1 = findPCIByPackageName(packageName1) // Attached to agreement line
//      PackageContentItem pci2 = findPCIByPackageName(packageName2)
//      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set
//
//      String agreement_name = agreementName
//      Map agreementResp = createAgreement(agreement_name)
//      addEntitlementForAgreement(agreement_name, pci1.id)
//    when: "Both PCIs found during setup is marked for deletion"
//      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
//      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
//        'pcis' pcisToDelete
//      })
//      log.info("Delete Response: {}", deleteResp.toString())
//
//    then: "Only one PCI resource is deleted."
//      verifySetSizes(deleteResp, 1, 0, 0,0)
//      verifyPciIds(deleteResp, [pci2.id] as Set) // Mark PCI NOT attached to agreement line for deletion.
//  }
//
//  void "Scenario: Both PCIs marked for deletion and both have an agreement line attached."() {
//    given: "Setup has found PCI IDs"
//      PackageContentItem pci1 = findPCIByPackageName(packageName1) // Attached to agreement line
//      PackageContentItem pci2 = findPCIByPackageName(packageName2) // Attached to agreement line
//      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set
//
//      String agreement_name = agreementName
//      Map agreementResp = createAgreement(agreement_name)
//      addEntitlementForAgreement(agreement_name, pci1.id)
//      addEntitlementForAgreement(agreement_name, pci2.id)
//
//    when: "Both PCIs found during setup is marked for deletion"
//      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
//      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
//        'pcis' pcisToDelete
//      })
//      log.info("Delete Response: {}", deleteResp.toString())
//
//    then: "No resources are deleted."
//      verifySetSizes(deleteResp, 0, 0, 0,0)
//  }
//
//  void "Scenario: Only one PCI and the PTI marked for deletion."() {
//    given: "Setup has found PCI IDs"
//      PackageContentItem pci1 = findPCIByPackageName(packageName1)
//      Set<String> pcisToDelete = [pci1.id] as Set
//      Set<String> ptisToDelete = [pci1.pti.id] as Set
//    when: "Only one PCI found during setup is marked for deletion"
//      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
//      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
//        'pcis' pcisToDelete
//        'ptis' ptisToDelete
//      })
//      log.info("Delete Response: {}", deleteResp.toString())
//
//    then: "Only one PCI resource is deleted."
//      verifySetSizes(deleteResp, 1, 0, 0,0)
//      verifyPciIds(deleteResp, pcisToDelete)
//  }
//
//  void "Scenario: Both PCIs and the PTI marked for deletion."() {
//    given: "Setup has found PCI IDs"
//      PackageContentItem pci1 = findPCIByPackageName(packageName1)
//      PackageContentItem pci2 = findPCIByPackageName(packageName2)
//      Set<PackageContentItem> pciSet = [pci1, pci2] as Set
//      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set
//      Set<String> ptisToDelete = [pci1.pti.id] as Set
//      Map resourceIds = collectResourceIds(getAllResourcesForPCIs(pciSet));
//    when: "Only one PCI found during setup is marked for deletion"
//      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
//      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
//        'pcis' pcisToDelete
//        'ptis' ptisToDelete
//      })
//      log.info("Delete Response: {}", deleteResp.toString())
//
//    then: "All resources are deleted."
//      verifySetSizes(deleteResp, 2, 1, 2,1)
//      verifyPciIds(deleteResp, resourceIds.get("pci"))
//      verifyPtiIds(deleteResp, resourceIds.get("pti"))
//      verifyTiIds(deleteResp, resourceIds.get("ti"))
//      verifyWorkIds(deleteResp, resourceIds.get("work"))
//  }
//
//  void "Scenario: Combined with Single Chain package import, only top-link chain deleted."() {
//    given: "Setup has found PCI IDs"
//      Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
//      doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
//      PackageContentItem pci1 = findPCIByPackageName(packageName1)
//      PackageContentItem pci2 = findPCIByPackageName(packageName2)
//      PackageContentItem pci_simple = findPCIByPackageName("K-Int Deletion Test Package 001")
//
//      Set<PackageContentItem> pciSet = [pci1, pci2] as Set
//      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set
//      Map resourceIds = collectResourceIds(getAllResourcesForPCIs(pciSet));
//      when: "Only one PCI found during setup is marked for deletion"
//      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
//      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
//        'pcis' pcisToDelete
//      })
//      log.info("Delete Response: {}", deleteResp.toString())
//      Map kbStatsResp = doGet("/erm/statistics/kbCount")
//      log.info(kbStatsResp.toMapString())
//
//    then: "Top-Link resources are deleted, but simple resources remain."
//      kbStatsResp.get("Work") == 2
//      kbStatsResp.get("PackageContentItem") == 3
//      kbStatsResp.get("PlatformTitleInstance") == 2
//      kbStatsResp.get("TitleInstance") == 4
//      verifySetSizes(deleteResp, 2, 1, 2,1)
//      verifyPciIds(deleteResp, resourceIds.get("pci"))
//      verifyPtiIds(deleteResp, resourceIds.get("pti"))
//      verifyTiIds(deleteResp, resourceIds.get("ti"))
//      verifyWorkIds(deleteResp, resourceIds.get("work"))
//  }
//
//  void "Scenario: Combined with Single Chain package import, all PCIs marked for deletion."() {
//    given: "Setup has found PCI IDs"
//    Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
//    doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
//    PackageContentItem pci1 = findPCIByPackageName(packageName1)
//    PackageContentItem pci2 = findPCIByPackageName(packageName2)
//    PackageContentItem pci_simple = findPCIByPackageName("K-Int Deletion Test Package 001")
//
//    Set<PackageContentItem> pciSet = [pci1, pci2, pci_simple] as Set
//    Set<String> pcisToDelete = [pci1.id, pci2.id, pci_simple.id] as Set
//    Map resourceIds = collectResourceIds(getAllResourcesForPCIs(pciSet));
//    when: "Only one PCI found during setup is marked for deletion"
//    log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
//    Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
//      'pcis' pcisToDelete
//    })
//    log.info("Delete Response: {}", deleteResp.toString())
//    Map kbStatsResp = doGet("/erm/statistics/kbCount")
//    log.info(kbStatsResp.toMapString())
//
//    then: "Top-Link resources are deleted, but simple resources remain."
//    kbStatsResp.get("Work") == 2
//    kbStatsResp.get("PackageContentItem") == 3
//    kbStatsResp.get("PlatformTitleInstance") == 2
//    kbStatsResp.get("TitleInstance") == 4
//    verifySetSizes(deleteResp, 3, 2, 4,2)
//    verifyPciIds(deleteResp, resourceIds.get("pci"))
//    verifyPtiIds(deleteResp, resourceIds.get("pti"))
//    verifyTiIds(deleteResp, resourceIds.get("ti"))
//    verifyWorkIds(deleteResp, resourceIds.get("work"))
//  }
}
