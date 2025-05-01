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
    importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
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
      Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')

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
      log.info("--- Running Cleanup ---")
      clearResources()
      log.info("--- Running Cleanup ---")
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

    cleanup:
      log.info("--- Running Cleanup ---")
      clearResources()
      log.info("--- Running Cleanup ---")
  }

  void "Scenario 3: Single PCI marked for deletion when PTI is attached to agreement line."() {
    given: "A PCI that references a PTI attached to an agreement line is marked for deletion."
      List<String> pcisToDelete = [pciIds.get(0)]
      PackageContentItem pci;
      withTenant {
        pci = PackageContentItem.executeQuery("""SELECT pci FROM PackageContentItem pci""").get(0);
      }

      String agreement_name = "matts_agreement"
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci.pti.id)

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
    cleanup:
      log.info("--- Running Cleanup ---")
      clearResources()
      log.info("--- Running Cleanup ---")

  }

  void "Scenario 4: Single PCI marked for deletion when PCI is attached to agreement line."() {
    given: "A PCI that is attached to an agreement line is marked for deletion."
      List<String> pcisToDelete = [pciIds.get(0)]
      PackageContentItem pci;
      withTenant {
        pci = PackageContentItem.executeQuery("""SELECT pci FROM PackageContentItem pci""").get(0);
      }

      String agreement_name = "matts_agreement"
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci.id)

      def requestBody = [pCIIds: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/pci/hdelete", requestBody)
      Map sasStatsResp = doGet("/erm/statistics/sasCount")
      log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    then: "Nothing is marked for deletion."
      // Should this be tested for the Stats controller separately?
      sasStatsResp
      sasStatsResp.get("SubscriptionAgreement") == 1
      sasStatsResp.get("Entitlement") == 1

      deleteResp
      deleteResp.pci.size() == 0
      deleteResp.pti.size() == 0
      deleteResp.ti.size() == 0
      deleteResp.work.size() == 0

    cleanup:
      log.info("--- Running Cleanup ---")
      clearResources()
      log.info("--- Running Cleanup ---")
  }

  void "Scenario 5: Two single-chain PCIs marked for deletion."() {
    given: "Two single-chain PCIs are marked for deletion."
      Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_2.json')
      doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 002']])
      List pciResp = doGet("/erm/pci")
      pciIds = []
      pciResp?.forEach { Map item ->
        if (item?.id) {
          pciIds.add(item.id.toString())
        }
      }

      visualiseHierarchy(pciIds)

      List<String> pcisToDelete = [pciIds.get(0), pciIds.get(1)]
      PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
      PackageContentItem pci2 = findPCIByPackageName("K-Int Deletion Test Package 002")

      log.info(pci1.toString())
      log.info(pci2.toString())

      def requestBody = [pCIIds: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/pci/hdelete", requestBody)

    then: "All resources are marked for deletion"
      deleteResp
      deleteResp.pci
      deleteResp.pci.size() == 2
      (pci1.id in deleteResp.pci) && (pci2.id in deleteResp.pci)

      deleteResp.pti.size() == 2
      (pci1.pti.id in deleteResp.pti) && (pci2.pti.id in deleteResp.pti)

      deleteResp.ti.size() == 4
      // FIXME: need to extend this to find all TI ids
      (pci1.pti.titleInstance.id in deleteResp.ti) && (pci2.pti.titleInstance.id in deleteResp.ti)

      deleteResp.work.size() == 2
      (pci1.pti.titleInstance.work.id in deleteResp.work) && (pci2.pti.titleInstance.work.id in deleteResp.work)

    cleanup:
      log.info("--- Running Cleanup ---")
      clearResources()
      log.info("--- Running Cleanup ---")
  }

  void "Scenario 6: Two single-chain PCIs but only one is marked for deletion."() {
    given: "Two single-chain PCIs exist but only one is marked for deletion."
    Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_2.json')
    doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 002']])
    List pciResp = doGet("/erm/pci")
    pciIds = []
    List<String> pcisToDelete = new ArrayList<>();
    pciResp?.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id.toString())
      }
      log.info(item.toString())

      if (item?.pkg?.name.toString() == "K-Int Deletion Test Package 001") {
        pcisToDelete.add(item.id.toString())
      }
    }

    visualiseHierarchy(pciIds)

    PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
    PackageContentItem pci2 = findPCIByPackageName("K-Int Deletion Test Package 002")

    log.info(pci1.toString())
    log.info(pci2.toString())

    def requestBody = [pCIIds: pcisToDelete]

    when: "A delete request is made."
    Map deleteResp = doPost("/erm/pci/hdelete", requestBody)

    then: "One single-chain PCI is marked for deletion."
    deleteResp
    deleteResp.pci
    deleteResp.pci.size() == 1
    pci1.id == deleteResp.pci[0]

    deleteResp.pti.size() == 1
    pci1.pti.id == deleteResp.pti[0]

    deleteResp.ti.size() == 2
    // FIXME: need to extend this to find all TI ids
    (pci1.pti.titleInstance.id in deleteResp.ti)

    deleteResp.work.size() == 1
    pci1.pti.titleInstance.work.id == deleteResp.work[0]

    cleanup:
    log.info("--- Running Cleanup ---")
    clearResources()
    log.info("--- Running Cleanup ---")
  }

  void "Scenario 7: Two single-chain PCIs marked for deletion but one pci is attached to an agreement line."() {
    given: "Two single-chain PCIs are marked for deletion but one is attached."
    Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_2.json')
    doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 002']])
    List pciResp = doGet("/erm/pci")
    pciIds = []
    List<String> pcisToDelete = new ArrayList<>();
    pciResp?.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id.toString())
        pcisToDelete.add(item.id.toString())
      }
    }

    visualiseHierarchy(pciIds)

    PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
    PackageContentItem pci2 = findPCIByPackageName("K-Int Deletion Test Package 002") // Attached to Agreement Line

    String agreement_name = "matts_agreement"
    Map agreementResp = createAgreement(agreement_name)
    addEntitlementForAgreement(agreement_name, pci2.id)

    def requestBody = [pCIIds: pcisToDelete]

    when: "A delete request is made."
    Map deleteResp = doPost("/erm/pci/hdelete", requestBody)

    then: "Only one PCI chain is marked for deletion."
    pcisToDelete.size() == 2
    deleteResp
    deleteResp.pci
    deleteResp.pci.size() == 1
    pci1.id == deleteResp.pci[0]

    deleteResp.pti.size() == 1
    pci1.pti.id == deleteResp.pti[0]

    deleteResp.ti.size() == 2
    // FIXME: need to extend this to find all TI ids
    (pci1.pti.titleInstance.id in deleteResp.ti)

    deleteResp.work.size() == 1
    pci1.pti.titleInstance.work.id == deleteResp.work[0]

    // FIXME: Also need to fetch agreement line and check ID on item->resource matches pci2.id

    cleanup:
    log.info("--- Running Cleanup ---")
    clearResources()
    log.info("--- Running Cleanup ---")
  }

  void "Scenario 8: Two single-chain PCIs marked for deletion but one PCI's PTI is attached to an agreement line."() {
    given: "Two single-chain PCIs are marked for deletion but one has a PTI which is attached."
    Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_2.json')
    doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 002']])
    List pciResp = doGet("/erm/pci")
    pciIds = []
    List<String> pcisToDelete = new ArrayList<>();
    pciResp?.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id.toString())
        pcisToDelete.add(item.id.toString())
      }
    }

    visualiseHierarchy(pciIds)

    PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
    PackageContentItem pci2 = findPCIByPackageName("K-Int Deletion Test Package 002") // Attached to Agreement Line

    String agreement_name = "matts_agreement"
    Map agreementResp = createAgreement(agreement_name)
    addEntitlementForAgreement(agreement_name, pci2.pti.id)

    def requestBody = [pCIIds: pcisToDelete]

    when: "A delete request is made."
    Map deleteResp = doPost("/erm/pci/hdelete", requestBody)

    then: "One full PCI chain is marked for deletion, and one single PCI id is marked for deletion."
    pcisToDelete.size() == 2
    deleteResp
    deleteResp.pci
    deleteResp.pci.size() == 2
    (pci1.id in deleteResp.pci) && (pci2.id in deleteResp.pci)

    deleteResp.pti.size() == 1
    pci1.pti.id == deleteResp.pti[0]

    deleteResp.ti.size() == 2
    // FIXME: need to extend this to find all TI ids
    (pci1.pti.titleInstance.id in deleteResp.ti)

    deleteResp.work.size() == 1
    pci1.pti.titleInstance.work.id == deleteResp.work[0]

    // FIXME: Also need to fetch agreement line and check ID on item->resource matches pci2.id

    cleanup:
    log.info("--- Running Cleanup ---")
    clearResources()
    log.info("--- Running Cleanup ---")
  }
}
