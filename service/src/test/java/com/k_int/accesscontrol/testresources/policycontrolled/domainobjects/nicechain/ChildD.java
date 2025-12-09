package com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.nicechain;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled;
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadataRestrictionMap;
import com.k_int.accesscontrol.core.policycontrolled.RestrictionMapEntry;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.PolicyControlledRestrictionTreeMap;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.SkeletonRestrictionTree;

@PolicyControlled(
  ownerColumn = "d_owner_column",
  ownerField = "owner",
  ownerClass = ChildC.class,
  resourceTableName = "d_table",
  resourceIdColumn = "d_id",
  resourceIdField = "id",
  hasStandaloneReadPolicies = true, // Allow separate READ restrictions on this level (THESE WILL BE ANDed)
  deleteRestrictionMapping = PolicyRestriction.UPDATE // Use UPDATE from ChildC to handle DELETE
)
public class ChildD {
  String id;
  ChildC owner;

  public static PolicyControlledMetadataRestrictionMap expectedRestrictionMap() {
    return new PolicyControlledMetadataRestrictionMap() {{
      put(
        PolicyRestriction.READ,
        RestrictionMapEntry.builder()
          .hasStandalonePolicies(true)
          .ownerRestriction(PolicyRestriction.READ)
          .build()
      );

      put(
        PolicyRestriction.DELETE,
        RestrictionMapEntry.builder()
          .hasStandalonePolicies(false)
          .ownerRestriction(PolicyRestriction.UPDATE)
          .build()
      );
    }};
  }

  public static PolicyControlledRestrictionTreeMap expectedRestrictionTreeMap() {
    return new PolicyControlledRestrictionTreeMap(){{
      put(
        PolicyRestriction.READ,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildD
          .restriction(PolicyRestriction.READ)
          .hasStandalonePolicies(true)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildC
              .restriction(PolicyRestriction.READ)
              .hasStandalonePolicies(true)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // ChildA
                  .restriction(PolicyRestriction.READ)
                  .hasStandalonePolicies(false)
                  .parent(
                    SkeletonRestrictionTree.builder()
                      .ownerLevel(3) // TopOwner
                      .restriction(PolicyRestriction.READ)
                      .hasStandalonePolicies(true)
                      .build()
                  )
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.UPDATE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildD
          .restriction(PolicyRestriction.UPDATE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildC
              .restriction(PolicyRestriction.UPDATE)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // ChildA
                  .restriction(PolicyRestriction.UPDATE)
                  .hasStandalonePolicies(false)
                  .parent(
                    SkeletonRestrictionTree.builder()
                      .ownerLevel(3) // TopOwner
                      .restriction(PolicyRestriction.UPDATE)
                      .hasStandalonePolicies(true)
                      .build()
                  )
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.CREATE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildD
          .restriction(PolicyRestriction.CREATE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildC
              .restriction(PolicyRestriction.CREATE)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // ChildA
                  .restriction(PolicyRestriction.UPDATE)
                  .hasStandalonePolicies(false)
                  .parent(
                    SkeletonRestrictionTree.builder()
                      .ownerLevel(3) // TopOwner
                      .restriction(PolicyRestriction.UPDATE)
                      .hasStandalonePolicies(true)
                      .build()
                  )
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.DELETE,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildD
          .restriction(PolicyRestriction.DELETE)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildC
              .restriction(PolicyRestriction.UPDATE)
              .hasStandalonePolicies(false)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // ChildA
                  .restriction(PolicyRestriction.UPDATE)
                  .hasStandalonePolicies(false)
                  .parent(
                    SkeletonRestrictionTree.builder()
                      .ownerLevel(3) // TopOwner
                      .restriction(PolicyRestriction.UPDATE)
                      .hasStandalonePolicies(true)
                      .build()
                  )
                  .build()
              )
              .build()
          )
          .build()
      );
      put(
        PolicyRestriction.APPLY_POLICIES,
        SkeletonRestrictionTree.builder()
          .ownerLevel(0) // ChildD
          .restriction(PolicyRestriction.APPLY_POLICIES)
          .hasStandalonePolicies(false)
          .parent(
            SkeletonRestrictionTree.builder()
              .ownerLevel(1) // ChildC
              .restriction(PolicyRestriction.APPLY_POLICIES)
              .hasStandalonePolicies(true)
              .parent(
                SkeletonRestrictionTree.builder()
                  .ownerLevel(2) // ChildA
                  .restriction(PolicyRestriction.NONE)
                  .hasStandalonePolicies(false)
                  .parent(
                    SkeletonRestrictionTree.builder()
                      .ownerLevel(3) // TopOwner
                      .restriction(PolicyRestriction.NONE)
                      .hasStandalonePolicies(false)
                      .build()
                  )
                  .build()
              )
              .build()
          )
          .build()
      );
    }};
  }
}
