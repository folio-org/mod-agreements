package com.k_int.accesscontrol.grails

import com.fasterxml.jackson.databind.JsonNode
import com.k_int.accesscontrol.acqunits.AcquisitionsClient
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnit
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembershipResponse
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitResponse
import com.k_int.folio.FolioClient
import com.k_int.folio.FolioClientException
import com.k_int.okapi.OkapiClient
import com.k_int.okapi.OkapiTenantResolver
import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController

public class SASResults {
  public List<JsonNode> results;
  public int pageSize;
  public int page;
  public int totalPages;
  public Object meta;
  public int totalRecords;
  public int total;
}

@Slf4j
@CurrentTenant
class AccessPolicyController extends OkapiTenantAwareController<AccessPolicyEntity> {
  OkapiClient okapiClient;

  AccessPolicyController() {
    super(AccessPolicyEntity)
  }

  public testRequestContext() {
    String okapiBaseUri = "https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}"
    log.info("LOGDEBUG BASE OKAPI URI: ${okapiBaseUri}")


    // FIXME this should be central in any controller doing AccessControl
    final String tenantName = OkapiTenantResolver.schemaNameToTenantId(Tenants.currentId())
    log.info("LOGDEBUG TENANT ID: ${tenantName}")

    // This is the user ID coming in through spring security
    // String patronId = getPatron().id;
    String patronId = "d54d4b04-f3f3-56e9-9b60-e756f3199698";

    try {
      //AcquisitionsClient acqClient = new AcquisitionsClient(okapiBaseUri, tenantName)
      // FIXME DO NOT CONNECT DIRECTLY TO EUREKA SNAPSHOT...
      AcquisitionsClient acqClient = new AcquisitionsClient("https://folio-etesting-snapshot-kong.ci.folio.org", "diku");

      String[] folioAccessHeaders = acqClient.getFolioAccessTokenCookie(
          "diku_admin",
          "admin",
          [] as String[]
      );

      log.info("LOGDEBUG LOGIN COOKIE: ${folioAccessHeaders}")
      // FIXME obviously this isn't what we need to do long term
      AcquisitionUnitResponse acqUnits = acqClient.getAcquisitionUnits(folioAccessHeaders, Collections.emptyMap());

      log.info("LOGDEBUG acqUnits: ${acqUnits}")
      log.info("LOGDEBUG acqUnits: ${acqUnits.acquisitionsUnits}")

      AcquisitionUnitMembershipResponse acquisitionUnitMemberships = acqClient.getAcquisitionUnitMemberships(folioAccessHeaders, Collections.emptyMap());

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
