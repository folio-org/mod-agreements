package com.k_int.accesscontrol.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessPolicy {
  private AccessPolicyType type;
  private String description;
  private Instant dateCreated;

  private String policyUUID; // UUID of the policy in String form
}
