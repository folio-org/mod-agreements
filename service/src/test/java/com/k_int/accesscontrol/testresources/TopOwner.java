package com.k_int.accesscontrol.testresources;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledRestrictionMap;

@PolicyControlled(
  resourceIdColumn = "top_owner_id",
  resourceIdField = "id",
  resourceTableName = "top_owner_table"
)
public class TopOwner {
  String id;
  ChildA childA;

  public static PolicyControlledRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledRestrictionMap();
  }
}

