package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.sql.PolicySubquery;

import java.util.List;

/**
 * A functional interface that provides a method to generate
 * a list of {@link PolicySubquery} for an {@link EnrichedRestrictionTree}
 * based on the owner level and a given PolicyRestriction.
 */
@FunctionalInterface
public interface RTSubqueriesProvider {
  /**
   * Generates a list of {@link PolicySubquery} based on the provided owner level and restriction.
   *
   * @param ownerLevel the level of ownership in the restriction tree
   * @param restriction the policy restriction for which to generate subqueries
   * @return the generated list of {@link PolicySubquery}
   */
  List<PolicySubquery> apply(
    int ownerLevel,
    PolicyRestriction restriction
  );
}
