package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.cycle;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;

@PolicyControlled(
  ownerColumn = "a_owner_column",
  ownerField = "owner",
  ownerClass = CycleB.class,
  resourceTableName = "a_table",
  resourceIdColumn = "a_id",
  resourceIdField = "id"
)
public class CycleA {
  String id;
  CycleB owner;
}
