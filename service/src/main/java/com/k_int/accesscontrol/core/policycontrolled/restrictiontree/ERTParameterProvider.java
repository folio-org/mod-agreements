package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

// FIXME This is a model under consideration, I am not thrilled with it at the moment.

import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters;

/**
 * An interface concerned with providing the methods needed to "Enrich" a RestrictionTree with
 * PolicySubqueryParameter objects at each level. This is used in conjunction with the policyEngine enrichRestrictionTree
 * method.
 */
public interface ERTParameterProvider {

  /**
   * A method to take in an ownerLevel from a {@link com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager}
   * ownership chain and provide {@link PolicySubqueryParameters} for a given level of it.
   * @param ownerLevel The level in the PolicyControlledManager ownership chain to get parameters for
   * @return The parameters to assign to some PolicySubquery SQL at a given level in an {@link IRestrictionTree}
   */
  PolicySubqueryParameters provideParameters(int ownerLevel);
}
