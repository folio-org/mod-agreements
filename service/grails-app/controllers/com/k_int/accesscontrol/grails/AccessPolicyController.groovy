package com.k_int.accesscontrol.grails

import com.fasterxml.jackson.databind.JsonNode

import com.k_int.accesscontrol.acqunits.AcquisitionsClient
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnit
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembership
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembershipResponse
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitResponse
import com.k_int.folio.FolioClientConfig
import com.k_int.folio.FolioClientException

import com.k_int.okapi.OkapiClient
import com.k_int.okapi.OkapiTenantResolver

import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController

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


    // Dynamic folio client config
//    FolioClientConfig dynamicFolioClientConfig = new FolioClientConfig(
//        "https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}", // baseOkapiUri
//        OkapiTenantResolver.schemaNameToTenantId(Tenants.currentId()), // tenantName
//        getPatron().id, // patronId
//    );

    // BF Sunflower folio client config
    FolioClientConfig folioClientConfig = new FolioClientConfig(
        "https://kong-bugfest-sunflower.int.aws.folio.org", // baseOkapiUri
        "fs09000000", // tenantName
        "9eb67301-6f6e-468f-9b1a-6134dc39a684", // patronId
        "folio",
        "folio"
    );

    // Eureka Snapshot folio client config
//    FolioClientConfig folioClientConfig = new FolioClientConfig(
//        "https://folio-etesting-snapshot-kong.ci.folio.org", // baseOkapiUri
//        "diku", // tenantName
//        "a432e091-e445-40e7-a7a6-e31c035cd51a", // patronId
//        "diku_admin",
//        "admin"
//    );

    log.info("LOGDEBUG BASE OKAPI URI: ${folioClientConfig.baseOkapiUri}")
    log.info("LOGDEBUG TENANT ID: ${folioClientConfig.tenantName}")
    log.info("LOGDEBUG PATRON ID: ${folioClientConfig.patronId}")
    log.info("LOGDEBUG USER LOGIN: ${folioClientConfig.userLogin}")
    log.info("LOGDEBUG USER PASSWORD: ${folioClientConfig.userPassword}")

    try {
      AcquisitionsClient acqClient = new AcquisitionsClient(folioClientConfig);

      // FIXME in the final work we will just pass down request context headers instead, not do a separate login
      String[] folioAccessHeaders = acqClient.getFolioAccessTokenCookie([] as String[]);

      log.info("LOGDEBUG LOGIN COOKIE: ${folioAccessHeaders}")
      // FIXME obviously this isn't what we need to do long term

      // Fetch acq units which DO NOT restrict read
      List<AcquisitionUnit> acqUnitsNoRestrictRead = acqClient.getNoRestrictReadAcquisitionUnits(folioAccessHeaders, Collections.emptyMap()).acquisitionsUnits;

      // Fetch acq units which DO restrict read
      List<AcquisitionUnit> acqUnitsRestrictRead = acqClient.getRestrictReadAcquisitionUnits(folioAccessHeaders, Collections.emptyMap()).acquisitionsUnits;

      log.info("LOGDEBUG acqUnitsNoRestrictRead: ${acqUnitsNoRestrictRead}")
      log.info("LOGDEBUG acqUnitsRestrictRead: ${acqUnitsRestrictRead}")

      // This fetches the acq memberships patron holds
      List<AcquisitionUnitMembership> acquisitionUnitMemberships = acqClient.getPatronAcquisitionUnitMemberships(folioAccessHeaders, Collections.emptyMap()).acquisitionsUnitMemberships;

      log.info("LOGDEBUG acqUnitMemberships: ${acquisitionUnitMemberships}")

      // We aim for 3 lists.
      /*
      1.  **List A** – Acquisition units the user _is a member of_ and which _restrict READ_ access.

      2.  **List B** – Acquisition units that _do not restrict READ_ access for anyone.

      3.  **List C** – Acquisition units the user _is NOT a member of_ but _restrict READ_ access.
     */

      // List B is acqUnitsNoRestrictRead
      //  construct List A
      List<AcquisitionUnit> acqUnitsMemberAndRestrict = acqUnitsRestrictRead
        .stream()
        .filter { AcquisitionUnit au -> {
          return acquisitionUnitMemberships.stream().anyMatch { AcquisitionUnitMembership aum -> {
            return aum.acquisitionsUnitId == au.id && aum.userId == folioClientConfig.patronId
          }}
        }}
        .collect()

      //  construct List C
      List<AcquisitionUnit> acqUnitsNotMemberAndRestrict = acqUnitsRestrictRead
        .stream()
        .filter { AcquisitionUnit au -> {
          return acquisitionUnitMemberships.stream().noneMatch { AcquisitionUnitMembership aum -> {
            return aum.acquisitionsUnitId == au.id && aum.userId == folioClientConfig.patronId
          }}
        }}
        .collect()

      log.info("LOGDEBUG List A: ${acqUnitsMemberAndRestrict}")
      log.info("LOGDEBUG List B: ${acqUnitsNoRestrictRead}")
      log.info("LOGDEBUG List C: ${acqUnitsNotMemberAndRestrict}")

      log.info("LOGDEBUG List A SIZE: ${acqUnitsMemberAndRestrict.size()}")
      log.info("LOGDEBUG List B SIZE: ${acqUnitsNoRestrictRead.size()}")
      log.info("LOGDEBUG List C SIZE: ${acqUnitsNotMemberAndRestrict.size()}")

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
