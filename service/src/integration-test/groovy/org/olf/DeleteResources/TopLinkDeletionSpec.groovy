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
  @Shared
  String packageName1 = "K-Int Link - Deletion Test Package 001";

  @Shared
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

    List resp = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 001']])
    List resp2 = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 002']])

    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    Map sasStatsResp = doGet("/erm/statistics/sasCount")

    // Fetch PCIs and save IDs to list
    Set<String> pciIds = collectIDs(getPCIs())
    Set<String> ptiIds = collectIDs(getPTIs())

    log.info("Found PCI IDs (in setup): {}", pciIds)
    log.info("Found PTI IDs (in setup): {}", ptiIds)
    log.info("KB Counts (in setup): {}", kbStatsResp?.toString())
    log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    visualiseHierarchy(pciIds)

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

  void "[Scenario] Structure: Top-Link, Marked: [PCI1, PCI2], Agreement Lines: []"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
    PackageContentItem pci1 = findPCIByPackageName(packageName1)
    PackageContentItem pci2 = findPCIByPackageName(packageName2)
    Set<String> pcisToDelete = [pci1.id, pci2.id] as Set

    when: "Both PCIs found during setup is marked for deletion"
    log.info("Attempting to delete PCI IDs: {}", pcisToDelete)
    String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
    Map operationResponse = doPost(url, ['pcis': pcisToDelete])
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    log.info("Operation Response: ${operationResponse}")
    log.info("KB Stats: ${kbStatsResp}")

    then: "All resources are deleted."
    if (doDelete) {
      verifyKbStats(kbStatsResp, 0,0,0,0)
    } else {
      verifySetSizes(operationResponse, 2, 1, 2, 1)
      verifyIds(operationResponse, collectIDs(getPCIs()), collectIDs(getPTIs()), collectIDs(getTIs()), getWorkIds())
      verifyKbStats(kbStatsResp, 2, 1, 2, 1)
    }

    where:
    actionDescription   | doDelete
    "Marked for Deletion" | false
//    "Deleted in database"  | true
  }

  void "[Scenario] Structure: Top-Link, Marked: [PCI1], Agreement Lines: []"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
      PackageContentItem pci1 = findPCIByPackageName(packageName1)
      Set<String> pcisToDelete = [pci1.id] as Set
    when: "Only one PCI found during setup is marked for deletion"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      Map operationResponse = doPost(url, ['pcis': pcisToDelete])
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")

    then: "Only one PCI resource is deleted."
      if (doDelete) {
        verifyKbStats(kbStatsResp, 1,1,2,1)
      } else {
        verifySetSizes(operationResponse, 1, 0, 0, 0)
        verifyIds(operationResponse, pcisToDelete)
        verifyKbStats(kbStatsResp, 2, 1, 2, 1)
      }

    where:
    actionDescription   | doDelete
    "Marked for Deletion" | false
//    "Deleted in database"  | true
  }

  void "[Scenario] Structure: Top-Link, Marked: [PCI1, PCI2], Agreement Lines: [PCI1]"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
      PackageContentItem pci1 = findPCIByPackageName(packageName1) // Attached to agreement line
      PackageContentItem pci2 = findPCIByPackageName(packageName2)
      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set

      String agreement_name = agreementName
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci1.id)
    when: "Both PCIs found during setup is marked for deletion"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      Map operationResponse = doPost(url, ['pcis': pcisToDelete])
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")

    then: "Only one PCI resource is deleted."
      if (doDelete) {
        verifyKbStats(kbStatsResp, 1,1,2,1)
      } else {
        verifySetSizes(operationResponse, 1, 0, 0, 0)
        verifyIds(operationResponse, [pci2.id] as Set)
        verifyKbStats(kbStatsResp, 2, 1, 2, 1)
      }

    where:
    actionDescription   | doDelete
    "Marked for Deletion" | false
//    "Deleted in database"  | true
  }

  void "[Scenario] Structure: Top-Link, Marked: [PCI1, PCI2], Agreement Lines: [PCI1, PCI2]"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
      PackageContentItem pci1 = findPCIByPackageName(packageName1) // Attached to agreement line
      PackageContentItem pci2 = findPCIByPackageName(packageName2) // Attached to agreement line
      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set

      String agreement_name = agreementName
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci1.id)
      addEntitlementForAgreement(agreement_name, pci2.id)

    when: "Both PCIs found during setup is marked for deletion"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      Map operationResponse = doPost(url, ['pcis': pcisToDelete])
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")
    then: "No resources are deleted."
      if (doDelete) {
        verifyKbStats(kbStatsResp, 2,1,2,1)
      } else {
        verifySetSizes(operationResponse, 0, 0, 0,0)
        verifyIds(operationResponse) // Empty
        verifyKbStats(kbStatsResp, 2, 1, 2, 1)
      }

    where:
      actionDescription   | doDelete
      "Marked for Deletion" | false
  //    "Deleted in database"  | true
  }

  void "[Scenario]  Structure: Top-Link, Marked: [PCI1, PTI1], Agreement Lines: []"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
      PackageContentItem pci1 = findPCIByPackageName(packageName1)
      Set<String> pcisToDelete = [pci1.id] as Set
      Set<String> ptisToDelete = [pci1.pti.id] as Set
    when: "Only one PCI found during setup is marked for deletion"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      Map operationResponse = doPost(url, {
        'pcis' pcisToDelete
        'ptis' ptisToDelete
      })
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")
    then: "Only one PCI resource is deleted."
      if (doDelete) {
        verifyKbStats(kbStatsResp, 1,1,2,1)
      } else {
        verifySetSizes(operationResponse, 1, 0, 0,0)
        verifyIds(operationResponse, pcisToDelete)
        verifyKbStats(kbStatsResp, 2, 1, 2, 1)
      }

    where:
    actionDescription   | doDelete
    "Marked for Deletion" | false
    //    "Deleted in database"  | true
  }

  void "[Scenario]  Structure: Top-Link, Marked: [PCI1, PCI2, PTI1/2], Agreement Lines: []"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
      PackageContentItem pci1 = findPCIByPackageName(packageName1)
      PackageContentItem pci2 = findPCIByPackageName(packageName2)
      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set
      Set<String> ptisToDelete = [pci1.pti.id] as Set
    when: "Only one PCI found during setup is marked for deletion"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      Map operationResponse = doPost(url, {
        'pcis' pcisToDelete
        'ptis' ptisToDelete
      })
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")

    then: "All resources are deleted."
      if (doDelete) {
        verifyKbStats(kbStatsResp, 0,0,0,0)
      } else {
        verifySetSizes(operationResponse, 2, 1, 2,1)
        verifyIds(operationResponse, collectIDs(getPCIs()), collectIDs(getPTIs()), collectIDs(getTIs()), getWorkIds())
        verifyKbStats(kbStatsResp, 2, 1, 2,1)
      }
    where:
      actionDescription   | doDelete
      "Marked for Deletion" | false
      //    "Deleted in database"  | true
  }

  void "[Scenario]  Structure: Top-Link + Simple, Marked: [PCI1, PCI2], Agreement Lines: []"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
      Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
      doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
      PackageContentItem pci1 = findPCIByPackageName(packageName1)
      PackageContentItem pci2 = findPCIByPackageName(packageName2)
      PackageContentItem pci_simple = findPCIByPackageName("K-Int Deletion Test Package 001")

      Set<PackageContentItem> pciSet = [pci1, pci2] as Set
      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set
      Map resourceIds = collectResourceIds(getAllResourcesForPCIs(pciSet)); // APIs are too broad (only want to fetch top-link resources) so use HQL.
    when: "Only one PCI found during setup is marked for deletion"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      Map operationResponse = doPost(url, ['pcis': pcisToDelete])
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")

    then: "Top-Link resources are deleted, but simple resources remain."
      if (doDelete) {
        verifyKbStats(kbStatsResp, 1,1,2,1)
      } else {
        verifyKbStats(kbStatsResp, 3, 2, 4, 2)
        verifySetSizes(operationResponse, 2, 1, 2, 1)
        verifyIds(operationResponse, resourceIds.get("pci"), resourceIds.get("pti"), resourceIds.get("ti"), resourceIds.get("work"))
      }
    where:
      actionDescription   | doDelete
      "Marked for Deletion" | false
      //    "Deleted in database"  | true
  }

  void "[Scenario]  Structure: Top-Link + Simple, Marked: [PCI_TopLink_1, PCI_TopLink_2, PCI_Simple_3], Agreement Lines: []"(String actionDescription, boolean doDelete) {
    given: "Setup has found PCI IDs"
    Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
    doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    PackageContentItem pci1 = findPCIByPackageName(packageName1)
    PackageContentItem pci2 = findPCIByPackageName(packageName2)
    PackageContentItem pci_simple = findPCIByPackageName("K-Int Deletion Test Package 001")
    Set<String> pcisToDelete = [pci1.id, pci2.id, pci_simple.id] as Set

    when: "Only one PCI found during setup is marked for deletion"
      String url = doDelete ? "/erm/hierarchicalDelete/delete" : "/erm/hierarchicalDelete/markForDelete"
      Map operationResponse = doPost(url, ['pcis': pcisToDelete])
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      log.info("Operation Response: ${operationResponse}")
      log.info("KB Stats: ${kbStatsResp}")

    then: "All resources are deleted"
    if (doDelete) {
      verifyKbStats(kbStatsResp, 0,0,0,0)
    } else {
      verifyKbStats(kbStatsResp, 3, 2, 4, 2)
      verifySetSizes(operationResponse, 3, 2, 4, 2)
      verifyIds(operationResponse, collectIDs(getPCIs()), collectIDs(getPTIs()), collectIDs(getTIs()), getWorkIds())
    }
    where:
      actionDescription   | doDelete
      "Marked for Deletion" | false
      //    "Deleted in database"  | true
  }
}
