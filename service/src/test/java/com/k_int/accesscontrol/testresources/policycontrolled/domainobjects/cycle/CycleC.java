package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.cycle;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;

@PolicyControlled(
  ownerColumn = "c_owner_column",
  ownerField = "owner",
  ownerClass = CycleA.class,
  resourceTableName = "c_table",
  resourceIdColumn = "c_id",
  resourceIdField = "id"
)
public class CycleC {
  String id;
  CycleA owner;
}
