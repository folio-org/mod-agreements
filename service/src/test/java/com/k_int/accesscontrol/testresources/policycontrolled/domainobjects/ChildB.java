package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadataRestrictionMap;

@PolicyControlled(
  ownerColumn = "b_owner_column",
  ownerField = "owner",
  ownerClass = ChildA.class,
  resourceTableName = "b_table",
  resourceIdColumn = "b_id",
  resourceIdField = "id"
)
public class ChildB {
  String id;
  ChildA owner;

  public static PolicyControlledMetadataRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledMetadataRestrictionMap();
  }
}
