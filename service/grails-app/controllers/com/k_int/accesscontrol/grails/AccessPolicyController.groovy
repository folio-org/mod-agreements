package com.k_int.accesscontrol.grails

import com.fasterxml.jackson.databind.JsonNode

import com.k_int.accesscontrol.acqunits.AcquisitionsClient
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnit
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
      AcquisitionUnitResponse acqUnits = acqClient.getAcquisitionUnits(folioAccessHeaders, Collections.emptyMap());

      log.info("LOGDEBUG acqUnits: ${acqUnits}")
      log.info("LOGDEBUG acqUnits: ${acqUnits.acquisitionsUnits}")

      AcquisitionUnitMembershipResponse acquisitionUnitMemberships = acqClient.getPatronAcquisitionUnitMemberships(folioAccessHeaders, Collections.emptyMap(), patronId);

      log.info("LOGDEBUG acqUnitMemberships: ${acquisitionUnitMemberships}")
      log.info("LOGDEBUG acqUnitMemberships: ${acquisitionUnitMemberships.acquisitionsUnitMemberships}")

      // This will NOT work in DC mode :(
      //log.info("LOGDEBUG USERID: ${getPatron().id}")
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
