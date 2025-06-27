package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.acqunits.AcquisitionsClient
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.PolicySubquery
import com.k_int.accesscontrol.core.PolicySubqueryParameters

import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.main.PolicyEngineConfiguration

import com.k_int.folio.FolioClientConfig
import com.k_int.folio.FolioClientException

import com.k_int.okapi.OkapiClient
import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
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

      List<PolicySubquery> policySubqueries = policyEngine.getPolicySubqueries(folioAccessHeaders, PolicyRestriction.READ);

      // We build a parameter block to use on the policy subqueries. Some of these we can probably set up ahead of time...
      PolicySubqueryParameters params = PolicySubqueryParameters
        .builder()
        .accessPolicyTableName("access_policy")
        .accessPolicyTypeColumnName("acc_pol_type")
        .accessPolicyIdColumnName("acc_pol_policy_id")
        .accessPolicyResourceIdColumnName("acc_pol_resource_id")
        .accessPolicyResourceClassColumnName("acc_pol_resource_class")
        .resourceAlias("{alias}")
        .resourceIdColumnName("sa_id") // FIXME we should be able to get these two from "PolicyControlled" trait
        .resourceClass("org.olf.erm.SubscriptionAgreement")
        .build()

      long beforeLookup = System.nanoTime();

      // FIXME Obviously this would need to go in the SubscriptionAgreementController...
      respond doTheLookup(SubscriptionAgreement) {
        policySubqueries.each {psq -> {
          sqlRestriction(psq.getSql(params))
        }};
      }
      long endTime = System.nanoTime();

      Duration loginToPolicy = Duration.ofNanos(beforePolicy - beforeLogin);
      Duration policyToLookup = Duration.ofNanos(beforeLookup - beforePolicy);
      Duration lookupToEnd = Duration.ofNanos(endTime - beforeLookup);

      log.debug("LOGDEBUG login time: ${loginToPolicy}")
      log.debug("LOGDEBUG policy lookup time: ${policyToLookup}")
      log.debug("LOGDEBUG query time: ${lookupToEnd}")

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
}
