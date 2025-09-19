package org.olf.General

import grails.testing.mixin.integration.Integration
import jakarta.inject.Inject
import org.olf.BaseSpec
import org.olf.EntitlementService
import org.olf.erm.Entitlement
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

@Stepwise
@Integration
class EntitlementSpec extends BaseSpec  {

  @Inject
  EntitlementService entitlementService;
  String gokbAuthorityName = "GOKB-RESOURCE"
  // packageUuid:titleUuid
  String gokbReference = "26929514-237c-11ed-861d-0242ac120002:26929514-237c-11ed-861d-0242ac120001"

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
  Map postExternalEntitlementNoAg(String agreementId, String authority, String reference) {
    def payload = [
      type: 'external',
      reference: "reference",
      authority: 'gokb-resource',
      description: 'test',
      owner: ['id': 'agreementId']
    ]
    return doPost("/erm/entitlements", payload) as Map
  }

  @Ignore
  Map postExternalEntitlement(String agreementName, String authority, String reference, String description) {

    def payload = [
      items: [
        [
          'type' : 'external' ,
          'reference' : reference ,
          'authority' : authority,
          'resourceName': authority == gokbAuthorityName ? "test resource" : null,
          'description': description
        ]
      ],
      periods: [
        [
          startDate: '2025-08-20'
        ]
      ],
      name: agreementName,
      agreementStatus: "active"
    ]

    return doPost("/erm/sas", payload) as Map
  }

  void setupDataForTest() {
    importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
  }

  void "Should not have a referenceObject if authority is gokb-resource" () {
    when:
      Map postResponse = postExternalEntitlement("test_agreement", gokbAuthorityName, gokbReference, 'testEntitlement')

    then:
      List entitlementsList = doGet("/erm/entitlements")

      def theEntitlement = entitlementsList[0] // Get the first (and only) item
      entitlementsList.each{entitlement -> log.info(entitlement.reference)}
      entitlementsList.each{entitlement -> log.info((entitlement as Map).toMapString())}

      assert theEntitlement.reference == gokbReference
      assert theEntitlement.authority == gokbAuthorityName
      assert theEntitlement.reference_object == null
      assert theEntitlement.resourceName == "test resource"
  }

  void "Should have a referenceObject if authority is NOT gokb-resource" () {
    when:
      postExternalEntitlement("test_agreement2", 'EKB-TITLE', gokbReference, 'titleRef')

    then:
      List entitlementsList = doGet("/erm/entitlements")

      def theEntitlement = entitlementsList[1]
      entitlementsList.each{entitlement -> log.info(entitlement.reference)}
      entitlementsList.each{entitlement -> log.info((entitlement as Map).toMapString())}

      assert theEntitlement.reference == gokbReference
      assert theEntitlement.authority == "EKB-TITLE"
      assert theEntitlement.reference_object != null
      assert theEntitlement.resourceName == null
  }

  void "EntitlementService can find entitlements with gokb authorities" () {
    when:
    def entitlementsFound;
      withTenant {
        entitlementsFound = entitlementService.findEntitlementsByAuthority(gokbAuthorityName)
      }

    then:
      assert entitlementsFound != null;
      assert entitlementsFound.size() == 1;
  }

  void "An entitlement that has a gokb authority for a package that is not yet set to sync, sets it to sync." () {
    setup:
      importPackageFromFileViaService('entitlementSpec/pkgSyncFalse.json')  // K-Int Test Package 001
      List packageList = doGet("/erm/packages")
      packageList.each{resource -> log.info(resource.toString())}
      Pkg originalPkg = (Pkg) packageList.stream().filter(item -> item.name == "Sync False Test Package").collect(Collectors.toList())[0]

    when:
      withTenant {
        entitlementService.processExternalEntitlements()
      }

    then:
      List updatedPackageList = doGet("/erm/packages")
      Pkg updatedPkg = (Pkg) updatedPackageList.stream().filter(item -> item.name == "Sync False Test Package").collect(Collectors.toList())[0]

      assert originalPkg.syncContentsFromSource == false
      assert updatedPkg.syncContentsFromSource == true
  }

  void "An entitlement that has a gokb authority for a package that has sync set to true should be updated to internal." () {
    setup:
      importPackageFromFileViaService('entitlementSpec/pkgSyncTrue.json')  // K-Int Test Package 001
      List packageList = doGet("/erm/packages")

      Pkg originalPkg = (Pkg) packageList.stream().filter(item -> item.name == "Sync True Test Package").collect(Collectors.toList())[0]
      List entitlementsList = doGet("/erm/entitlements")
      Entitlement entitlement = (Entitlement) entitlementsList.stream().filter(item -> item.description == "testEntitlement").collect(Collectors.toList())[0]

    when:
      withTenant {
        entitlementService.processExternalEntitlements()
      }

    then:
      List updatedPackageList = doGet("/erm/packages")
      Pkg updatedPkg = (Pkg) updatedPackageList.stream().filter(item -> item.name == "Sync True Test Package").collect(Collectors.toList())[0]
      updatedPackageList.each{resource -> log.info(resource.toString())}

      // The update should set most properties on the entitlement to null, so we find it using the description for this test.
      List updatedEntitlementList = doGet("/erm/entitlements")
      Entitlement updatedEntitlement = (Entitlement) updatedEntitlementList.stream().filter(item -> item.description == "testEntitlement").collect(Collectors.toList())[0]

      List pcisList = doGet("/erm/pci")
      PackageContentItem pci = (PackageContentItem) pcisList.stream().filter(item -> item.pkg.name == "Sync True Test Package").collect(Collectors.toList())[0]

      assert entitlement.reference == "26929514-237c-11ed-861d-0242ac120002:26929514-237c-11ed-861d-0242ac120001"
      assert updatedEntitlement.reference == null
      assert updatedEntitlement.authority == null
      assert updatedEntitlement.type == "internal"
      assert updatedEntitlement.resourceName == null
      assert updatedEntitlement.resource
      assert updatedEntitlement.resource.id == pci.id
  }


}
