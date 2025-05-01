package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.BaseSpec
import org.olf.ErmResourceService
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.PackageContentItem
import org.olf.kb.Work
import org.olf.kb.metadata.PackageIngressMetadata
import spock.lang.Ignore
import spock.lang.Stepwise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import groovy.json.JsonOutput

@Integration
@Stepwise
@Slf4j
class DeletionBaseSpec extends BaseSpec {

  ErmResourceService ermResourceService;

  @Ignore
  Map createAgreement(String name="test_agreement") {
    def today = LocalDate.now()
    def tomorrow = today.plusDays(1)

    def payload = [
        periods: [
            [
                startDate: today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate: tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ]
        ],
        name: name,
        agreementStatus: "active"
    ]

    def response = doPost("/erm/sas/", payload)

    return response as Map
  }

  @Ignore
  Map addEntitlementForAgreement(String agreementName, String resourceId) {
    String agreement_id;
    withTenant {
      String hql = """
            SELECT agreement.id 
            FROM SubscriptionAgreement agreement 
            WHERE agreement.name = :agreementName 
        """
      List results = SubscriptionAgreement.executeQuery(hql, [agreementName: agreementName])
      agreement_id = results.get(0)
    }


    return doPut("/erm/sas/${agreement_id}", {
      items ([
          {
            resource {
              id resourceId
            }
          }
      ])
    }) as Map
  }

  PackageContentItem findPCIByPackageName(String packageName) {
    withTenant {
      String hql = """
            SELECT pci
            FROM PackageContentItem pci
            WHERE pci.pkg.name = :packageName
        """
      List results = PackageContentItem.executeQuery(hql, [packageName: packageName])
      if (results.size() > 1) {
        throw new IllegalStateException("Multiple PCIs found for package name, one expected.")
      }
      return results.get(0);
    }
  }

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
  List loadSingleChainDeletion() {
    importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    return resp
  }

  @Ignore
  void clearResources() {
    withTenant {
      ErmResource.withTransaction {
        PackageIngressMetadata.executeUpdate("DELETE FROM PackageIngressMetadata")


        IdentifierOccurrence.executeUpdate("DELETE FROM IdentifierOccurrence")


        ErmResource.executeUpdate(
            """DELETE FROM Period"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM Entitlement"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM SubscriptionAgreement"""
        )

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
      }
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
