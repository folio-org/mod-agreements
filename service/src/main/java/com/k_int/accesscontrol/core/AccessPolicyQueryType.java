package com.k_int.accesscontrol.core;

public enum AccessPolicyQueryType {
  LIST, // Corresponds to PolicySubquery used to filter ALL records by PolicyRestriction
  SINGLE // Corresponds to PolicySubquery used to determine if PolicyRestriction applies to a SINGLE record
}
