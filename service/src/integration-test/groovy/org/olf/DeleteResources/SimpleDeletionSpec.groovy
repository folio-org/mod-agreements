package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.ErmResourceService
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import groovyx.net.http.HttpException
import org.spockframework.runtime.SpecificationContext
import org.spockframework.runtime.model.SpecInfo
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
@Slf4j
class SimpleDeletionSpec extends DeletionBaseSpec {

  @Shared
  String pkg_id

  List<String> pciIds;
  List<String> ptiIds;
  List<String> tiIds;

  def setup() {
    SpecificationContext currentSpecInfo = specificationContext;
    if (!specificationContext.currentFeature?.name.contains("Scenario")) {
      // If not in a SimpleDeletionSpec Scenario (i.e. in a tenant purge/ensure test tenant), don't try to load packages yet.
      log.info("--- Skipping Setup for tenant setup tests: ${currentSpecInfo.currentSpec.displayName} (Feature: ${currentSpecInfo.currentFeature?.name}) ---")
      return;
    }
    log.info("--- Running Setup for test: ${specificationContext.currentIteration?.name ?: specificationContext.currentFeature?.name} ---")

    // Load Single Chain PCI
    importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Deletion Test Package 001']])
    pkg_id = resp[0].id

    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    Map sasStatsResp = doGet("/erm/statistics/sasCount")

    // Fetch PCIs and save IDs to list
    List pciResp = doGet("/erm/pci")
    List ptiResp = doGet("/erm/pti")

    pciIds = []
    pciResp?.forEach { Map item ->
      if (item?.id) {
        pciIds.add(item.id.toString())
      }
    }

    ptiIds = []
    ptiResp?.forEach { Map item ->
      if (item?.id) {
        ptiIds.add(item.id.toString())
      }
    }

    log.info("Found PCI IDs (in setup): {}", pciIds)
    log.info("Found PTI IDs (in setup): {}", ptiIds)
    log.info("KB Counts (in setup): {}", kbStatsResp?.toString())
    log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    if (!pciIds.isEmpty()) {
      visualiseHierarchy(pciIds)
    }
    log.info("--- Setup Complete ---")
  }

  def cleanup() {
    // Used to clear resources from DB between tests.
    // Specification logic is needed to ensure clearResources is not run for BaseSpec tests (which will cause it to fail).
    SpecificationContext currentSpecInfo = specificationContext;
    if (specificationContext.currentFeature?.name.contains("Scenario")) {
      log.info("--- Running Cleanup for test: ${currentSpecInfo.currentIteration?.name ?: currentSpecInfo.currentFeature?.name ?: currentSpecInfo.currentSpec.name} in ${SimpleDeletionSpec.simpleName} ---")
      try {
        clearResources()
      } catch (Exception e) {
        log.error("--- Error during SimpleDeletionSpec cleanup: ${e.message}", e)
      }
      log.info("--- ${SimpleDeletionSpec.simpleName} Cleanup Complete ---")
    } else {
      log.info("--- Skipping SimpleDeletionSpec-specific cleanup for BaseSpec feature run in: ${currentSpecInfo.currentSpec.displayName} (Feature: ${currentSpecInfo.currentFeature?.name}) ---")
    }
  }

  void "Scenario 1: Fully delete one PCI chain with no other references"() {
    given: "Setup has found PCI IDs"
      assert pciIds != null: "pciIds should have been initialized by setup()"
      assert !pciIds.isEmpty(): "Setup() must find at least one PCI for this test"
    when: "The first PCI found during setup is marked for deletion"
      List<String> pcisToDelete = [pciIds.get(0)]
      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)

      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
        'pcis' pcisToDelete
      })
      log.info("Delete Response: {}", deleteResp.toString())

      // Get PCI for assertions
      PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
      Set<PackageContentItem> pciSet = [pci1] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)

      log.info(resourceIds.toString())
      log.info(deleteResp.toString())

    then:
      verifySetSizes(deleteResp)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))

  }

  void "Scenario 3: Single PCI marked for deletion when PTI is attached to agreement line."() {
    given: "A PCI that references a PTI attached to an agreement line is marked for deletion."
      PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
      List<String> pcisToDelete = [pci1.id]

    String agreement_name = "test_agreement"
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci1.pti.id)

      def requestBody = [pcis: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", requestBody)
      Map sasStatsResp = doGet("/erm/statistics/sasCount")
      log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    then: "Only the PCI is marked for deletion."
      // Should this be tested for the Stats controller separately?
      sasStatsResp
      sasStatsResp.get("SubscriptionAgreement") == 1
      sasStatsResp.get("Entitlement") == 1

      verifySetSizes(deleteResp, 1, 0, 0, 0)
      verifyPciIds(deleteResp, pcisToDelete.toSet())

      // Does agreement line item->resource id match pci.pti.id
      findAgreementByName("test_agreement").items.resource.get(0).id == pci1.pti.id

  }

  void "Scenario 4: Single PCI marked for deletion when PCI is attached to agreement line."() {
    given: "A PCI that is attached to an agreement line is marked for deletion."
      List<String> pcisToDelete = [pciIds.get(0)]
      PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")

      String agreement_name = "test_agreement"
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci1.id)

      def requestBody = [pcis: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", requestBody)
      Map sasStatsResp = doGet("/erm/statistics/sasCount")
      log.info("SAS Counts (in setup): {}", sasStatsResp?.toString())

    then: "Nothing is marked for deletion."
      // Should this be tested for the Stats controller separately?
      sasStatsResp
      sasStatsResp.get("SubscriptionAgreement") == 1
      sasStatsResp.get("Entitlement") == 1

      deleteResp
      verifySetSizes(deleteResp, 0, 0, 0, 0)

      // Does agreement line item->resource id match pci2.pti.id
      findAgreementByName("test_agreement").items.resource.get(0).id == pci1.id
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

      def requestBody = [pcis: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", requestBody)
      Set<PackageContentItem> pciSet = [pci1, pci2] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)


    then: "All resources are marked for deletion"
      deleteResp
      verifySetSizes(deleteResp, 2, 2, 4, 2)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))

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
        if (item?.pkg?.name.toString() == "K-Int Deletion Test Package 001") {
          pcisToDelete.add(item.id.toString())
        }
      }

      visualiseHierarchy(pciIds)

      PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001") // Marked for deletion
      PackageContentItem pci2 = findPCIByPackageName("K-Int Deletion Test Package 002")

      Set<PackageContentItem> pciSet = [pci1] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)

      def requestBody = [pcis: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", requestBody)

    then: "One single-chain PCI is marked for deletion."
      deleteResp
      verifySetSizes(deleteResp)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))
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

      Set<PackageContentItem> pciSet = [pci1] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)

      String agreement_name = "test_agreement"
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci2.id)

      def requestBody = [pcis: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", requestBody)

    then: "Only one PCI chain is marked for deletion."
      pcisToDelete.size() == 2
      deleteResp
      verifySetSizes(deleteResp)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))

      // Does agreement line item->resource id match pci2.pti.id
      findAgreementByName("test_agreement").items.resource.get(0).id == pci2.id
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
      PackageContentItem pci2 = findPCIByPackageName("K-Int Deletion Test Package 002") // PTI attached to Agreement Line

      String agreement_name = "test_agreement"
      Map agreementResp = createAgreement(agreement_name)
      addEntitlementForAgreement(agreement_name, pci2.pti.id)

      Set<PackageContentItem> pciSet = [pci1] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)
      resourceIds.get("pci").add(pci2.id) // Add pci2 id as expected for deletion.

      def requestBody = [pcis: pcisToDelete]

    when: "A delete request is made."
      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", requestBody)

    then: "One full PCI chain is marked for deletion, and one single PCI id is marked for deletion."
      pcisToDelete.size() == 2
      deleteResp
      verifySetSizes(deleteResp, 2)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))

      // Does agreement line item->resource id match pci2.pti.id
      findAgreementByName("test_agreement").items.resource.get(0).id == pci2.pti.id
  }

  void "Scenario: One single chain PCI and user marks a PTI for deletion."() {
    given: "Setup has found PTI IDs"
      assert ptiIds != null: "ptiIds should have been initialized by setup()"
      assert !ptiIds.isEmpty(): "Setup() must find at least one PTI for this test"
    when: "The first PTI found during setup is marked for deletion"
      List<String> ptisToDelete = [ptiIds.get(0)]
      log.info("Attempting to delete PTI IDs: {}", ptisToDelete)

      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
        'ptis' ptisToDelete
      })
      log.info("Delete Response: {}", deleteResp.toString())

      // Get PCI for assertions
      PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
      Set<PackageContentItem> pciSet = [pci1] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)

      log.info(resourceIds.toString())
      log.info(deleteResp.toString())

    then:
      verifySetSizes(deleteResp, 0, 0, 0, 0)
  }

  void "Scenario: One single chain PCI and user marks PCI and PTI for deletion."() {
    given: "Setup has found PCI IDs"
      assert ptiIds != null: "ptiIds should have been initialized by setup()"
      assert pciIds != null: "pciIds should have been initialized by setup()"
      assert !ptiIds.isEmpty(): "Setup() must find at least one PTI for this test"
      assert !pciIds.isEmpty(): "Setup() must find at least one PCI for this test"
    when: "The first PCI found during setup is marked for deletion"
      List<String> ptisToDelete = [ptiIds.get(0)]
      List<String> pcisToDelete = [pciIds.get(0)]
      log.info("Attempting to delete PTI IDs: {}", ptisToDelete)
      log.info("Attempting to delete PCI IDs: {}", pcisToDelete)

      Map deleteResp = doPost("/erm/hierarchicalDelete/markForDelete", {
        'ptis' ptisToDelete
        'pcis' pcisToDelete
      })
      log.info("Delete Response: {}", deleteResp.toString())

      // Get PCI for assertions
      PackageContentItem pci1 = findPCIByPackageName("K-Int Deletion Test Package 001")
      Set<PackageContentItem> pciSet = [pci1] as Set

      Map resourceMap = getAllResourcesForPCIs(pciSet);
      Map resourceIds = collectResourceIds(resourceMap)

      log.info(resourceIds.toString())
      log.info(deleteResp.toString())

    then:
      verifySetSizes(deleteResp, 1, 1, 2, 1)
      verifyPciIds(deleteResp, resourceIds.get("pci"))
      verifyPtiIds(deleteResp, resourceIds.get("pti"))
      verifyTiIds(deleteResp, resourceIds.get("ti"))
      verifyWorkIds(deleteResp, resourceIds.get("work"))
  }
}
