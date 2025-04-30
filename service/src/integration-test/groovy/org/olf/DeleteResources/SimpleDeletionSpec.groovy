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
    given: "Test aaa-000"
    Map statsResp = doGet("/erm/statistics/kbCount")
    log.info("Stats in test: {}", statsResp.toString())

    List resp = doGet("/erm/pci")
    log.info(resp.toString())
    log.info(resp.get(0).get("longName"));
    log.info(resp.get(1).get("longName"));

    List<String> pciIds = new ArrayList<>();
    resp.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id)
      }
    }

    log.info("PCI ids in test: {}", pciIds.toListString());

    withTenant {
      List<String> workIds = Work.executeQuery("""
        SELECT work.id FROM Work work
      """.toString())
      log.info(ermResourceService.visualizePciHierarchy(pciIds.get(0)))
      workIds.forEach{String id ->  log.info(ermResourceService.visualizeWorkHierarchy(id))}
    }

    Map deleteResp = doPost("/erm/pci/hdelete", {
      'pCIIds' pciIds
    })

    log.info(deleteResp.toString())
  }

}
