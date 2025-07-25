package com.k_int.accesscontrol.core.http.responses;

import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

/** Helper class to represent the response of access control checks. An implementation can choose to ignore this and return their own API should they wish */
@Getter
@Builder
public class CanAccessResponse {
  /**
   * Indicates whether the user can read the resource.
   * This field is optional and may be null if the read permission is not applicable.
   *
   * @return true if the user can read, false otherwise
   */
  @Nullable
  final Boolean canRead;
  /**
   * Indicates whether the user can update the resource.
   * This field is optional and may be null if the update permission is not applicable.
   *
   * @return true if the user can update, false otherwise
   */
  @Nullable
  final Boolean canUpdate;
  /**
   * Indicates whether the user can delete the resource.
   * This field is optional and may be null if the delete permission is not applicable.
   *
   * @return true if the user can delete, false otherwise
   */
  @Nullable
  final Boolean canDelete;
  /**
   * Indicates whether the user can create the resource.
   * This field is optional and may be null if the create permission is not applicable.
   *
   * @return true if the user can create, false otherwise
   */
  @Nullable
  final Boolean canCreate;
  /**
   * Indicates whether the user can claim the resource.
   * This field is optional and may be null if the claim permission is not applicable.
   *
   * @return true if the user can claim, false otherwise
   */
  @Nullable
  final Boolean canClaim;
  /**
   * Indicates whether the user can apply policies to the resource.
   * This field is optional and may be null if the apply permission is not applicable.
   *
   * @return true if the user can apply policies, false otherwise
   */
  @Nullable
  final Boolean canApplyPolicies;
}
