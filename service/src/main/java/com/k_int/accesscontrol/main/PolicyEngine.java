package com.k_int.accesscontrol.main;

import lombok.Builder;

@Builder
public class PolicyEngine {
  private final PolicyEngineConfiguration config;

  public PolicyEngine(PolicyEngineConfiguration config) {
    this.config = config;
  }

  // FIXME we need a way to
}
