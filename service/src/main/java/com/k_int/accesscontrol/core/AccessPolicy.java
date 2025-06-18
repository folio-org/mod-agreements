package com.k_int.accesscontrol.core;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

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
