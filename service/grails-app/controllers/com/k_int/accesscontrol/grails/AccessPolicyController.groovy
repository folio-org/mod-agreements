package com.k_int.accesscontrol.grails

import com.fasterxml.jackson.databind.JsonNode
import com.k_int.folio.FolioClient
import com.k_int.folio.FolioClientException
import com.k_int.okapi.OkapiClient
import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
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
    SASResults results;

    String okapiBaseUri = "https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}"
    log.info("LOGDEBUG BASE OKAPI URI: ${okapiBaseUri}")

    try {
      //FolioClient folioClient = new folioClient(okapiBaseUri)
      // FIXME DO NOT CONNECT DIRECTLY TO EUREKA SNAPSHOT...
      FolioClient folioClient = new FolioClient("https://folio-etesting-snapshot-kong.ci.folio.org")

      String[] folioAccesssHeaders = folioClient.getFolioAccessTokenCookie(
          "diku_admin",
          "admin",
          [
              "X-Okapi-Tenant", "diku"
          ] as String[]
      );

      log.info("LOGDEBUG LOGIN COOKIE: ${folioAccesssHeaders}")
      // FIXME obviously this isn't what we need to do long term
      results = folioClient.get(
          "/erm/sas",
          FolioClient.combineCookies(
              [
                  "accept", "application/json",
                  "X-Okapi-Tenant", "diku"
              ] as String[],
              folioAccesssHeaders
          ),
          [
              "stats": "true"
          ],
          SASResults
      );


      log.info("LOGDEBUG RESULTS: ${results}")

      // This will NOT work in DC mode :(
      //log.info("LOGDEBUG USERID: ${getPatron().id}")
    } catch (FolioClientException e) {
      if (e.cause) {
        log.error("Something went wrong in internal folio redirect: ${e}: CAUSE:", e.cause)
      } else {
        log.error("Something went wrong in internal folio redirect", e)
      }
      // Oops
    }
    request.getHeaderNames()
    log.info("LOGDEBUG HEADERS: ${request.getHeaderNames()}")

    render results as JSON
  }
}
