package com.k_int.accesscontrol.core;

public interface PolicySubquery {
  AccessPolicyQueryType getQueryType();
  String getSql(PolicySubqueryParameters parameters);
}
