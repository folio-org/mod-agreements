package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.cycle;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;

@PolicyControlled(
  ownerColumn = "b_owner_column",
  ownerField = "owner",
  ownerClass = CycleC.class,
  resourceTableName = "b_table",
  resourceIdColumn = "b_id",
  resourceIdField = "id"
)
public class CycleB {
  String id;
  CycleC owner;
}
