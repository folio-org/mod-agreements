package com.k_int.accesscontrol.core;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * Data model representing an access policy rule.
 * An access policy links a specific policy ID to a target resource class and ID,
 * and is typed according to a known `AccessPolicyType` (e.g., ACQ_UNIT).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessPolicy {
  private String id;

  // Access policy itself
  private String description;
  private Instant dateCreated;
  private AccessPolicyType type;
  private String policyId;

  // On what resource
  private String resourceClass;
  private String resourceId;
}
