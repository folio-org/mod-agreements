package com.k_int.accesscontrol.core;

import java.util.List;

public interface PolicyEngineImplementor {
  /**
   * There are two types of AccessPolicy query that we might want to handle, subQueries: "Show me all records for which I can do RESTRICTION" and booleanQueries: "Can I do RESTRICTION for resource X?"
   *
   * @param headers The request context headers -- used mainly to connect to FOLIO (or other "internal" services)
   * @param pr The policy restriction which we want to filter by
   * @return A list of PolicySubqueries, either for boolean restriction or for filtering.
   * @throws PolicyEngineException
   */
  List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType);
}
