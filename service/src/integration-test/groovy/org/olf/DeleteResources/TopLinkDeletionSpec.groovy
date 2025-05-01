package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.ErmResourceService
import org.olf.kb.*
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


  List<String> pciIds;

  List resp;
  List resp2;

  def setup() {
    if (!pkg_id) {
      return;
    }
    log.info("--- Running Setup for test: ${specificationContext.currentIteration?.name ?: specificationContext.currentFeature?.name} ---")
    importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion.json')
    importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion_link.json')

    resp = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 001']])
    resp2 = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 002']])

    // Fetch PCIs and save IDs to list
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    Map sasStatsResp = doGet("/erm/statistics/sasCount")
    List pciResp = doGet("/erm/pci")

    log.info("KB Counts (in setup): {}", kbStatsResp?.toString()) // Use safe navigation ?. just in case
    log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    pciIds = []
    pciResp?.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id.toString())
      }
    }
    log.info("Found PCI IDs (in setup): {}", pciIds)
    withTenant {
      List<String> workIds = Work.executeQuery("""
        SELECT work.id FROM Work work
      """.toString())

      pciIds.forEach { String id -> log.info(ermResourceService.visualizePciHierarchy(id)) }

      workIds.forEach { String id -> log.info(ermResourceService.visualizeWorkHierarchy(id)) }
    }
    visualiseHierarchy(pciIds)
    if (!pciIds.isEmpty()) {
      visualiseHierarchy(pciIds)
    } else {
      log.warn("No PCI IDs found during setup, visualization skipped.")
    }
    log.info("--- Setup Complete ---")
  }

  void "Load Packages"() {

    when: 'File loaded'
    Map result = importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion.json')
    Map result2 = importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion_link.json')

    then: 'Package imported'
    result.packageImported == true

    when: "Looked up package with name"
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 001']])
    List resp2 = doGet("/erm/packages", [filters: ['name==K-Int Link - Deletion Test Package 002']])
    log.info(resp.toString())
    log.info(resp[0].toString())
    pkg_id = resp[0].id
    pkg_id2 = resp2[0].id

    then: "Package found"
    resp.size() == 1
    resp[0].id != null
  }

  void "Scenario 1: Two PCIs which reference the same PTI both marked for deletion."() {
    given: "Setup has found PCI IDs"
    assert pciIds != null: "pciIds should have been initialized by setup()"
    assert !pciIds.isEmpty(): "Setup() must find at least one PCI for this test"
    when: "The first PCI found during setup is marked for deletion"
    Set<String> pcisToDelete = new HashSet([pciIds.get(0), pciIds.get(1)]) // FIXME Matt -- this might need re-jigging slightly and neatening, currently creating a Set from a list from ids in a specific order
    log.info("Attempting to delete PCI IDs: {}", pcisToDelete)

    Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
      'pcis' pcisToDelete
    })
    log.info("Delete Response: {}", deleteResp.toString())

    // Get PCI for assertions
    PackageContentItem pci;
    withTenant {
      pci = PackageContentItem.executeQuery("""SELECT pci FROM PackageContentItem pci""").get(0);
    }

    log.info(deleteResp.toString())

    then:
    deleteResp
    deleteResp.pci
    deleteResp.pci.size() == 2

    new HashSet(deleteResp.pci).equals(pcisToDelete) // FIXME this may need reflecting elsewhere... Sets not Lists so no ordering

    deleteResp.pti.size() == 1
    deleteResp.pti[0] == pci.pti.id

    deleteResp.ti.size() == 2
    deleteResp.ti.any { ti -> ti == pci.pti.titleInstance.id }

    deleteResp.work.size() == 1
    deleteResp.work[0] == pci.pti.titleInstance.work.id;

    cleanup:
    log.info("--- Manually running cleanup for feature two ---")
    withTenant { // May need explicit tenant ID if setup changed it
      clearResources()
    }
    log.info("--- Manual cleanup complete for feature two ---")
  }


}
