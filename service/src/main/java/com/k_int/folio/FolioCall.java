package com.k_int.folio;

/**
 * Functional interface representing a call to the FOLIO client.
 * <p>
 * This interface is used to encapsulate operations that can be executed against the FOLIO client,
 * allowing for exception handling and retry logic to be applied uniformly.
 * </p>
 *
 * @param <T> the type of the result returned by the FOLIO client call
 */
@FunctionalInterface
public interface FolioCall<T> {
  T execute() throws Exception;
}
