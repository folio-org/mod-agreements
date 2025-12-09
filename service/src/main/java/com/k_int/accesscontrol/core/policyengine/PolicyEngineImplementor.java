package com.k_int.accesscontrol.core.policyengine;

import com.k_int.accesscontrol.core.AccessPolicyQueryType;
import com.k_int.accesscontrol.core.GroupedExternalPolicies;
import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.sql.PolicySubquery;

import java.util.*;

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
   * Generates a mapping of policy restrictions to their corresponding policy subqueries. This is an extension of the
   * getPolicySubqueries method, allowing for the fetching of multiple restriction subqueries at once for efficiency reasons.
   *
   * @param headers      the request context headers, used for FOLIO/internal service authentication
   * @param restrictions the collection of policy restrictions to process
   * @param queryType    the type of query to generate (SINGLE or LIST)
   * @return a map where each key is a {@link PolicyRestriction} and the value is a list of {@link PolicySubquery} objects
   *         associated with that restriction
   */
  Map<PolicyRestriction, List<PolicySubquery>> getPolicySubqueries(String[] headers, Collection<PolicyRestriction> restrictions, AccessPolicyQueryType queryType);

  /**
   * Retrieves a list of access policy IDs grouped by their type for the given policy restriction.
   *
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @param pr      the policy restriction to filter by
   * @return a list of {@link GroupedExternalPolicies} containing policy IDs grouped by type
   */
  List<GroupedExternalPolicies> getRestrictionPolicies(String[] headers, PolicyRestriction pr);

  /**
   * Validates the policy IDs against the provided headers and policy restriction.
   *
   * @param headers   the request context headers, used for FOLIO/internal service authentication
   * @param pr        the policy restriction to filter by
   * @param policies the list of policy IDs to validate
   * @return true if all policy IDs are valid, false otherwise
   */
  boolean arePoliciesValid(String[] headers, PolicyRestriction pr, List<GroupedExternalPolicies> policies);

  /**
   * Enriches the policy information from the `id` provided
   * (Likely incoming is a {@link com.k_int.accesscontrol.core.http.responses.BasicPolicy} implementation)
   *
   * @param policies a list of AccessPolicy objects to enrich, it will use the "type" and the "policy.id" fields to enrich
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @return A list of AccessPolicy objects with all policy information provided
   */
  List<GroupedExternalPolicies> enrichPolicies(String[] headers, List<GroupedExternalPolicies> policies);

  /**
   * Generates a list of {@link PolicySubquery} objects representing subqueries
   * required to filter {@link com.k_int.accesscontrol.core.DomainAccessPolicy} objects for a given {@link com.k_int.accesscontrol.core.sql.PolicySubqueryParameters}
   *  <p>
   * This method is typically used to construct SQL subqueries for access control entity resources.
   * The resourceAlias parameter is expected to control the named alias for the AccessControlEntity table,
   * and the expectation is that each subquery will be ORed together in a WHERE clause.
   * The subqueries should focus on whether an DomainAccessPolicy object applies to a given resource given the resourceId in parameters
   * If resourceId is null then it is expected that each implementation should provide ALL DomainAccessPolicies.
   * </p>
   *
   * @param headers the request context headers, used for authentication and context propagation
   * @return a list of {@link PolicySubquery} objects for use on a DB query for DomainAccessPolicy objects
   */
  List<PolicySubquery> getPolicyEntitySubqueries(String[] headers);
}
