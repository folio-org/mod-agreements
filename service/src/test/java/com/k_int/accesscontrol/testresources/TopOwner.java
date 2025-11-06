package com.k_int.accesscontrol.testresources;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledRestrictionMap;

@PolicyControlled(
  resourceIdColumn = "top_owner_id",
  resourceIdField = "id"
)
public class TopOwner {
  String id;
  ChildA childA;

  public static PolicyControlledRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledRestrictionMap();
  }
}

