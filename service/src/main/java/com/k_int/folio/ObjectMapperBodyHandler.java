package com.k_int.folio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

@Slf4j
public class ObjectMapperBodyHandler<T> implements HttpResponse.BodyHandler<T> {
  private final ObjectMapper objectMapper;
  private final Class<T> targetType;

  public ObjectMapperBodyHandler(Class<T> targetType) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule()); // Allows for parsing Instant, LocalDateTime, etc.

    this.objectMapper = mapper;
    this.targetType = targetType;
  }

  @Override
  public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
    // Check for success status codes (2xx)
    if (responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 300) {
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