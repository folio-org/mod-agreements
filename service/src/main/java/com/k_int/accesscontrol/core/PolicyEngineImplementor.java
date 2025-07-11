package com.k_int.accesscontrol.core;

import java.util.List;

/**
 * Interface for implementing policy engine logic.
 * <p>
 * This interface defines methods for generating policy subqueries based on
 * access policy restrictions, allowing for flexible integration with different
 * policy engines.
 * </p>
 */
public interface PolicyEngineImplementor {
  /**
   * Generates policy subqueries for the given policy restriction and query type.
   *
   * @param headers   the request context headers, used for FOLIO/internal service authentication
   * @param pr        the policy restriction to filter by
   * @param queryType the type of query to generate (SINGLE or LIST)
   * @return a list of {@link PolicySubquery} objects for the given restriction and query type
   * @throws PolicyEngineException if an error occurs while generating policy subqueries
   */
  List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType);
}
