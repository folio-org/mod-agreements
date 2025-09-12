package org.olf.General

import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import org.olf.erm.SubscriptionAgreement
import spock.lang.Ignore
import spock.lang.Stepwise

import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Stepwise
@Integration
class EntitlementSpec extends BaseSpec  {

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
          'authority' : authority
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

    return doPost("/erm/sas?fetchExternalResources=false", payload) as Map
  }

  void setupDataForTest() {
    importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
  }

  void "No resources present in request body" () {
    setup:
//    setupDataForTest()
    log.info("In setup")

    when:
    Map postResponse = postExternalEntitlement("test_agreement", 'EKB-PACKAGE', "package_ref")
    postExternalEntitlement("test_agreement2", 'EKB-TITLE', 'acde070d-8c4c-4f0d-9d8a-162843c10333:acde070d-8c4c-4f0d-9d8a-162843c10333')
    postExternalEntitlementNoAg("cd452f37-fef2-4e59-b052-628050085f73", 'EKB-PACKAGE', "package_ref")

    then:
    List entitlementsList = doGet("/erm/entitlements")

    def theEntitlement = entitlementsList[0] // Get the first (and only) item
    entitlementsList.each{entitlement -> log.info(entitlement.reference)}
    entitlementsList.each{entitlement -> log.info((entitlement as Map).toMapString())}

      try{
        def refObject = theEntitlement.reference_object
        log.info("ref object found: {}", refObject)
    } catch (Exception e) {
        log.error("Caught exception while accessing reference_object!", e)
      }


    log.info(theEntitlement.reference);
    assert theEntitlement.reference == "package_ref"
    assert theEntitlement.authority == "EKB-PACKAGE"

  }

}
