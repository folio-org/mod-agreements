package com.k_int.folio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/**
 * Configuration class for Folio client settings.
 * <p>
 * This class encapsulates the necessary configuration parameters for connecting to
 * a Folio instance, including the base URI, tenant name, patron ID, and optional user credentials.
 * </p>
 */
@Data
@AllArgsConstructor
@Builder
public class FolioClientConfig {
  String baseOkapiUri;
  String tenantName;

  String patronId;

  @Nullable
  String userLogin;
  @Nullable
  String userPassword;
}
