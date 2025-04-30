package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import org.olf.ErmResourceService
import org.olf.kb.ErmResource
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.PackageContentItem
import org.olf.kb.Work
import org.olf.kb.metadata.PackageIngressMetadata
import spock.lang.Ignore
import spock.lang.Stepwise

@Integration
@Stepwise
class DeletionBaseSpec extends BaseSpec {

  ErmResourceService ermResourceService;

//  void "Scenario 1: Fully delete one PCI chain with no other references"() {
//    when: 'We check what resources are in the system'
//    Map kbStatsResp = doGet("/erm/statistics/kbCount")
//    Map sasStatsResp = doGet("/erm/statistics/sasCount")
//    then:
//    kbStatsResp.ErmResource == 0
//    kbStatsResp.PackageContentItems == 0
//    kbStatsResp.PlatformTitleInstance == 0
//    kbStatsResp.TitleInstance == 0
//    kbStatsResp.Work == 0
//
//    sasStatsResp.SubscriptionAgreement == 0
//    sasStatsResp.Entitlement == 0
//  }

  @Ignore
  void clearResources() {
    ErmResource.withTransaction {
      PackageIngressMetadata.executeUpdate("DELETE FROM PackageIngressMetadata")


      IdentifierOccurrence.executeUpdate("DELETE FROM IdentifierOccurrence")

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

      pciIds.forEach { String id -> log.info(ermResourceService.visualizePciHierarchy(id)) }

      workIds.forEach { String id -> log.info(ermResourceService.visualizeWorkHierarchy(id)) }
    }
  }

}
