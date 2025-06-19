package com.k_int.accesscontrol.grails

import com.fasterxml.jackson.databind.JsonNode
import com.k_int.folio.InternalFolioClient
import com.k_int.folio.InternalFolioClientException
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
    def result = [:]

    String okapiBaseUri = "https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}"
    log.info("LOGDEBUG BASE OKAPI URI: ${okapiBaseUri}")

    try {
      //InternalFolioClient internalFolioClient = new InternalFolioClient(okapiBaseUri)
      // FIXME DO NOT CONNECT DIRECTLY TO EUREKA SNAPSHOT...
      InternalFolioClient internalFolioClient = new InternalFolioClient("https://folio-etesting-snapshot-kong.ci.folio.org")

      // FIXME obviously this isn't what we need to do long term
      SASResults results = internalFolioClient.get(
          "/erm/sas",
          [
              "accept", "application/json",
              "Cookie", "folioAccessToken=eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI4MW84X09UYVIyN2dUWmduNlJzQXMtT2lBa0FCZV94SXdIZFJIQzlmR1JnIn0.eyJleHAiOjE3NTAzNDE2ODIsImlhdCI6MTc1MDM0MTM4MiwiYXV0aF90aW1lIjoxNzUwMzM0ODQxLCJqdGkiOiJvbnJ0cnQ6ZTdkYTc4N2UtMzBmYS00MWZmLTkyOWUtNTQzZTRhM2ExZmJkIiwiaXNzIjoiaHR0cHM6Ly9mb2xpby1ldGVzdGluZy1zbmFwc2hvdC1rZXljbG9hay5jaS5mb2xpby5vcmcvcmVhbG1zL2Rpa3UiLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiZGlrdV9hZG1pbiIsInR5cCI6IkJlYXJlciIsImF6cCI6ImRpa3UtYXBwbGljYXRpb24iLCJzaWQiOiI3YWEzMmNmMS0yYzFkLTRkZTMtYTRjZi00NWI3NTg0NDYzMjMiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbIi8qIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsImFkbWluUm9sZSIsInVtYV9hdXRob3JpemF0aW9uIiwiZGVmYXVsdC1yb2xlcy1kaWt1Il19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwidXNlcl9pZCI6IjU1OWFlNTBlLTcxYmEtNDI5My04MmUwLWZkYmVhZGFmNDRjMSIsIm5hbWUiOiJEaWt1X2FkbWluIEFETUlOSVNUUkFUT1IiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJkaWt1X2FkbWluIiwiZ2l2ZW5fbmFtZSI6IkRpa3VfYWRtaW4iLCJmYW1pbHlfbmFtZSI6IkFETUlOSVNUUkFUT1IiLCJlbWFpbCI6ImRpa3VfYWRtaW5AZXhhbXBsZS5vcmcifQ.lCDFFPEGSTPRVCPFUOqkM45Ele3mVjUBhr1GV0iYfe4-KKPYw-NXiCcVexWQSdWjOFas8_MQbldbE8xJMOVg64dzeEHmoJ5KAUy-g31piTLWQfi7w4S_01lmCiVBHzSxbN4gjAqbq4_VhmRujeJZRye_DVVQrYC1gKr7fKdTyLWu5EkHgBLaYQHa7NQCqO1xFpWxG74XLNSL6in9rGVUf-ymJ-vsqxp6pkGbmLwW5DttF5a9rgLEglrjfNI7p0-Y9wu0NzN4OymXOVCa9cfJVF4WF_d9btdrlaFiJlQQjKNEQNZnhiH6KJJEIE6Me0Yu4NJujw6Nn2rlI28aJhTncg",
              "X-Okapi-Tenant", "diku"
          ] as String[],
          [
              "stats": "true"
          ],
          SASResults
      );
      log.info("LOGDEBUG RESULTS: ${results}")

      // This will NOT work in DC mode :(
      log.info("LOGDEBUG USERID: ${getPatron().id}")
    } catch (InternalFolioClientException e) {
      if (e.cause) {
        log.error("Something went wrong in internal folio redirect: ${e}: CAUSE:", e.cause)
      } else {
        log.error("Something went wrong in internal folio redirect", e)
      }
      // Oops
    }
    request.getHeaderNames()
    log.info("LOGDEBUG HEADERS: ${request.getHeaderNames()}")

    render result as JSON
  }
}
