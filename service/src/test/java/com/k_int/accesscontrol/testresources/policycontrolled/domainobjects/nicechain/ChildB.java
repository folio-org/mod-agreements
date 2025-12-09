package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.nicechain;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadataRestrictionMap;
import com.k_int.accesscontrol.core.policycontrolled.RestrictionMapEntry;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.PolicyControlledRestrictionTreeMap;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.SkeletonRestrictionTree;

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

  public static PolicyControlledRestrictionTreeMap expectedRestrictionTreeMap() {
    return new PolicyControlledRestrictionTreeMap(){{
      put(
        PolicyRestriction.READ,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildB
          .restriction(PolicyRestriction.READ)
          .hasStandalonePolicies(true)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildA
              .restriction(PolicyRestriction.READ)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // TopOwner
                  .restriction(PolicyRestriction.READ)
                  .hasStandalonePolicies(true)
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.UPDATE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildB
          .restriction(PolicyRestriction.UPDATE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildA
              .restriction(PolicyRestriction.UPDATE)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // TopOwner
                  .restriction(PolicyRestriction.UPDATE)
                  .hasStandalonePolicies(true)
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.CREATE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildB
          .restriction(PolicyRestriction.CREATE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildA
              .restriction(PolicyRestriction.CREATE)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // TopOwner
                  .restriction(PolicyRestriction.CREATE)
                  .hasStandalonePolicies(true)
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.DELETE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildB
          .restriction(PolicyRestriction.DELETE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildA
              .restriction(PolicyRestriction.DELETE)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // TopOwner
                  .restriction(PolicyRestriction.DELETE)
                  .hasStandalonePolicies(true)
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.APPLY_POLICIES,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildB
          .restriction(PolicyRestriction.APPLY_POLICIES)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildA
              .restriction(PolicyRestriction.APPLY_POLICIES)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // TopOwner
                  .restriction(PolicyRestriction.APPLY_POLICIES)
                  .hasStandalonePolicies(true)
                  .build()
              )
              .build()
          )
          .build()
      );
    }};
  }
}
