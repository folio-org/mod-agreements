package com.k_int.folio;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Stream;

/**
 * This is designed to be a low level client for connecting to FOLIO.
 * It will be spun up in a FOLIO request context and then removed at the end
 * To that end it will simply pass through all headers provided to it,
 * and not be responsible for authentication at all
 * */
public class FolioClient {
  private final HttpClient httpClient;
  private final String baseUrl;
  private final ObjectMapper objectMapper;

  private static final String LOGIN_PATH = "/authn/login-with-expiry";

  // BASEURL is going to need to be passed in because this is an external lib NOT a spring framework plugin
  // OkapiClient uses @Value('${okapi.service.host:}') and @Value('${okapi.service.port:80}')
  public FolioClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    this.objectMapper= new ObjectMapper();
  }

  public <T> T get(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException, IOException, InterruptedException {
    URI uri = buildUri(path, queryParams);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri)
        .GET()
        .headers(headers)
        .build();

    HttpResponse<String> response;
    //try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    /*} catch (Exception e){
      throw new InternalFolioClientException("Request failed", InternalFolioClientException.FAILED_REQUEST, e.getCause());
    }*/

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      if (response.body() == null || response.body().isEmpty()) {
        return null;
      }
      try {
        return objectMapper.readValue(response.body(), responseType);
      } catch (Exception e) {
        throw new FolioClientException("Failed to cast response body: " + response.body() + " to type: " + responseType.getSimpleName(), FolioClientException.RESPONSE_WRONG_SHAPE, e);
      }
    } else {
      throw new FolioClientException("GET request failed: " + response.statusCode() + " - " + response.body(), FolioClientException.REQUEST_NOT_OK);
    }
  }

  // There's almost definitely a better way to build this URI... this will do for now
  private URI buildUri(String path, Map<String, String> queryParams) {
    StringBuilder url = new StringBuilder(baseUrl).append(path);

    if (queryParams != null && !queryParams.isEmpty()) {
      StringJoiner joiner = new StringJoiner("&");
      for (Map.Entry<String, String> entry : queryParams.entrySet()) {
        String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
        String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
        joiner.add(key + "=" + value);
      }
      url.append("?").append(joiner.toString());
    }

    return URI.create(url.toString());
  }

  public static String[] combineCookies(String[] headers1, String[] headers2) {
    return Stream.concat(Stream.of(headers1), Stream.of(headers2))
        .toArray(String[]::new);
  }

  public String[] getFolioAccessTokenCookie(String username, String password, String[] headers) throws FolioClientException, IOException, InterruptedException {
    URI uri = URI.create(baseUrl + LOGIN_PATH);
    String credBody = "{ \"username\": \"" + username + "\",  \"password\": \"" + password + "\"}";

    String[] baseHeaders = new String[] {"Content-Type", "application/json"};

    // Concatenate baseHeaders and headers
    String[] finalHeaders = combineCookies(baseHeaders, headers);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri)
        .POST(HttpRequest.BodyPublishers.ofString(credBody))
        .headers(finalHeaders)
        .build();

    HttpResponse<String> response;
    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    String folioAccessToken = "";
    for (String string : response.headers().map().get("set-cookie")) {
      if (string.matches("folioAccessToken=.*")) {
        folioAccessToken = string;
      }
    }

    return new String[] { "Cookie", folioAccessToken };
  }


  // TODO extend this to POST, PUT, etc
}
