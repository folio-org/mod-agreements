package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.*;
import com.k_int.accesscontrol.core.AccessPolicyQueryType;
import com.k_int.accesscontrol.core.PolicyEngineException;
import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.PolicySubquery;
import com.k_int.folio.FolioClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Core entry point for evaluating policy restrictions within the access control system.
 * The engine fetches necessary access policy data (e.g., from acquisition units)
 * and composes SQL fragments that can be used to filter Hibernate queries.
 * Configuration is driven by `PolicyEngineConfiguration`.
 */
@Slf4j
public class PolicyEngine {
  private final PolicyEngineConfiguration config;

  public PolicyEngine(PolicyEngineConfiguration config) {
    this.config = config;
  }

  /**
   * There are two types of AccessPolicy query that we might want to handle, subQueries: "Show me all records for which I can do RESTRICTION" and booleanQueries: "Can I do RESTRICTION for resource X?"
   *
   * @param headers The request context headers -- used mainly to connect to FOLIO (or other "internal" services)
   * @param pr The policy restriction which we want to filter by
   * @return A list of PolicySubqueries, either for boolean restriction or for filtering.
   */
  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr) throws PolicyEngineException {
    List<PolicySubquery> policySubqueries = new ArrayList<>();

    if (pr.equals(PolicyRestriction.CLAIM)) {
      throw new PolicyEngineException("PolicySubquery list is not valid for PolicyRestriction.CLAIM", PolicyEngineException.INVALID_RESTRICTION);
    }

    if (config.acquisitionUnits) {
      AcquisitionUnitPolicyEngineImplementor acqUnitPolicyEngine = new AcquisitionUnitPolicyEngineImplementor(config);
      // TODO do we want to catch the PolicyEngineException here or throw and 500? -- likely the latter
      policySubqueries.addAll(acqUnitPolicyEngine.getPolicySubqueries(headers, pr, AccessPolicyQueryType.LIST));
    }

    return policySubqueries;
  }
}
