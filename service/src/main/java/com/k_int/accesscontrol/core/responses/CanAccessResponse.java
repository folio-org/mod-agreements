package com.k_int.accesscontrol.core.responses;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

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
}
