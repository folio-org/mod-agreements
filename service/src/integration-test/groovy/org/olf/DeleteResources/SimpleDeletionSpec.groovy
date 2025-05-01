package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.ErmResourceService
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import groovyx.net.http.HttpException
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
@Slf4j
class SimpleDeletionSpec extends DeletionBaseSpec {

  @Shared
  String pkg_id

  List<String> pciIds;

  List resp;

  def setup() {
    if (!pkg_id) {
      return;
    }
    log.info("--- Running Setup for test: ${specificationContext.currentIteration?.name ?: specificationContext.currentFeature?.name} ---")

    // Load Single Chain PCI
    importPackageFromFileViaService('hierarchicalDeletion/simple_deletion.json')
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    log.info(resp.toListString())
    pkg_id = resp[0].id

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

    if (!pciIds.isEmpty()) {
      visualiseHierarchy(pciIds)
    } else {
      log.warn("No PCI IDs found during setup, visualization skipped.")
    }
    log.info("--- Setup Complete ---")
  }

//  def cleanup() {
//    if (specificationContext.currentFeature.specification.name == SimpleDeletionSpec.name) {
//      log.info("--- Running Cleanup specifically for test: ${specificationContext.currentIteration?.name ?: specificationContext.currentFeature?.name} in ${SimpleDeletionSpec.name} ---")
//      withTenant { clearResources() } // Assuming withTenant needs a specific context
//      log.info("--- ${SimpleDeletionSpec.name} Cleanup Complete ---")
//    } else {
//      log.info("--- Skipping SimpleDeletionSpec cleanup for BaseSpec feature: ${specificationContext.currentFeature?.name} ---")
//    }
//  }


  void "Load Packages"() {

    when: 'File loaded'
    Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion.json')

    then: 'Package imported'
    result.packageImported == true

    when: "Looked up package with name"
    resp = doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    log.info(resp.toString())
    log.info(resp[0].toString())
    pkg_id = resp[0].id

    then: "Package found"
    resp.size() == 1
    resp[0].id != null
  }


  void "Scenario 1: Fully delete one PCI chain with no other references"() {
    given: "Setup has found PCI IDs"
      assert pciIds != null: "pciIds should have been initialized by setup()"
      assert !pciIds.isEmpty(): "Setup() must find at least one PCI for this test"
    when: "The first PCI found during setup is marked for deletion"
      List<String> pcisToDelete = [pciIds.get(0)]
      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)

      Map deleteResp = doPost("/erm/pci/hdelete", {
        'pCIIds' pcisToDelete
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
      deleteResp.pci.size() == 1
      deleteResp.pci[0] == pcisToDelete.get(0)

      deleteResp.pti.size() == 1
      deleteResp.pti[0] == pci.pti.id

      deleteResp.ti.size() == 2
      deleteResp.ti.any { ti -> ti == pci.pti.titleInstance.id }

      deleteResp.work.size() == 1
      deleteResp.work[0] == pci.pti.titleInstance.work.id;

    cleanup:
      log.info("--- Manually running cleanup for feature two ---")
      clearResources()
      log.info("--- Manual cleanup complete for feature two ---")
  }

  void "Scenario 2: Nothing marked for deletion with one PCI chain."() {
    given: "An empty list of PCI IDs is prepared"
      List<String> pcisToDelete = new ArrayList<>()
      def requestBody = [pCIIds: pcisToDelete]

    when: "A hierarchical delete request is made with the empty list"
      doPost("/erm/pci/hdelete", requestBody)

    then: "An HttpException indicating an Unprocessable Entity (422) error is thrown"
      def e = thrown(HttpException)
      log.info(e.toString())
      assert e.message == "Unprocessable Entity"

//    cleanup:
//    log.info("--- Manually running cleanup for feature two ---")
//    withTenant { // May need explicit tenant ID if setup changed it
//      clearResources()
//    }
//    log.info("--- Manual cleanup complete for feature two ---")
  }

  void "Scenario 3: Single PCI marked for deletion when PTI is attached to agreement line."() {
    given: "A PCI that references a PTI attached to an agreement line is marked for deletion."
      List<String> pcisToDelete = [pciIds.get(0)]
      PackageContentItem pci;
      withTenant {
        pci = PackageContentItem.executeQuery("""SELECT pci FROM PackageContentItem pci""").get(0);
      }

      // FIXME: add helper for creating agreement and move to baseSpec
      Map agreementResp = doPost("/erm/sas/", {
        'periods' ([{
                      'startDate' today.toString()
                      'endDate' tomorrow.toString()
                    }])
        'name' "matts_agreement"
        'agreementStatus' "active"
      })

      log.info("Agreement response", agreementResp.toMapString())
      log.info("Agreement response", agreementResp)

      String agreement_id;
      withTenant {
        agreement_id = SubscriptionAgreement.executeQuery("""SELECT agreement.id FROM SubscriptionAgreement agreement""").get(0);
      }


      doPut("/erm/sas/${agreement_id}", {
          items ([
            {
              resource {
                id pci.pti.id
              }
            }
        ])
      })

      def requestBody = [pCIIds: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/pci/hdelete", requestBody)
      Map sasStatsResp = doGet("/erm/statistics/sasCount")
      log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    then: "Only the PCI is marked for deletion."
      // Should this be tested for the Stats controller separately?
      sasStatsResp
      sasStatsResp.get("SubscriptionAgreement") == 1
      sasStatsResp.get("Entitlement") == 1

      deleteResp
      deleteResp.pci
      deleteResp.pci.size() == 1
      deleteResp.pci[0] == pcisToDelete.get(0)

      deleteResp.pti.size() == 0
      deleteResp.ti.size() == 0
      deleteResp.work.size() == 0

  }
}
