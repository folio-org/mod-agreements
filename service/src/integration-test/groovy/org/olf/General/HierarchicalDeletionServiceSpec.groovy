package org.olf.General

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.BaseSpec
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
class HierarchicalDeletionServiceSpec extends BaseSpec{

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
  private Map createPciPtiTiWorkInstance(String suffix = "") {
    Work work = new Work(name: "Test Work ${suffix}").save(flush: true, failOnError: true)
    TitleInstance ti = new TitleInstance(name: "Test TI ${suffix}", work: work).save(flush: true, failOnError: true)
    PlatformTitleInstance pti = new PlatformTitleInstance(name: "Test PTI ${suffix}", titleInstance: ti).save(flush: true, failOnError: true)
    PackageContentItem pci = new PackageContentItem(name: "Test PCI ${suffix}", pti: pti).save(flush: true, failOnError: true)

    return [work: work, ti: ti, pti: pti, pci: pci]
  }


  void "Load Packages"() {

    when: 'File loaded'
    Map result = importPackageFromFileViaService('deletion_service_pkg.json')

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
    log.info(pciIds.toListString());
    PackageContentItem pci = getPCIByName("Test aaa-000")

//    PlatformTitleInstance pti = pci?.pti
//    TitleInstance ti = pti?.ti
//    Work work = ti?.work
//
//    // Ensure no entitlements exist
////    assert Entitlement.countByResource(pci) == 0
////    assert Entitlement.countByResource(pti) == 0
////    assert PackageContentItem.count() == 1
////    assert PlatformTitleInstance.count() == 1
////    assert TitleInstance.count() == 1
////    assert Work.count() == 1
//
//    when: "The service is called with the PCI ID"
//    List<String> pciIdsToTest = [pci.id.toString()]
//    Map<String, List<String>> result = ermResourceService.heirarchicalDeletePCI(pciIdsToTest)
//
//    then: "The result map contains IDs for PCI, PTI, TI, and Work"
//    result != null
//    result.PCIs?.size() == 1
//    result.PCIs?.contains(pci.id.toString())
//
//    result.PTIs?.size() == 1
//    result.PTIs?.contains(pti.id.toString())
//
//    result.TIs?.size() == 1
//    result.TIs?.contains(ti.id.toString())
//
//    result.Works?.size() == 1
//    result.Works?.contains(work.id.toString())
  }

}
