package com.k_int.accesscontrol.testresources;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledRestrictionMap;

@PolicyControlled(
  ownerColumn = "owner_column",
  ownerField = "owner",
  ownerClass = ChildA.class,
  resourceTableName = "b_table",
  resourceIdColumn = "b_id",
  resourceIdField = "id"
)
public class ChildB {
  String id;
  ChildA owner;

  public static PolicyControlledRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledRestrictionMap();
  }
}
