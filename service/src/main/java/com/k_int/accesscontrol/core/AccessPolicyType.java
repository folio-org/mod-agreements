package com.k_int.accesscontrol.core;

import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;

/**
 * Enumeration of access policy types.
 * These represent different mechanisms of access control, such as:
 * - `ACQ_UNIT`: FOLIO acquisition units
 * - `KI_GRANT`: Reserved for custom grant-based access control (planned)
 */
public enum AccessPolicyType {
  /**
   * Represents no access policy type.
   * This type indicates that there are no specific access policies applied.
   * It is occasionally useful to represent the absence of access control, such as filtering for resources
   * with no policies on them.
   */
  NONE,
  /**
   * Represents access policies based on FOLIO acquisition units.
   * This type is used to manage access control through acquisition units in the FOLIO system.
   */
  ACQ_UNIT,
  /**
   * Reserved for future use, specifically for custom grant-based access control.
   * This type is planned to be implemented in the future to handle access control through grants.
   */
  KI_GRANT;

  /**
   * Converts a string representation of an access policy type into the corresponding enum value.
   * @param value The string representation of the access policy type.
   * @param throwOnNone If true, throws an exception if the resulting type is NONE.
   * @return The corresponding AccessPolicyType enum value.
   * @throws PolicyEngineException If the provided string does not match any valid AccessPolicyType,
   * or if throwOnNone is true and the resulting type is NONE.
   */
  public static AccessPolicyType fromString(String value, boolean throwOnNone) throws PolicyEngineException {
    try {
      AccessPolicyType apt = AccessPolicyType.valueOf(value);
      if (throwOnNone && apt == AccessPolicyType.NONE) {
        throw new PolicyEngineException("GroupedExternalPolicies::fromString error. AccessPolicyType NONE is not allowed here.", PolicyEngineException.INVALID_POLICY_TYPE);
      }
      return apt;
    } catch (Exception e) {
      throw new PolicyEngineException("GroupedExternalPolicies::fromString error. Invalid AccessPolicyType: " + value, PolicyEngineException.INVALID_POLICY_TYPE);
    }
  }

  /**
   * Converts a string representation of an access policy type into the corresponding enum value.
   * This variant always throws an exception if the resulting type is NONE.
   * @param value The string representation of the access policy type.
   * @return The corresponding AccessPolicyType enum value.
   * @throws PolicyEngineException If the provided string does not match any valid AccessPolicyType,
   * or if the resulting type is NONE.
   */
  public static AccessPolicyType fromString(String value) throws PolicyEngineException {
    return AccessPolicyType.fromString(value, true);
  }
}
