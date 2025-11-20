package com.k_int.accesscontrol.core.sql;

/**
 * Functional interface for providing owner IDs based on ownership levels and resource identifiers.
 * <p>
 * This interface defines a method to retrieve the owner ID corresponding to a specific level
 * in the ownership chain, given a leaf resource ID and the starting level of that resource.
 * </p>
 */
@FunctionalInterface
public interface OwnerIdProvider {
  /**
   * Retrieves the owner ID for a specified level in the ownership chain.
   * Designed to work with a {@link com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager}, but a framework layer can choose to implement it differently.
   * @param leafId     the identifier of the leaf resource (applies at startLevel)
   * @param ownerLevel the level in the ownership chain for which to retrieve the owner ID
   * @param startLevel the level at which the given leafId applies
   * @return the owner ID as a String
   */
  String apply(
    String leafId, // The "bottom" identifier, applied to level $startLevel
    int ownerLevel, // The level in the ownershipChain we want to return the id of
    int startLevel // The level at which the given resourceId applies. For CREATE we will want to start at level 1 with id Y, instead of level 0 with id X, since we don't have id in hand for create
  );
}
