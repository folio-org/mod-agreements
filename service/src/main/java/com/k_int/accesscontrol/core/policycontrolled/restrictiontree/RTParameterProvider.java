package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters;

/**
 * A functional interface that provides a method to generate
 * {@link PolicySubqueryParameters} for an {@link EnrichedRestrictionTree}
 * based on the owner level and a given PolicyRestriction.
 */
@FunctionalInterface
public interface RTParameterProvider {
  /**
   * Generates {@link PolicySubqueryParameters} based on the provided owner level and restriction.
   *
   * @param ownerLevel the level of ownership in the restriction tree
   * @param restriction the policy restriction for which to generate parameters
   * @return the generated {@link PolicySubqueryParameters}
   */
  PolicySubqueryParameters apply(
    int ownerLevel,
    PolicyRestriction restriction
  );
}
