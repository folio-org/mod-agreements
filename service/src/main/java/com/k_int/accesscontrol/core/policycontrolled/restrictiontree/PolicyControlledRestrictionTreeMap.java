package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;

import java.util.HashMap;

/**
 * A map that associates {@link PolicyRestriction} keys with {@link EnrichedRestrictionTree} values.
 * <p>
 * This map is used to control how policy restrictions are structured in a tree format,
 * allowing for hierarchical representation of restrictions through their ownership chain.
 * </p>
 */
public class PolicyControlledRestrictionTreeMap extends HashMap<PolicyRestriction, SkeletonRestrictionTree> {
}
