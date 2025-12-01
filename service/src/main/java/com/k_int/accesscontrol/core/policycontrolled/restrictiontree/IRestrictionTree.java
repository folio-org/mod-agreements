package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;

import java.util.Set;

/**
 * This interface represents the path up a parent-child ownership tree for a given restriction.
 * Restriction A on the bottom node might map to Restriction B on the parent node, and so on.
 * Implementations of this are used generally with {@link com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager}
 * to represent
 */
public interface IRestrictionTree {
  /**
   * Retrieves the parent restriction tree node.
   *
   * @return the parent {@link IRestrictionTree} node
   */
  IRestrictionTree getParent();

  /**
   * Retrieves the owner level of the restriction tree.
   *
   * @return the owner level as an integer
   */
  int getOwnerLevel();

  /**
   * Retrieves the policy restriction associated with this tree node.
   *
   * @return the {@link PolicyRestriction} for this node
   */
  PolicyRestriction getRestriction();

  /**
   * Indicates whether this level has standalone policies, independent of the parent.
   *
   * @return true if standalone policies exist, false otherwise
   */
  boolean hasStandalonePolicies();

  /**
   * Gathers all policy restrictions from this node up to the root of the tree.
   *
   * @return a set of {@link PolicyRestriction} objects representing the ancestral restrictions
   */
  default Set<PolicyRestriction> getAncestralRestrictions() {
    Set<PolicyRestriction> restrictions = new java.util.HashSet<>();
    IRestrictionTree current = this;
    while (current != null) {
      restrictions.add(current.getRestriction());
      current = current.getParent();
    }
    return restrictions;
  }
}
