package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/**
 * A class to parameterise the inputs for a getSql call on a PolicySubquery
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

  // Sometimes subquery getSql will differ drastically depending on LIST vs SINGLE queries
  AccessPolicyQueryType type;
}
