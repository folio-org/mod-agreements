package com.k_int.accesscontrol.core;

import java.time.Instant;

/**
 * Interface representing an access policy rule.
 * An access policy links a specific policy ID to a target resource class and ID,
 * and is typed according to a known `AccessPolicyType` (e.g., ACQ_UNIT).
 * Each implementation (Grails, micronaut, etc) will need to provide its own "entity" class implementing this interface.
 */
public interface AccessPolicy {
  String getId();
  void setId(String id);

  // Access policy itself
  String getDescription();
  void setDescription(String description);

  Instant getDateCreated();
  void setDateCreated(Instant dateCreated);

  AccessPolicyType getType();
  void setType(AccessPolicyType type);

  String getPolicyId();
  void setPolicyId(String policyId);

  // On what resource
  String getResourceClass();
  void setResourceClass(String resourceClass);

  String getResourceId();
  void setResourceId(String resourceId);
}
