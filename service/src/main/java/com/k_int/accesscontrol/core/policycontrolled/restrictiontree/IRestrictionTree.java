package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;

import java.util.Set;

/**
 * Represents a tree structure for policy restrictions, allowing traversal of parent-child relationships.
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
