package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

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
  String resourceAlias;
  String resourceIdColumnName;
  String resourceClass;
}
