package com.k_int.accesscontrol.core.policycontrolled;

import com.k_int.accesscontrol.testresources.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
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
        List.of(TopOwner.expectedRestrictionMap()) // Expected restriction mappings are empty
      ),
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
          "ChildA",
          ChildA.class // Should be an ownership chain containing 2 items
        ),
        2, // Ownership chain size
        false, // Leaf != Root
        "owner_alias_0", // Root alias name (1 level deep)
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
        "owner_alias_1", // Root alias name (2 levels deep)
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
        "owner_alias_1", // Root alias name (2 levels deep)
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
        "owner_alias_2", // Root alias name (3 levels deep)
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
    List<PolicyControlledRestrictionMap> expectedRestrictionMaps
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
}
