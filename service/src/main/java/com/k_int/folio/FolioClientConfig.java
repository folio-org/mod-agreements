package com.k_int.folio;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@AllArgsConstructor
public class FolioClientConfig {
  String baseOkapiUri;
  String tenantName;

  String patronId;

  @Nullable
  String userLogin;
  @Nullable
  String userPassword;
}
