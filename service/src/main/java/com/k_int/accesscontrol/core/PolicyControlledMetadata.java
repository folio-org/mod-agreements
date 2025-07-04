package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PolicyControlledMetadata {
  String resourceClass;
  String resourceIdColumn;
}
