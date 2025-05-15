package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.BaseSpec
import org.olf.ErmResourceService
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
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

  SubscriptionAgreement findAgreementByName(String agreementName) {
    withTenant {
      String hql = """
            SELECT agreement
            FROM SubscriptionAgreement agreement 
            WHERE agreement.name = :agreementName 
        """
      List results = SubscriptionAgreement.executeQuery(hql, [agreementName: agreementName])
      return results.get(0)
    } as SubscriptionAgreement
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
        throw new IllegalStateException("Multiple PCIs found for package name ${packageName}, one expected.")
      }
      return results.get(0);
    }
  }

  PackageContentItem findPCIById(String id) {
    withTenant {
      String hql = """
            SELECT pci
            FROM PackageContentItem pci
            WHERE pci.id = :id
        """
      List results = PackageContentItem.executeQuery(hql, [id: id])
      if (results.size() > 1) {
        throw new IllegalStateException("Multiple PCIs found for package name, one expected.")
      }
      return results.get(0);
    }
  }

  List<TitleInstance> findTisByWorkId(Set<String> workIds) {
    withTenant {
      String hql = """
            SELECT ti
            FROM TitleInstance ti
            WHERE ti.work.id IN :workIds
        """
      List results = PackageContentItem.executeQuery(hql, [workIds: workIds])
      return results
    }
  }

    List<PlatformTitleInstance> findPTIsByTiIds(List<String> tiIds) {
      withTenant {
        String hql = """
            SELECT pti
            FROM PlatformTitleInstance pti
            WHERE pti.titleInstance.id IN :tiIds
        """
        List results = PackageContentItem.executeQuery(hql, [tiIds: tiIds])
        return results
    }
  }

  List<PlatformTitleInstance> findPCIsByPtiIds(List<String> ptiIds) {
    withTenant {
      String hql = """
            SELECT pci
            FROM PackageContentItem pci
            WHERE pci.pti.id IN :ptiIds
        """
      List results = PackageContentItem.executeQuery(hql, [ptiIds: ptiIds])
      return results
    }
  }

  Map getPCIs() {
    return doGet("/erm/pci?stats=true&perPage=25") as Map
  }

  Map getPTIs() {
    return doGet("/erm/pti?stats=true&perPage=25") as Map
  }

  Map getTIs() {
    return doGet("/erm/titles?stats=true&perPage=25") as Map
  }

  Set<String> getWorkIds() {
    Map res = doGet("/erm/titles?stats=true&perPage=25") as Map
    return res.get("results").collect({Map titleInstance -> titleInstance.get("work").get("id")}) as Set
  }

  Set<String> collectIDs(Map resourceGetResponse) {
    return resourceGetResponse.get("results").collect({Map resource -> resource.id}) as Set
  }

  void verifyIds(Map deleteResp,
                 Set<String> expectedPciIds = new HashSet<String>(),
                 Set<String> expectedPtiIds = new HashSet<String>(),
                 Set<String> expectedTiIds = new HashSet<String>(),
                 Set<String> expectedWorkIds = new HashSet<String>()) {
    assert deleteResp?.pci as Set == expectedPciIds as Set
    assert deleteResp?.pti as Set == expectedPtiIds as Set
    assert deleteResp?.ti as Set == expectedTiIds as Set
    assert deleteResp?.work as Set == expectedWorkIds as Set
  }

  void verifyKbStats(Map kbStatsResp,
                     int expectedPciSize,
                     int expectedPtiSize,
                     int expectedTiSize,
                     int expectedWorkSize) {
    assert kbStatsResp.get("PackageContentItem") == expectedPciSize
    assert kbStatsResp.get("PlatformTitleInstance") == expectedPtiSize
    assert kbStatsResp.get("TitleInstance") == expectedTiSize
    assert kbStatsResp.get("Work") == expectedWorkSize
  }

  @Ignore
  Map getAllResourcesForPCIs(Set<PackageContentItem> pciset) {
   /* Given a set of PCIs, traverse down to the work level and back up,
   finding and returning a map of lists ({pci: [], pti: [], ti: [], work: []}) of all resources related to the
   original set of PCIs.
    */
  Set<String> workIds = pciset.collect(pci -> pci.pti.titleInstance.work.id)
  List<String> works = pciset.collect(pci -> pci.pti.titleInstance.work)



  List<TitleInstance> tis = findTisByWorkId(workIds);
  List<PlatformTitleInstance> ptis = findPTIsByTiIds(tis.collect { titleInstance -> titleInstance.id });
  List<PackageContentItem> pcis = findPCIsByPtiIds(ptis.collect {platformTitleInstance -> platformTitleInstance.id });


  Map resources = new HashMap();
  resources.put("pci", pcis)
  resources.put("pti", ptis);
  resources.put("ti", tis);
  resources.put("work", works);

  return resources
  }

  @Ignore
  Map<String, Set<String>> collectResourceIds(Map<String, List<? extends ErmResource>> resourcesMap) {
    /* Given a map of lists of resources (returned from getAllResourcesForPCIs())
    return the ids of the resources in the Map instead of the ErmResource objects.
     */
    return resourcesMap.collectEntries { String resourceType, List<? extends ErmResource> resourceList ->
      Set<String> ids = resourceList*.id as Set
      [resourceType, ids]
    }
  }

  @Ignore
  void verifySetSizes(deleteResp,
                              int expectedPciSize,
                              int expectedPtiSize,
                              int expectedTiSize,
                              int expectedWorkSize) {
    assert deleteResp?.pci?.size() == expectedPciSize;
    assert deleteResp?.pti?.size() == expectedPtiSize;
    assert deleteResp?.ti?.size() == expectedTiSize;
    assert deleteResp?.work?.size() == expectedWorkSize;
  }

  @Ignore
  void verifyResourceIds(Map deleteResp, String resourceType, Set<String> expectedResourceIds) {
    assert deleteResp != null: "deleteResp cannot be null"
    assert resourceType != null && !resourceType.isEmpty(): "resourceType cannot be null or empty"
    assert expectedResourceIds != null: "expectedResourceIds cannot be null"

    switch (resourceType.toLowerCase()) {
      case "pci":
        assert deleteResp?.pci as Set == deleteResp["pci"] as Set
        break
      case "pti":
        assert deleteResp?.pti as Set == deleteResp["pti"] as Set
        break
      case "ti":
        assert deleteResp?.ti as Set == deleteResp["ti"] as Set
        break
      case "work":
        assert deleteResp?.work as Set == deleteResp["work"] as Set
        break
      default:
        assert false: "Unknown resource type provided to verifyResourceIds: '${resourceType}'"
        return
    }
  }

  @Ignore
  void verifyPciIds(deleteResp, Set<String> resourceIdsToDelete) {
    assert deleteResp?.pci as Set == resourceIdsToDelete as Set
  }

  @Ignore
  void verifyPtiIds(deleteResp, Set<String> resourceIdsToDelete) {
    assert deleteResp?.pti as Set == resourceIdsToDelete as Set
  }

  @Ignore
  void verifyTiIds(deleteResp, Set<String> resourceIdsToDelete) {
    assert deleteResp?.ti as Set == resourceIdsToDelete as Set
  }

  @Ignore
  void verifyWorkIds(deleteResp, Set<String> resourceIdsToDelete) {
    assert deleteResp?.work as Set == resourceIdsToDelete as Set
  }

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

  @Ignore
  void clearAgreements() {
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

      }
    }
  }

  @Ignore
  void visualiseHierarchy(Set<String> pciIds) {
    log.info("PCI ids in test: {}", pciIds.toListString());
    if (pciIds.isEmpty()) return;

    withTenant {
      List<String> workIds = Work.executeQuery("""
        SELECT work.id FROM Work work
      """.toString())

      pciIds.forEach { String id -> log.info(ermResourceService.visualizePciHierarchy(id)) }

      workIds.forEach { String id -> log.info(ermResourceService.visualizeWorkHierarchy(id)) }
    }
  }

}
