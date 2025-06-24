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
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Low-level HTTP client for interacting with FOLIO APIs.
 * Supports both synchronous and asynchronous request execution.
 * This client is designed to be lightweight, stateless, and used within
 * a request scope. It is not responsible for session or token management
 * but has the ability to perform a login should the calling code need that.
 */
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

  /**
   * Synchronously executes a GET request and maps the response to the given class.
   *
   * @param path Relative path of the resource to fetch
   * @param headers Headers to pass in the request
   * @param queryParams Map of query parameters
   * @param responseType Target class for response mapping
   * @param <T> Type of the expected result
   * @return Parsed object from JSON response
   * @throws IOException If I/O error occurs
   * @throws InterruptedException If the thread is interrupted
   * @throws FolioClientException For response decoding or non-2xx responses
   */
  public <T> T get(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException, IOException, InterruptedException {
    URI uri = buildUri(path, queryParams);

    String[] finalHeaders = combineHeaders(getBaseHeaders(), headers);

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

  /**
   * Asynchronously executes a GET request and maps the response to the given class.
   *
   * @param path Relative path of the resource to fetch
   * @param headers Headers to pass in the request
   * @param queryParams Map of query parameters
   * @param responseType Target class for response mapping
   * @param <T> Type of the expected result
   * @return CompletableFuture containing the mapped response or exception
   */
  public <T> CompletableFuture<T> getAsync(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) {
    URI uri = buildUri(path, queryParams);
    String[] finalHeaders = combineHeaders(getBaseHeaders(), headers);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(uri)
      .GET()
      .headers(finalHeaders)
      .build();

    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .thenApply(response -> {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          if (response.body() == null || response.body().isEmpty()) {
            return null;
          }
          try {
            return objectMapper.readValue(response.body(), responseType);
          } catch (Exception e) {
            String exceptionMessage = "Failed to cast response body: " + response.body() + " to type: " + responseType.getSimpleName();
            log.error(exceptionMessage, e);
            throw new CompletionException(exceptionMessage, new FolioClientException(exceptionMessage, FolioClientException.RESPONSE_WRONG_SHAPE, e));
          }
        } else {
          String exceptionMessage = "GET request failed: " + response.statusCode() + " - " + response.body();
          log.error(exceptionMessage);
          throw new CompletionException(exceptionMessage, new FolioClientException(exceptionMessage, FolioClientException.REQUEST_NOT_OK));
        }
      });
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

  /**
   * Combines two string header arrays into one.
   *
   * @param headers1 First array
   * @param headers2 Second array
   * @return Combined array
   */
  public static String[] combineHeaders(String[] headers1, String[] headers2) {
    return Stream.concat(Stream.of(headers1), Stream.of(headers2))
        .toArray(String[]::new);
  }

  /**
   * Merges two query parameter maps into a single map.
   *
   * @param map1 First map
   * @param map2 Second map
   * @return Combined map
   */
  public static Map<String, String> combineQueryParams(Map<String,String> map1, Map<String, String> map2) {
    return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream()).collect(
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Returns a new Cookie header containing a valid folioAccessToken.
   * This method performs a blocking login using provided credentials.
   *
   * @param headers Additional headers to use during login
   * @return Array with a single "Cookie" header including folioAccessToken
   * @throws IOException If I/O error occurs
   * @throws InterruptedException If the thread is interrupted
   * @throws FolioClientException For any error during login
   */
  public String[] getFolioAccessTokenCookie(String[] headers) throws FolioClientException, IOException, InterruptedException {
    URI uri = URI.create(baseUrl + LOGIN_PATH);
    String credBody = "{ \"username\": \"" + userLogin + "\",  \"password\": \"" + userPassword + "\"}";

    // Concatenate baseHeaders and headers
    String[] finalHeaders = combineHeaders(getBaseHeaders(), headers);

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

  /**
   * Combines async method with a blocking wrapper that unwraps exceptions
   * into a FolioClientException. Used by synchronous wrappers.
   *
   * @param supplier Supplier of a CompletableFuture
   * @param <T> The result type
   * @return The resolved result of the CompletableFuture
   * @throws FolioClientException If the future fails, times out, or is interrupted
   */
  protected <T> T asyncFolioClientExceptionHelper(Supplier<CompletableFuture<T>> supplier) throws FolioClientException {
    try {
      return supplier.get().get(5, TimeUnit.SECONDS); // configurable timeout
    } catch (ExecutionException e) {
      if (e.getCause() instanceof FolioClientException) {
        throw (FolioClientException) e.getCause(); // rethrow as-is
      }
      throw new FolioClientException("Unhandled async execution error", FolioClientException.GENERIC_ERROR, e.getCause());
    } catch (TimeoutException e) {
      throw new FolioClientException("Async call timed out", FolioClientException.TIMEOUT_ERROR, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new FolioClientException("Async call interrupted", FolioClientException.INTERRUPTED_ERROR, e);
    } catch (Exception e) {
      throw new FolioClientException("Unhandled error", FolioClientException.GENERIC_ERROR, e);
    }
  }

  // TODO extend this to POST, PUT, etc
}
