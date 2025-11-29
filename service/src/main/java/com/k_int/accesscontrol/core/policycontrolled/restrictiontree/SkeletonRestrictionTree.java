package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * An immutable implementation of IRestrictionTree which sets up the skeleton structure without parameters or subqueries, such that it might be reused
 */
@Getter
@Builder
@SuppressWarnings("javadoc")
public class SkeletonRestrictionTree implements IRestrictionTree {
  /**
   * The parent restriction tree node. Null if this is the root.
   * @return the parent restriction tree node
   */
  final SkeletonRestrictionTree parent;

  /**
   * Tracks how "far up" the hierarchy this {@code RestrictionTree} instance is. <br/>
   * 0: Represents the "base class" or leaf resource. <br/>
   * 1: Represents the direct owner of the base class. <br/>
   * 2: Represents the owner of the owner of the base class, and so on.
   * @return the level in the hierarchy for this policy controlled object
   */
  final int ownerLevel;

  /**
   * The policy restriction represented by this tree node.
   * @return the policy restriction
   */
  final PolicyRestriction restriction;

  /**
   * Indicates whether this level has standalone policies, independent of the parent.
   * @return true if standalone policies exist, false otherwise
   */
  @Builder.Default
  @Accessors(fluent = true)
  final boolean hasStandalonePolicies = false;
}
