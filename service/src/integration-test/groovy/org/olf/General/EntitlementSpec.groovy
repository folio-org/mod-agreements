package org.olf.General

import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import org.olf.erm.SubscriptionAgreement
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise

import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Stepwise
@Integration
class EntitlementSpec extends BaseSpec  {

  String gokbAuthorityName = "GOKB-RESOURCE"
  String gokbReference = "packageUuid:titleUuid"

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
  Map postExternalEntitlement(String agreementName, String authority, String reference) {

    def payload = [
      items: [
        [
          'type' : 'external' ,
          'reference' : reference ,
          'authority' : authority,
          'resourceName': authority == gokbAuthorityName ? "test resource" : null
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
    Map postResponse = postExternalEntitlement("test_agreement", gokbAuthorityName, gokbReference)

    then:
    List entitlementsList = doGet("/erm/entitlements")

    def theEntitlement = entitlementsList[0] // Get the first (and only) item
    entitlementsList.each{entitlement -> log.info(entitlement.reference)}
    entitlementsList.each{entitlement -> log.info((entitlement as Map).toMapString())}

    log.info(theEntitlement.reference);
    assert theEntitlement.reference == "package_ref"
    assert theEntitlement.authority == gokbAuthorityName
    assert theEntitlement.reference_object == null
    assert theEntitlement.resourceName == "test resource"
  }

  void "Should have a referenceObject if authority is NOT gokb-resource" () {
    when:
    postExternalEntitlement("test_agreement2", 'EKB-TITLE', gokbReference)

    then:
    List entitlementsList = doGet("/erm/entitlements")

    def theEntitlement = entitlementsList[1]
    entitlementsList.each{entitlement -> log.info(entitlement.reference)}
    entitlementsList.each{entitlement -> log.info((entitlement as Map).toMapString())}

    log.info(theEntitlement.reference);
    assert theEntitlement.reference == gokbReference
    assert theEntitlement.authority == "EKB-TITLE"
    assert theEntitlement.reference_object != null
    assert theEntitlement.resourceName == null
  }

}
