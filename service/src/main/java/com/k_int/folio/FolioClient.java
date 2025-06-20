package com.k_int.folio;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is designed to be a low level client for connecting to FOLIO.
 * It will be spun up in a FOLIO request context and then removed at the end
 * To that end it will simply pass through all headers provided to it,
 * and not be responsible for authentication at all
 * */
@Slf4j
public class FolioClient {
  private final HttpClient httpClient;
  private final String baseUrl;
  private final ObjectMapper objectMapper;
  private final String tenant;

  @Getter
  private final String patronId;

  private final String userLogin;
  private final String userPassword;

  private static final String LOGIN_PATH = "/authn/login-with-expiry";

  // BASEURL is going to need to be passed in because this is an external lib NOT a spring framework plugin
  // OkapiClient uses @Value('${okapi.service.host:}') and @Value('${okapi.service.port:80}')
  public FolioClient(
    String baseUrl,
    String tenant,
    String patronId,
    String userLogin,
    String userPassword
  ) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.tenant = tenant;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    this.objectMapper= new ObjectMapper();
    this.patronId = patronId;

    this.userLogin = userLogin;
    this.userPassword = userPassword;
  }

  public FolioClient(
    FolioClientConfig config
  ) {
    this(config.baseOkapiUri, config.tenantName, config.patronId, config.userLogin, config.userPassword);
  }

  public <T> T get(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException, IOException, InterruptedException {
    URI uri = buildUri(path, queryParams);

    String[] finalHeaders = combineCookies(getBaseHeaders(), headers);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri)
        .GET()
        .headers(finalHeaders)
        .build();

    HttpResponse<String> response;
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      if (response.body() == null || response.body().isEmpty()) {
        return null;
      }
      try {
        return objectMapper.readValue(response.body(), responseType);
      } catch (Exception e) {
        log.error("Failed to cast response body: {} to type: {}", response.body(), responseType.getSimpleName(), e);
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

  public String[] getBaseHeaders() {
    return new String[] {
        "X-Okapi-Tenant", tenant,
        "Content-Type", "application/json",
        "accept", "application/json",
    };
  };

  public static String[] combineCookies(String[] headers1, String[] headers2) {
    return Stream.concat(Stream.of(headers1), Stream.of(headers2))
        .toArray(String[]::new);
  }

  public static Map<String, String> combineQueryParams(Map<String,String> map1, Map<String, String> map2) {
    return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream()).collect(
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public String[] getFolioAccessTokenCookie(String[] headers) throws FolioClientException, IOException, InterruptedException {
    URI uri = URI.create(baseUrl + LOGIN_PATH);
    String credBody = "{ \"username\": \"" + userLogin + "\",  \"password\": \"" + userPassword + "\"}";

    // Concatenate baseHeaders and headers
    String[] finalHeaders = combineCookies(getBaseHeaders(), headers);

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
