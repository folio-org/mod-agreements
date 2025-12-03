package com.k_int.accesscontrol.core.sql;

// FIXME This is a model under consideration, I am not thrilled with it at the moment.

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
