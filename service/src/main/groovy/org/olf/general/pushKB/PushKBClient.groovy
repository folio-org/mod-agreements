package org.olf.general.pushKB

import groovy.util.logging.Slf4j

// Allow us to parse as JsonObjects for now instead of fully typing all responses
import org.grails.web.json.JSONObject;

// Swapping to Micronaut's Http low level client builder (declarative would be a big shift, instead build requests up)
import io.micronaut.http.client.HttpClient
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.uri.UriBuilder

// Client to handle PUBLIC API calls to a pushKB
@Slf4j
class PushKBClient {
  private HttpClient client;

  public static String HEALTH_ENDPOINT = "/health"
  public static String TEMPORARY_PUSHTASK_ENDPOINT = "/public/temporarypushtask"

  public PushKBClient(String baseUrl) {
    this.client = HttpClient.create(baseUrl.toURL());
  }

  public HttpClient getClient() {
    return this.client;
  }

  public JSONObject health() {
    HttpRequest request = HttpRequest.GET(UriBuilder.of(HEALTH_ENDPOINT)
        .build())

    HttpResponse<String> resp = client.toBlocking().exchange(request, JSONObject)
    JSONObject json = resp.body()

    println("LOGDEBUG DID THIS WORK? ${json}")
  }
}