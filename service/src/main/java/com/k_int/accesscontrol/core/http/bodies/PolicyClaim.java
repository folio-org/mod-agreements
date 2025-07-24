package com.k_int.accesscontrol.core.http.bodies;

import com.k_int.accesscontrol.core.AccessPolicyType;

/**
 * Represents a policy claim in the context of access control.
 * <p>
 * This interface defines the structure for a policy claim, which includes
 * an identifier, associated policy ID, type of access policy, and a description.
 * </p>
 */
public interface PolicyClaim {
  String getId();
  void setId(String id);

  String getPolicyId();
  void setPolicyId(String policyId);

  AccessPolicyType getType();
  void setType(AccessPolicyType type);

  String getDescription();
  void setDescription(String description);
}
