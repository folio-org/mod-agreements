package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.*;
import com.k_int.accesscontrol.core.*;
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
  /**
   * Configuration for the policy engine, including whether to use acquisition units.
   * This is set during construction and used to determine which policy types to query.
   */
  @Getter
  private final PolicyEngineConfiguration config;

  /**
   * The acquisition unit policy engine implementor, which handles policy subquery generation
   * for acquisition units. This is initialized based on the configuration.
   */
  @Getter
  private final AcquisitionUnitPolicyEngineImplementor acquisitionUnitPolicyEngine;

  public PolicyEngine(PolicyEngineConfiguration config) {
    this.config = config;

    if (config.acquisitionUnits) {
      this.acquisitionUnitPolicyEngine = new AcquisitionUnitPolicyEngineImplementor(config);
    } else {
      this.acquisitionUnitPolicyEngine = null;
    }
  }

  /**
   * There are two types of AccessPolicy query that we might want to handle, LIST: "Show me all records for which I can do RESTRICTION" and SINGLE: "Can I do RESTRICTION for resource X?"
   *
   * @param headers The request context headers -- used mainly to connect to FOLIO (or other "internal" services)
   * @param pr The policy restriction which we want to filter by
   * @param queryType Whether to return boolean queries for single use or filter queries for all records
   * @return A list of PolicySubqueries, either for boolean restriction or for filtering.
   * @throws PolicyEngineException -- the understanding is that within a request context this should be caught and return a 500
   */
  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType) throws PolicyEngineException {
    List<PolicySubquery> policySubqueries = new ArrayList<>();

    if (pr.equals(PolicyRestriction.CLAIM)) {
      throw new PolicyEngineException("getPolicySubqueries is not valid for PolicyRestriction.CLAIM", PolicyEngineException.INVALID_RESTRICTION);
    }

    if (acquisitionUnitPolicyEngine != null) {
      policySubqueries.addAll(acquisitionUnitPolicyEngine.getPolicySubqueries(headers, pr, queryType));
    }

    return policySubqueries;
  }


  // Helper method to get all valid policy IDs for a given policy restriction.
  // We handle CLAIM restrictions in this manner, canClaim should return the "PolicyId" list that a user can claim against a resource.
  // For Acq units these PolicyIds are acquisition unit IDs, for KI_GRANT they may be ownership strings "GBV%", "GBV/Rostock", "%" etc
  public List<AccessPolicyTypeIds> getPolicyIds(String[] headers, PolicyRestriction pr) throws PolicyEngineException {
    List<AccessPolicyTypeIds> policyIds = new ArrayList<>();

    if (acquisitionUnitPolicyEngine != null) {
      policyIds.addAll(acquisitionUnitPolicyEngine.getPolicyIds(headers, pr));
    }

    return policyIds;
  }
}
