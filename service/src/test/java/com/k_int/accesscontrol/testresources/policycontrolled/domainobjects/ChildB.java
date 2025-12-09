package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadataRestrictionMap;
import com.k_int.accesscontrol.core.policycontrolled.RestrictionMapEntry;

@PolicyControlled(
  ownerColumn = "b_owner_column",
  ownerField = "owner",
  ownerClass = ChildA.class,
  resourceTableName = "b_table",
  resourceIdColumn = "b_id",
  resourceIdField = "id",
  hasStandaloneReadPolicies = true
)
public class ChildB {
  String id;
  ChildA owner;

  public static PolicyControlledMetadataRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledMetadataRestrictionMap(){{
      put(PolicyRestriction.READ, RestrictionMapEntry.builder().ownerRestriction(PolicyRestriction.READ).hasStandalonePolicies(true).build());
    }};
  }
}
