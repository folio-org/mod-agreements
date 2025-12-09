package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects;

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadataRestrictionMap;

@PolicyControlled(
  ownerColumn = "a_owner_column",
  ownerField = "owner",
  ownerClass = TopOwner.class,
  resourceTableName = "a_table",
  resourceIdColumn = "a_id",
  resourceIdField = "id"
)
public class ChildA {
  String id;
  TopOwner owner;
  ChildB childB;
  ChildC childC;

  public static PolicyControlledMetadataRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledMetadataRestrictionMap();
  }
}
