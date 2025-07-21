package com.k_int.accesscontrol.core.http.responses;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/** Helper class to represent the response of access control checks. An implementation can choose to ignore this and return their own API should they wish */
@Data
@Builder
public class CanAccessResponse {
  @Nullable
  Boolean canRead;
  @Nullable
  Boolean canUpdate;
  @Nullable
  Boolean canDelete;
  @Nullable
  Boolean canCreate;
  @Nullable
  Boolean canClaim;
  @Nullable
  Boolean canApplyPolicies;
}
