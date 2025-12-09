package com.k_int.accesscontrol.core.policycontrolled;

import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.PolicyControlledRestrictionTreeMap;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.core.sql.AccessControlSql;
import com.k_int.accesscontrol.core.sql.AccessControlSqlType;
import com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.cycle.CycleA;
import com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.cycle.CycleB;
import com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.cycle.CycleC;
import com.k_int.accesscontrol.testresources.policycontrolled.domainobjects.nicechain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyControlledManagerTest {
  private static Stream<Arguments> ownershipChainGetArguments() {
    return Stream.of(
      // An unhandled RuntimeException (should be wrapped in FolioClientException)
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "TopOwner",
          TopOwner.class // Should be a single ownership chain
        ),
        1, // Ownership chain size
        true, // Leaf == Root
        null, // Root alias name (no need for alias since this is top level)
        List.of(TopOwner.expectedRestrictionMap())
      ),
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "ChildA",
          ChildA.class // Should be an ownership chain containing 2 items
        ),
        2, // Ownership chain size
        false, // Leaf != Root
        "owner_alias_1", // Root alias name (1 level deep)
        List.of(
          ChildA.expectedRestrictionMap(),
          TopOwner.expectedRestrictionMap()
        ) // Expected restriction mappings are empty
      ),
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "ChildB",
          ChildB.class // Should be a ownership chain containing 3 items
        ),
        3, // Ownership chain size
        false, // Leaf != Root
        "owner_alias_2", // Root alias name (2 levels deep)
        List.of(
          ChildB.expectedRestrictionMap(),
          ChildA.expectedRestrictionMap(),
          TopOwner.expectedRestrictionMap()
        ) // Expected restriction mappings are empty
      ),
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "ChildC",
          ChildC.class // Should be a ownership chain containing 3 items (bypassing B since it's parallel to it)
        ),
        3, // Ownership chain size
        false, // Leaf != Root
        "owner_alias_2", // Root alias name (2 levels deep)
        List.of(
          ChildC.expectedRestrictionMap(),
          ChildA.expectedRestrictionMap(),
          TopOwner.expectedRestrictionMap()
        ) // Expected restriction mappings are empty
      ),
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "ChildD",
          ChildD.class // Should be an ownership chain containing 3 items (bypassing B since it's parallel to it)
        ),
        4, // Ownership chain size
        false, // Leaf != Root
        "owner_alias_3", // Root alias name (3 levels deep)
        List.of(
          ChildD.expectedRestrictionMap(),
          ChildC.expectedRestrictionMap(),
          ChildA.expectedRestrictionMap(),
          TopOwner.expectedRestrictionMap()
        ) // Expected restriction mappings are empty
      )
    );
  }

  @ParameterizedTest(name = "{0}") // Use the named first argument and ignore the rest for test case naming
  @MethodSource("ownershipChainGetArguments")
  @DisplayName("PolicyControlledManager generates expected ownership chain")
  void ownershipChainTest(
    Class<?> theClass,
    int chainSize,
    boolean leafEqualsRoot,
    String rootAliasName,
    List<PolicyControlledMetadataRestrictionMap> expectedRestrictionMaps
  ) {
    // WHEN
    // Set up for TopOwner
    PolicyControlledManager pcm = new PolicyControlledManager(theClass);

    // THEN
    assertEquals(chainSize, pcm.getOwnershipChain().size());

    if (leafEqualsRoot) {
      assertEquals(pcm.getLeafPolicyControlledMetadata(), pcm.getRootPolicyControlledMetadata());
    } else {
      assertNotEquals(pcm.getLeafPolicyControlledMetadata(), pcm.getRootPolicyControlledMetadata());
    }

    assertEquals(rootAliasName, pcm.getRootPolicyControlledMetadata().getAliasName());

    for(int i=0; i < expectedRestrictionMaps.size(); i++) {
      assertEquals(expectedRestrictionMaps.get(i), pcm.getOwnershipChain().get(i).getRestrictionMap());
    }
  }

  private static Stream<Arguments> idSqlGetArguments() {
    return Stream.of(
      // An unhandled RuntimeException (should be wrapped in FolioClientException)
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "TopOwner",
          TopOwner.class // Should be a single ownership chain
        ),
        List.of(0, 1), // Owner levels to test standalone
        List.of(
          AccessControlSql.builder()
            .sqlString("SELECT ? as id;")
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder().build() // If illegal, test throws
        ), // Expected SQL List
        List.of(NullPointerException.class, PolicyEngineException.class), // Expected throws list
        Collections.emptyList(), // Owner level/start level pairs to test
        Collections.emptyList(), // Expected SQL list
        Collections.emptyList() // Expected throws list
      ),
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "ChildD",
          ChildD.class // Should be an ownership chain containing 3 items (bypassing B since it's parallel to it)
        ),
        List.of(1, 2, 3), // Owner levels to test standalone
        List.of(
          AccessControlSql.builder()
            .sqlString(
              "SELECT t1.c_id as id " +
                "FROM d_table as t0 " +
                "JOIN c_table AS t1 ON t0.d_owner_column = t1.c_id " +
                "WHERE t0.d_id = ?;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder()
            .sqlString(
              "SELECT t2.a_id as id " +
                "FROM d_table as t0 " +
                "JOIN c_table AS t1 ON t0.d_owner_column = t1.c_id " +
                "JOIN a_table AS t2 ON t1.c_owner_column = t2.a_id " +
                "WHERE t0.d_id = ?;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder()
            .sqlString(
              "SELECT t3.top_owner_id as id " +
                "FROM d_table as t0 " +
                "JOIN c_table AS t1 ON t0.d_owner_column = t1.c_id " +
                "JOIN a_table AS t2 ON t1.c_owner_column = t2.a_id " +
                "JOIN top_owner_table AS t3 ON t2.a_owner_column = t3.top_owner_id " +
                "WHERE t0.d_id = ?;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build()
        ), // Expected SQL List
        List.of(NullPointerException.class, NullPointerException.class, NullPointerException.class), // Expected throws list
        List.of(
          List.of(3,1),
          List.of(2,1),
          List.of(2,2),
          List.of(1,2)
        ), // Owner level/start level pairs to test
        List.of(
          AccessControlSql.builder()
            .sqlString(
              "SELECT t3.top_owner_id as id " +
                "FROM c_table as t1 " +
                "JOIN a_table AS t2 ON t1.c_owner_column = t2.a_id " +
                "JOIN top_owner_table AS t3 ON t2.a_owner_column = t3.top_owner_id " +
                "WHERE t1.c_id = ?;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder()
            .sqlString(
              "SELECT t2.a_id as id " +
                "FROM c_table as t1 " +
                "JOIN a_table AS t2 ON t1.c_owner_column = t2.a_id " +
                "WHERE t1.c_id = ?;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder()
            .sqlString(
              "SELECT ? as id;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build()
        ), // Expected SQL list
        List.of(
          NullPointerException.class,
          NullPointerException.class,
          NullPointerException.class,
          IllegalArgumentException.class
        ) // Expected throws list
      ),
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "ChildA",
          ChildA.class // Should be an ownership chain containing 3 items (bypassing B since it's parallel to it)
        ),
        List.of(0, 1, 2), // Owner levels to test standalone
        List.of(
          AccessControlSql.builder()
            .sqlString(
              "SELECT ? as id;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder()
            .sqlString(
              "SELECT t1.top_owner_id as id " +
                "FROM a_table as t0 " +
                "JOIN top_owner_table AS t1 ON t0.a_owner_column = t1.top_owner_id " +
                "WHERE t0.a_id = ?;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder().build()
        ), // Expected SQL List
        List.of(NullPointerException.class, NullPointerException.class, PolicyEngineException.class), // Expected throws list
        List.of(
          List.of(2,1),
          List.of(1,1),
          List.of(1,0),
          List.of(0,1)
        ), // Owner level/start level pairs to test
        List.of(
          AccessControlSql.builder().build(),
          AccessControlSql.builder()
            .sqlString(
              "SELECT ? as id;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
          AccessControlSql.builder()
            .sqlString(
              "SELECT t1.top_owner_id as id " +
                "FROM a_table as t0 " +
                "JOIN top_owner_table AS t1 ON t0.a_owner_column = t1.top_owner_id " +
                "WHERE t0.a_id = ?;"
            )
            .parameters(List.of("theId").toArray())
            .types(List.of(AccessControlSqlType.STRING).toArray(AccessControlSqlType[]::new))
            .build(),
            AccessControlSql.builder().build()
          ), // Expected SQL list
        List.of(
          PolicyEngineException.class,
          NullPointerException.class,
          NullPointerException.class,
          IllegalArgumentException.class
        ) // Expected throws list
      )
    );
  }

  @ParameterizedTest(name = "{0}") // Use the named first argument and ignore the rest for test case naming
  @MethodSource("idSqlGetArguments")
  @DisplayName("PolicyControlledManager generates expected id SQL")
  void pcmIdSQLTest(
    Class<?> theClass,
    List<Integer> ownerLevelTestList,
    List<AccessControlSql> expectedSqlList,
    List<Class<? extends Exception>> expectedThrowsList,
    List<List<Integer>> ownerStartPairList,
    List<AccessControlSql> pairExpectedSqlList,
    List<Class<? extends Exception>> pairExpectedThrowsList
  ) {
    // WHEN
    PolicyControlledManager pcm = new PolicyControlledManager(theClass);

    for (int i = 0; i < ownerLevelTestList.size(); i++) {
      final Integer fetcher = ownerLevelTestList.get(i);
      if (expectedThrowsList.get(i) != NullPointerException.class) { // Treat nullPointerException as "null" for test purposes
        assertThrows(
          expectedThrowsList.get(i),
          () -> pcm.getOwnerIdSql("theId", fetcher)
        );
      } else {
        assertEquals(
          expectedSqlList.get(i),
          pcm.getOwnerIdSql("theId", fetcher)
        );
      }
    }

    for (int i = 0; i < ownerStartPairList.size(); i++) {
      final List<Integer> fetcher = ownerStartPairList.get(i);

      if (pairExpectedThrowsList.get(i) != NullPointerException.class) {
        assertThrows(
          pairExpectedThrowsList.get(i),
          () -> pcm.getOwnerIdSql("theId", fetcher.get(0), fetcher.get(1))
        );
      } else {
        assertEquals(
          pairExpectedSqlList.get(i),
          pcm.getOwnerIdSql("theId", fetcher.get(0), fetcher.get(1))
        );
      }
    }
  }

  private static Stream<Arguments> restrictionTreeArguments() {
    return Stream.of(
      Arguments.of(
        Named.of("ChildD", ChildD.class),
        ChildD.expectedRestrictionTreeMap()
      ),
      Arguments.of(
        Named.of("ChildC", ChildC.class),
        ChildC.expectedRestrictionTreeMap()
      ),
      Arguments.of(
        Named.of("ChildB", ChildB.class),
        ChildB.expectedRestrictionTreeMap()
      ),
      Arguments.of(
        Named.of("ChildA", ChildA.class),
        ChildA.expectedRestrictionTreeMap()
      ),
      Arguments.of(
        Named.of("TopOwner", TopOwner.class),
        TopOwner.expectedRestrictionTreeMap()
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("restrictionTreeArguments")
  @DisplayName("PolicyControlledManager builds expected SkeletonRestrictionTree")
  void restrictionTreeTest(
    Class<?> theClass,
    PolicyControlledRestrictionTreeMap expectedTreeMap
  ) {
    PolicyControlledManager pcm = new PolicyControlledManager(theClass);
    PolicyControlledRestrictionTreeMap actualTreeMap = pcm.getRestrictionTreeMap();

    assertEquals(expectedTreeMap, actualTreeMap);
  }

  @Test
  @DisplayName("PolicyControlledManager detects control cycles")
  void cycleTest() {
    assertThrows(
      IllegalStateException.class,
      () -> new PolicyControlledManager(CycleA.class)
    );

    assertThrows(
      IllegalStateException.class,
      () -> new PolicyControlledManager(CycleB.class)
    );

    assertThrows(
      IllegalStateException.class,
      () -> new PolicyControlledManager(CycleC.class)
    );
  }

  @Test
  @DisplayName("Utility methods (hasOwners, getNonLeafOwnershipChain) operate correctly")
  void utilityMethodsTest() {
    // ARRANGE 1: TopOwner (Chain size 1, Leaf=Root, NO owners)
    PolicyControlledManager pcmRoot = new PolicyControlledManager(TopOwner.class);

    // ASSERT 1
    assertFalse(pcmRoot.hasOwners(), "TopOwner should report hasOwners=false.");
    assertTrue(pcmRoot.getNonLeafOwnershipChain().isEmpty(), "TopOwner should have an empty non-leaf chain.");

    // ARRANGE 2: ChildA (Chain size 2, Leaf!=Root, HAS owners)
    PolicyControlledManager pcmChild = new PolicyControlledManager(ChildA.class);

    // ASSERT 2
    assertTrue(pcmChild.hasOwners(), "ChildA should report hasOwners=true.");
    assertEquals(1, pcmChild.getNonLeafOwnershipChain().size(), "ChildA should have one non-leaf owner (TopOwner).");
    assertEquals(TopOwner.class.getCanonicalName(), pcmChild.getNonLeafOwnershipChain().get(0).getResourceClassName(), "Non-leaf chain should contain the owner.");

    // ARRANGE 3: ChildD (Chain size 4)
    PolicyControlledManager pcmDeep = new PolicyControlledManager(ChildD.class);

    // ASSERT 3
    assertEquals(3, pcmDeep.getNonLeafOwnershipChain().size(), "ChildD should have three non-leaf owners.");
  }
}
