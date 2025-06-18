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
  private AccessPolicyType type;
  private String description;
  private Instant dateCreated;
  private String policyId;
}
