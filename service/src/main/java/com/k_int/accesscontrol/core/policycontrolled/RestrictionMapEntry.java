package com.k_int.accesscontrol.core.policycontrolled;

import com.k_int.accesscontrol.core.PolicyRestriction;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * Represents an entry in the policy restriction map, associating a policy restriction
 * with its owner restriction and a flag indicating if standalone policies exist.
 */
@Builder
@SuppressWarnings("javadoc")
@Getter
@ToString
@EqualsAndHashCode
public class RestrictionMapEntry {
  /**
   * The owner restriction associated with this entry.
   *
   * @param ownerRestriction the owner restriction for this entry
   * @return this builder instance
   */
  PolicyRestriction ownerRestriction;


  /**
   * Indicates whether this level has standalone policies, independent of the parent.
   *
   * @param hasStandalonePolicies true if standalone policies exist
   * @return this builder instance
   */
  @Builder.Default
  @Accessors(fluent = true)
  boolean hasStandalonePolicies = false;
}
