package com.k_int.accesscontrol.testresources;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledRestrictionMap;
import com.k_int.accesscontrol.core.policycontrolled.RestrictionMapEntry;

@PolicyControlled(
  ownerColumn = "d_owner_column",
  ownerField = "owner",
  ownerClass = ChildC.class,
  resourceTableName = "d_table",
  resourceIdColumn = "d_id",
  resourceIdField = "id",
  hasStandaloneReadPolicies = true, // Allow separate READ restrictions on this level (THESE WILL BE ANDed)
  deleteRestrictionMapping = PolicyRestriction.UPDATE // Use UPDATE from ChildC to handle DELETE
)
public class ChildD {
  String id;
  ChildC owner;

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
    }};
  }
}
