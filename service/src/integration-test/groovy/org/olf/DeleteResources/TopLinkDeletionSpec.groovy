package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
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
      assert pciIds != null: "pciIds should have been initialized by setup()"
      assert !pciIds.isEmpty(): "Setup() must find at least one PCI for this test"
    when: "Both PCIs found during setup is marked for deletion"
      PackageContentItem pci1 = findPCIByPackageName(packageName1)
      PackageContentItem pci2 = findPCIByPackageName(packageName2)
      Set<PackageContentItem> pciSet = [pci1, pci2] as Set
      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)

      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)

      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
        'pcis' pcisToDelete
      })
      log.info("Delete Response: {}", deleteResp.toString())
      log.info(resourceIds.toString())
      log.info(deleteResp.toString())

    then: "All resources are deleted."
      verifySetSizes(deleteResp, 2, 1, 2, 1)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))
  }

  void "Scenario: Only one PCI marked for deletion."() {
    given: "Setup has found PCI IDs"
      assert pciIds != null: "pciIds should have been initialized by setup()"
      assert !pciIds.isEmpty(): "Setup() must find at least one PCI for this test"
    when: "Only one PCI found during setup is marked for deletion"
      PackageContentItem pci1 = findPCIByPackageName(packageName1)
      PackageContentItem pci2 = findPCIByPackageName(packageName2)
      Set<PackageContentItem> pciSet = [pci1, pci2] as Set
      Set<String> pcisToDelete = [pci1.id] as Set

      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)

      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
        'pcis' pcisToDelete
      })

      log.info("Delete Response: {}", deleteResp.toString())
      log.info(deleteResp.toString())

    then: "Only one PCI resource is deleted."
      verifySetSizes(deleteResp, 1, 0, 0,0)
      verifyPciIds(deleteResp, pcisToDelete)
  }

  void "Scenario: Both PCIs marked for deletion but one has an agreement line attached."() {
    given: "Setup has found PCI IDs"
      assert pciIds != null: "pciIds should have been initialized by setup()"
      assert !pciIds.isEmpty(): "Setup() must find at least one PCI for this test"
    when: "Only one PCI found during setup is marked for deletion"
      PackageContentItem pci1 = findPCIByPackageName(packageName1) // Attached to agreement line
      PackageContentItem pci2 = findPCIByPackageName(packageName2)
      Set<String> pcisToDelete = [pci1.id, pci2.id] as Set

      String agreement_name = agreementName
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci1.id)

      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)

      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
        'pcis' pcisToDelete
      })

      log.info("Delete Response: {}", deleteResp.toString())
      log.info(deleteResp.toString())

    then: "Only one PCI resource is deleted."
      verifySetSizes(deleteResp, 1, 0, 0,0)
      verifyPciIds(deleteResp, [pci2.id] as Set) // Mark PCI NOT attached to agreement line for deletion.
  }
}
