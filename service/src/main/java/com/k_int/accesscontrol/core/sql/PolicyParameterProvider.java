package com.k_int.accesscontrol.core.sql;

import java.util.function.BiFunction;

/**
 * Functional interface for providing parameters needed to generate policy subqueries.
 * <p>
 * This interface is a specialized BiFunction that takes the ownership level and
 * resource ID and returns the corresponding PolicySubqueryParameters.
 * </p>
 *
 * @see PolicySubqueryParameters
 */
@FunctionalInterface
public interface PolicyParameterProvider extends
  BiFunction<String, Integer, PolicySubqueryParameters> {

  /**
   * Retrieves the parameters required for generating a policy subquery.
   * Designed to work with a {@link com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager}, but a framework layer can choose to implement it differently.
   *
   * @param resourceId The ID of the resource to which the policies apply. Can be {@code null} for LIST queries.
   * @param ownerLevel the level in the ownership chain
   * @return a {@link PolicySubqueryParameters} object containing the necessary parameters
   */
  @Override
  PolicySubqueryParameters apply(String resourceId, Integer ownerLevel);
}