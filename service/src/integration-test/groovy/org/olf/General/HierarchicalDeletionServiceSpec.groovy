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

  @Shared
  String pkg_id2

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

  @Ignore
  void clearResources() {
    ErmResource.withTransaction {
      ErmResource.executeUpdate(
          """DELETE FROM PackageContentItem"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM PlatformTitleInstance"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM TitleInstance"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM Work"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM ErmResource"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM ErmTitleList"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM SubscriptionAgreement"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM Entitlement"""
      )
    }
  }

  void "Load Packages"() {

    when: 'File loaded'
//    Map result = importPackageFromFileViaService('hierarchicalDeletion/deletion_service_pkg.json')
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

  void "Scenario 1: Fully delete one PCI chain with no other references"() {
    when: 'We check what resources are in the system'
      Map kbStatsResp = doGet("/erm/statistics/kbCount")
      Map sasStatsResp = doGet("/erm/statistics/sasCount")
    then:
      kbStatsResp.ErmResource == 0
      kbStatsResp.PackageContentItems == 0
      kbStatsResp.PlatformTitleInstance == 0
      kbStatsResp.TitleInstance == 0
      kbStatsResp.Work == 0

      sasStatsResp.SubscriptionAgreement == 0
      sasStatsResp.Entitlement == 0
  }

}
