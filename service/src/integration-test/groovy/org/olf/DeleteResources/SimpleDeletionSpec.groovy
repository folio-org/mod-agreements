package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.ErmResourceService
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Work
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
@Slf4j
class SimpleDeletionSpec extends DeletionBaseSpec{

  ErmResourceService ermResourceService;

  @Shared
  String pkg_id

  String PCI_HQL = """
    SELECT pci
    FROM PackageContentItem AS pci
    """.toString()

  String PTI_HQL = """
    SELECT pti
    FROM PlatformTitleInstance AS pti
    """.toString()

  String TI_HQL = """
    SELECT ti
    FROM TitleInstance AS ti
    """.toString()

  List<ErmResource> getAllResource(String hqlForResource) {
    List<PackageContentItem> results = []

    withTenant {
      log.debug("Executing query within tenant context: {}", tenantId)
      results = ErmResource.executeQuery(hqlForResource)
      log.debug("Query returned {} results", results?.size() ?: 0)
    }

    return results
  }

  List<ErmResource> getPCIByName(String name) {
    String HQL = """
    SELECT pci
    FROM PackageContentItem AS pci
    WHERE pci.name = '${name}'
    """.toString()

    List<PackageContentItem> results = []

    withTenant {
      results = ErmResource.executeQuery(HQL)
    }

    return results
  }

  @Ignore
  void visualiseHierarchy(List<String> pciIds) {
    log.info("PCI ids in test: {}", pciIds.toListString());

    withTenant {
      List<String> workIds = Work.executeQuery("""
        SELECT work.id FROM Work work
      """.toString())

      pciIds.forEach{String id -> log.info(ermResourceService.visualizePciHierarchy(id))}

      workIds.forEach{String id ->  log.info(ermResourceService.visualizeWorkHierarchy(id))}
    }
  }

  void "Load Packages"() {

    when: 'File loaded'
    Map result = importPackageFromFileViaService('hierarchicalDeletion/deletion_service_pkg.json')

    then: 'Package imported'
    result.packageImported == true

    when: "Looked up package with name"
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    log.info(resp.toString())
    log.info(resp[0].toString())
    pkg_id = resp[0].id

    then: "Package found"
    resp.size() == 1
    resp[0].id != null
    getAllResource(PCI_HQL).forEach{ErmResource pci ->
      {
        log.info(pci.toString())
      }
    }
    getAllResource(PTI_HQL).forEach{ErmResource pti ->
      {
        log.info(pti.toString())
      }
    }
    getAllResource(TI_HQL).forEach{ErmResource ti ->
      {
        log.info(ti.toString())
      }
    }
  }

  void "Scenario 1: Fully delete one PCI chain with no other references"() {
    when: "One 1:1:1 PCI is marked for deletion and no entitlements."
    // Fetch PCIs and save IDs to list
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    Map sasStatsResp = doGet("/erm/statistics/sasCount")
    List resp = doGet("/erm/pci")
    log.info("KB Counts: {}", kbStatsResp.toString())
    log.info("Response from /erm/pci: {}", resp.toString())

    List<String> pciIds = new ArrayList<>();
    resp.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id)
      }
    }

    // Visualise Hierarchy
    visualiseHierarchy(pciIds)

    // Delete PCI
    List<String> pcisToDelete = new ArrayList<>();
    pcisToDelete.add(pciIds.get(0));
    Map deleteResp = doPost("/erm/pci/hdelete", {
      'pCIIds' pcisToDelete
    })

    // Get PCI for assertions
    PackageContentItem pci;
    withTenant {
      pci = PackageContentItem.executeQuery("""SELECT pci FROM PackageContentItem pci""").get(0);
    }

    log.info(deleteResp.toString())

    then:
    deleteResp
    deleteResp.pci
    deleteResp.pci.size() == 1
    deleteResp.pci[0] == pcisToDelete.get(0)

    deleteResp.pti.size() == 1
    deleteResp.pti[0] == pci.pti.id

    deleteResp.ti.size() == 2
    deleteResp.ti.any { ti -> ti == pci.pti.titleInstance.id }

    deleteResp.work.size() == 1
    deleteResp.work[0] == pci.pti.titleInstance.work.id;
  }

}
