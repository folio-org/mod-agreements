package com.k_int.accesscontrol.core.policycontrolled;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder
public class PolicyControlledMetadata {
  String resourceClassName;
  String resourceIdColumn;
  String resourceIdField;

  // Ownership metadata
  String ownerColumn;
  String ownerField;
  Class<?> ownerClass;
  // Track how "far up" the heirarchy this PolicyControlledMetadata is. -1 is the "base class", 0 is its owner class, etc etc
  @Builder.Default
  int ownerLevel = -1;

  // Owner alias fields
  @Nullable
  String aliasName;
  @Nullable
  String aliasOwnerColumn; // Stores the reference TO THIS PCM, ie how to alias to this from the LAST level.
  String aliasOwnerField; // Stores the reference TO THIS PCM, ie how to alias to this from the LAST level.
}
