package com.k_int.accesscontrol.core.sql;

// I'm not 100% on board with this model, as I think it creates a bit of a difficult to understand coupling between
// the framework layer and the engine. The intention is that given a level in the ownership tree, the parameters from
// the framework layer can and will change. These parameters are needed within structures like the enrichedRestrictionTree
// To avoid having to build that tree in every framework layer, this interface allows the building logic to be offloaded
// onto the engine, with the framework layer instead only responsible for handling the owner level -> parameters logic.

import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.IRestrictionTree;

/**
 * An interface concerned with providing the methods needed to provide PolicySubqueryParameter objects at each level
 * of an ownership chain. This is used in conjunction with the policyEngine enrichRestrictionTree method.
 */
public interface OwnerLevelParameterProvider {

  /**
   * A method to take in an ownerLevel from a {@link com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager}
   * ownership chain and provide {@link PolicySubqueryParameters} for a given level of it.
   * @param ownerLevel The level in the PolicyControlledManager ownership chain to get parameters for
   * @return The parameters to assign to some PolicySubquery SQL at a given level in an {@link IRestrictionTree}
   */
  PolicySubqueryParameters provideParameters(int ownerLevel);
}
