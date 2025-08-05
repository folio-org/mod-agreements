package com.k_int.accesscontrol.core.http.responses;

import lombok.Builder;
import lombok.Getter;

/**
 * A basic implementation of the Policy interface to allow for Policy to be built using ONLY identifiers
 */
@Getter
@Builder
public class BasicPolicy implements Policy {
  String id;
}
