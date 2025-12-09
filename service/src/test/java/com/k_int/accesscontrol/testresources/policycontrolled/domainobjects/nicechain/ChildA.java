package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.nicechain;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadataRestrictionMap;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.PolicyControlledRestrictionTreeMap;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.SkeletonRestrictionTree;

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

  public static PolicyControlledRestrictionTreeMap expectedRestrictionTreeMap() {
    return new PolicyControlledRestrictionTreeMap(){{
      put(
        PolicyRestriction.READ,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildA
          .restriction(PolicyRestriction.READ)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // TopOwner
              .restriction(PolicyRestriction.READ)
              .hasStandalonePolicies(true)
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.UPDATE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildA
          .restriction(PolicyRestriction.UPDATE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // TopOwner
              .restriction(PolicyRestriction.UPDATE)
              .hasStandalonePolicies(true)
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.CREATE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildA
          .restriction(PolicyRestriction.CREATE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // TopOwner
              .restriction(PolicyRestriction.CREATE)
              .hasStandalonePolicies(true)
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.DELETE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildA
          .restriction(PolicyRestriction.DELETE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // TopOwner
              .restriction(PolicyRestriction.DELETE)
              .hasStandalonePolicies(true)
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.APPLY_POLICIES,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildA
          .restriction(PolicyRestriction.APPLY_POLICIES)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // TopOwner
              .restriction(PolicyRestriction.APPLY_POLICIES)
              .hasStandalonePolicies(true)
              .build()
          )
          .build()
      );
    }};
  }
}
