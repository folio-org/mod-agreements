package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PolicyControlledMetadata {
  String resourceClass;
  String resourceIdColumn;

  String ownerField;
  Class<?> ownerClass;

  // FIXME Massively a work in progress. This is likely not how this will go
  List<Map<String, String>> aliases;
}
