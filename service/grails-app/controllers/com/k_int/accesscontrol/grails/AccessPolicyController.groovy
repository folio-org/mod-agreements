package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.acqunits.AcquisitionsClient
import com.k_int.accesscontrol.acqunits.UserAcquisitionUnits
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.main.PolicyEngineConfiguration
import com.k_int.accesscontrol.main.PolicyInformation
import com.k_int.folio.FolioClientConfig
import com.k_int.folio.FolioClientException

import com.k_int.okapi.OkapiClient
import com.k_int.okapi.OkapiTenantResolver

import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.erm.SubscriptionAgreement

import java.time.Duration

@Slf4j
@CurrentTenant
class AccessPolicyController extends OkapiTenantAwareController<AccessPolicyEntity> {
  OkapiClient okapiClient;

  AccessPolicyController() {
    super(AccessPolicyEntity)
  }

  public testRequestContext() {
    long startTime = System.nanoTime();
    // FIXME okapiBaseUri/tenantName/patronId should be central in any controller doing AccessControl
    // And obviously shouldn't be hardcoded

//    // Dynamic folio client config
//    FolioClientConfig folioClientConfig = FolioClientConfig.builder()
//      .baseOkapiUri("https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}")
//      .tenantName(OkapiTenantResolver.schemaNameToTenantId(Tenants.currentId()))
//      .patronId(getPatron().id)
//      .build();

    // BF Sunflower folio client config
    FolioClientConfig folioClientConfig = FolioClientConfig.builder()
      .baseOkapiUri("https://kong-bugfest-sunflower.int.aws.folio.org")
      .tenantName("fs09000000")
      .patronId("9eb67301-6f6e-468f-9b1a-6134dc39a684")
      .userLogin("folio")
      .userPassword("folio")
      .build();


//    // Eureka Snapshot folio client config
//    FolioClientConfig folioClientConfig = FolioClientConfig.builder()
//      .baseOkapiUri("https://folio-etesting-snapshot-kong.ci.folio.org")
//      .tenantName("diku")
//      .patronId("a432e091-e445-40e7-a7a6-e31c035cd51a")
//      .userLogin("diku_admin")
//      .userPassword("admin")
//      .build();

    log.info("LOGDEBUG BASE OKAPI URI: ${folioClientConfig.baseOkapiUri}")
    log.info("LOGDEBUG TENANT ID: ${folioClientConfig.tenantName}")
    log.info("LOGDEBUG PATRON ID: ${folioClientConfig.patronId}")
    log.info("LOGDEBUG USER LOGIN: ${folioClientConfig.userLogin}")
    log.info("LOGDEBUG USER PASSWORD: ${folioClientConfig.userPassword}")
    long beforeTry = System.nanoTime();

    try {
      // FIXME This probably ought to be spun up once in a service, rather than per request.
      PolicyEngine policyEngine = new PolicyEngine(
        PolicyEngineConfiguration
          .builder()
          .folioClientConfig(folioClientConfig)
          .acquisitionUnits(true)
          .build()
      )

      /* ------------------------------- LOGIN LOGIC ------------------------------- */
      AcquisitionsClient acqClient = policyEngine.getAcqClient();
      // FIXME in the final work we will just pass down request context headers instead, not do a separate login
      long beforeLogin = System.nanoTime();

      String[] folioAccessHeaders = acqClient.getFolioAccessTokenCookie([] as String[]);

      log.info("LOGDEBUG LOGIN COOKIE: ${folioAccessHeaders}")
      /* ------------------------------- END LOGIN LOGIC ------------------------------- */

      /* ------------------------------- ACTUALLY DO THE WORK FOR EACH POLICY RESTRICTION ------------------------------- */
      long beforePolicy = System.nanoTime();

      PolicyInformation policyInformationRead = policyEngine.getPolicyInformation(folioAccessHeaders, PolicyRestriction.READ);
//      PolicyInformation policyInformationClaim = policyEngine.getPolicyInformation(folioAccessHeaders, PolicyRestriction.CLAIM);
//      PolicyInformation policyInformationCreate = policyEngine.getPolicyInformation(folioAccessHeaders, PolicyRestriction.CREATE);
//      PolicyInformation policyInformationUpdate = policyEngine.getPolicyInformation(folioAccessHeaders, PolicyRestriction.UPDATE);
//      PolicyInformation policyInformationDelete = policyEngine.getPolicyInformation(folioAccessHeaders, PolicyRestriction.DELETE);

      logUserAcquisitionUnits(policyInformationRead.getUserAcquisitionUnits(), "READ")
//      logUserAcquisitionUnits(policyInformationClaim.getUserAcquisitionUnits(), "CLAIM")
//      logUserAcquisitionUnits(policyInformationCreate.getUserAcquisitionUnits(), "CREATE")
//      logUserAcquisitionUnits(policyInformationUpdate.getUserAcquisitionUnits(), "UPDATE")
//      logUserAcquisitionUnits(policyInformationDelete.getUserAcquisitionUnits(), "DELETE")
      long beforeLookup = System.nanoTime();

      // FIXME this is stupid
      respond doTheLookup(SubscriptionAgreement) {
        //FIXME We need to be able to template here I think
        sqlRestriction(policyInformationRead.getAcquisitionSql())
      }
      long endTime = System.nanoTime();

      Duration startToTry = Duration.ofNanos(beforeTry - startTime);
      Duration tryToLogin = Duration.ofNanos(beforeLogin - beforeTry);
      Duration loginToPolicy = Duration.ofNanos(beforePolicy - beforeLogin);
      Duration policyToLookup = Duration.ofNanos(beforeLookup - beforePolicy);
      Duration lookupToEnd = Duration.ofNanos(endTime - beforeLookup);

      log.debug("LOGDEBUG startToTry: ${startToTry}")
      log.debug("LOGDEBUG tryToLogin: ${tryToLogin}")
      log.debug("LOGDEBUG loginToPolicy: ${loginToPolicy}")
      log.debug("LOGDEBUG policyToLookup: ${policyToLookup}")
      log.debug("LOGDEBUG lookupToEnd: ${lookupToEnd}")

      return null;

    } catch (FolioClientException e) {
      if (e.cause) {
        log.error("Something went wrong in folio call: ${e}: CAUSE:", e.cause)
      } else {
        log.error("Something went wrong in folio call", e)
      }
      // Oops
    }

    // Render _something_ for now, see logs for success/failure
    Map results = ["didTheFetch": true];

    render results as JSON
  }

  // FIXME this shouoldn't be in the final code, here for logging while developing
  private logUserAcquisitionUnits(UserAcquisitionUnits uau, String name = "Generic") {
    log.info("LOGDEBUG (${name}) MemberRestrictiveUnits: ${uau.getMemberRestrictiveUnits()}")
    log.info("LOGDEBUG (${name}) NonRestrictiveUnits: ${uau.getNonRestrictiveUnits()}")
    log.info("LOGDEBUG (${name}) NonMemberRestrictiveUnits: ${uau.getNonMemberRestrictiveUnits()}")

    log.info("LOGDEBUG (${name}) MemberRestrictiveUnits SIZE: ${uau.getMemberRestrictiveUnits().size()}")
    log.info("LOGDEBUG (${name}) NonRestrictiveUnits SIZE: ${uau.getNonRestrictiveUnits().size()}")
    log.info("LOGDEBUG (${name}) NonMemberRestrictiveUnits: ${uau.getNonMemberRestrictiveUnits().size()}")
  }
}
