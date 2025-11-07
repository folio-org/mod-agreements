package com.k_int.accesscontrol.testresources;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledRestrictionMap;
import com.k_int.accesscontrol.core.policycontrolled.RestrictionMapEntry;

@PolicyControlled(
  ownerColumn = "owner_column",
  ownerField = "owner",
  ownerClass = ChildA.class,
  resourceTableName = "c_table",
  resourceIdColumn = "c_id",
  resourceIdField = "id",
  hasStandaloneReadPolicies = true, // Allow separate READ restrictions on this level (THESE WILL BE ANDed)
  createRestrictionMapping = PolicyRestriction.UPDATE, // Use UPDATE from ChildA to handle CREATE
  deleteRestrictionMapping = PolicyRestriction.UPDATE, // Use UPDATE from ChildA to handle DELETE
  applyPoliciesRestrictionMapping = PolicyRestriction.NONE, // Do not pay attention to ChildA's APPLY_POLICIES
  hasStandaloneApplyPolicies = true // Allow for separate APPLY_POLICIES to be at this level
)
public class ChildC {
  String id;
  ChildA owner;

  public static PolicyControlledRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledRestrictionMap() {{
      put(
        PolicyRestriction.READ,
        RestrictionMapEntry.builder()
          .hasStandalonePolicies(true)
          .ownerRestriction(PolicyRestriction.READ)
          .build()
      );


      put(
        PolicyRestriction.DELETE,
        RestrictionMapEntry.builder()
          .hasStandalonePolicies(false)
          .ownerRestriction(PolicyRestriction.UPDATE)
          .build()
      );

      put(
        PolicyRestriction.CREATE,
        RestrictionMapEntry.builder()
          .hasStandalonePolicies(false)
          .ownerRestriction(PolicyRestriction.UPDATE)
          .build()
      );

      put(
        PolicyRestriction.APPLY_POLICIES,
        RestrictionMapEntry.builder()
          .hasStandalonePolicies(true)
          .ownerRestriction(PolicyRestriction.NONE)
          .build()
      );
    }};
  }
}
