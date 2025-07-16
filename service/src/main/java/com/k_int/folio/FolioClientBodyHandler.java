package com.k_int.folio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

@Slf4j
public class FolioClientBodyHandler<T> implements HttpResponse.BodyHandler<T> {
  private final ObjectMapper objectMapper;
  private final Class<T> targetType;

  public FolioClientBodyHandler(Class<T> targetType) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule()); // Allows for parsing Instant, LocalDateTime, etc.

    this.objectMapper = mapper;
    this.targetType = targetType;
  }

  // Helper method to safely cast BodySubscriber<String> to BodySubscriber<T>
  @SuppressWarnings("unchecked")
  private HttpResponse.BodySubscriber<T> getStringBodySubscriber() {
    // This cast is safe because it's only called when targetType is String.class
    return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private HttpResponse.BodySubscriber<T> getInputStreamBodySubscriber() {
    // This cast is safe because it's only called when targetType is String.class
    return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.ofInputStream();
  }

  @Override
  public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
    // Check for success status codes (2xx)
    if (responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 300) {

      if (targetType.equals(String.class)) {
        // If the target type is String, we can directly return a BodySubscriber that reads the body as a String
        return getInputStreamBodySubscriber();
      }

      if (targetType.equals(InputStream.class)) {
        // If the target type is String, we can directly return a BodySubscriber that reads the body as a String
        return getStringBodySubscriber();
      }

      // If successful, we want to deserialize the body
      return HttpResponse.BodySubscribers.mapping(
        HttpResponse.BodySubscribers.ofInputStream(),
        inputStream -> {
          try (InputStream is = inputStream) { // Ensure stream is closed
            if (is == null) {
              return null;
            }
            return objectMapper.readValue(is, targetType);
          } catch (IOException e) {
            String errorMessage = "Failed to deserialize response body to " + targetType.getSimpleName();
            log.error(errorMessage, e);
            throw new FolioClientException(errorMessage, FolioClientException.RESPONSE_WRONG_SHAPE, e);
          }
        }
      );
    } else {
      return HttpResponse.BodySubscribers.mapping(
        HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8),
        errorBody -> {
          log.error("Received non-success status {}. Raw body: {}", responseInfo.statusCode(), errorBody);
          throw new FolioClientException(
            "HTTP request failed with status: " + responseInfo.statusCode() + " - " + errorBody,
            FolioClientException.REQUEST_NOT_OK
          );
        }
      );
    }
  }
}