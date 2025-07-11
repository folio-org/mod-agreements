package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/**
 * Parameters for generating SQL subqueries based on access policy restrictions.
 * <p>
 * This class encapsulates the necessary parameters to create a subquery that filters
 * records according to access policies, including details about the policy table,
 * resource class, and specific resource IDs.
 * </p>
 */
@Data
@Builder
public class PolicySubqueryParameters {
  String accessPolicyTableName;
  String accessPolicyTypeColumnName;
  String accessPolicyIdColumnName;
  String accessPolicyResourceIdColumnName;
  String accessPolicyResourceClassColumnName;
  String resourceClass;

  // Allows table name/id collections
  String resourceAlias;
  String resourceIdColumnName;

  // Allows individual id matching
  @Nullable
  String resourceId;
}
