package com.k_int.folio.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;

/** A FOLIO login response.
 * <p>
 * This class represents the structure of a login response from the authn/login-with-expiry endpoint in a FOLIO system,
 * including token expiration times.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenExpirationResponse {
  Instant refreshTokenExpiration;
  Instant accessTokenExpiration;
}