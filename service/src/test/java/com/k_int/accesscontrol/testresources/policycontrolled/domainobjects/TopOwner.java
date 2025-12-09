package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadataRestrictionMap;
import com.k_int.accesscontrol.core.policycontrolled.RestrictionMapEntry;

@PolicyControlled(
  resourceIdColumn = "top_owner_id",
  resourceIdField = "id",
  resourceTableName = "top_owner_table"
)
public class TopOwner {
  String id;
  ChildA childA;

  public static PolicyControlledMetadataRestrictionMap expectedRestrictionMap() {

    // As the top of the chain, it is expected that TopOwner's restriction map maps all restrictions to their proper
    // places and declares standalone policies for all
    return new PolicyControlledMetadataRestrictionMap(){{
      put(PolicyRestriction.READ, RestrictionMapEntry.builder().ownerRestriction(PolicyRestriction.READ).hasStandalonePolicies(true).build());
      put(PolicyRestriction.UPDATE, RestrictionMapEntry.builder().ownerRestriction(PolicyRestriction.UPDATE).hasStandalonePolicies(true).build());
      put(PolicyRestriction.DELETE, RestrictionMapEntry.builder().ownerRestriction(PolicyRestriction.DELETE).hasStandalonePolicies(true).build());
      put(PolicyRestriction.CREATE, RestrictionMapEntry.builder().ownerRestriction(PolicyRestriction.CREATE).hasStandalonePolicies(true).build());
      put(PolicyRestriction.APPLY_POLICIES, RestrictionMapEntry.builder().ownerRestriction(PolicyRestriction.APPLY_POLICIES).hasStandalonePolicies(true).build());
    }};
  }
}

