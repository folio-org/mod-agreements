package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * An immutable implementation of IRestrictionTree which sets up the skeleton structure without parameters or subqueries, such that it might be reused
 */
@Getter
@Builder
@EqualsAndHashCode
@ToString
@SuppressWarnings("javadoc")
public class SkeletonRestrictionTree implements IRestrictionTree {
  /**
   * The parent restriction tree node. Null if this is the root.
   * @param parent the parent restriction tree node
   * @return the parent restriction tree node
   */
  final SkeletonRestrictionTree parent;

  /**
   * Tracks how "far up" the hierarchy this {@code RestrictionTree} instance is. <br/>
   * 0: Represents the "base class" or leaf resource. <br/>
   * 1: Represents the direct owner of the base class. <br/>
   * 2: Represents the owner of the owner of the base class, and so on.
   * @param ownerLevel the level in the hierarchy for this policy controlled object
   * @return the level in the hierarchy for this policy controlled object
   */
  final int ownerLevel;

  /**
   * The policy restriction represented by this tree node.
   * @param restriction the policy restriction
   * @return the policy restriction
   */
  final PolicyRestriction restriction;

  /**
   * Indicates whether this level has standalone policies, independent of the parent.
   * @param hasStandalonePolicies true if standalone policies exist, false otherwise
   * @return true if standalone policies exist, false otherwise
   */
  @Builder.Default
  @Accessors(fluent = true)
  final boolean hasStandalonePolicies = false;
}
