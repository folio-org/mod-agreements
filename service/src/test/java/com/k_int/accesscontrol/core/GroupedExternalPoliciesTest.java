package com.k_int.accesscontrol.core;

import com.k_int.accesscontrol.core.http.responses.BasicPolicy;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.testresources.TestDomainAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.*;
  import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class GroupedExternalPoliciesTest {

  // =================================================================================
  // Test for fromAccessPolicyList
  // =================================================================================

  @Test
  void fromAccessPolicyList_shouldGroupPoliciesByType() {

    // Create mock DomainAccessPolicy instances implementing the interface
    List<DomainAccessPolicy> policyList = Arrays.asList(
      TestDomainAccessPolicy.createDomainAccessPolicy(AccessPolicyType.ACQ_UNIT, "acq1"),
      TestDomainAccessPolicy.createDomainAccessPolicy(AccessPolicyType.KI_GRANT, "grantA"),
      TestDomainAccessPolicy.createDomainAccessPolicy(AccessPolicyType.ACQ_UNIT, "acq2"), // Second policy for ACQ_UNIT
      TestDomainAccessPolicy.createDomainAccessPolicy(AccessPolicyType.KI_GRANT, "grantB")
    );

    // ACT
    List<GroupedExternalPolicies> result = GroupedExternalPolicies.fromAccessPolicyList(policyList);

    // ASSERT
    // Check size: Should have 2 groups (ACQ_UNIT and KI_GRANT)
    assertEquals(2, result.size(), "Should have exactly two policy groups.");

    // Check ACQ_UNIT group (should have 2 policies)
    GroupedExternalPolicies acqUnitGroup = result.stream()
      .filter(g -> g.getType() == AccessPolicyType.ACQ_UNIT)
      .findFirst()
      .orElseThrow(() -> new AssertionError("ACQ_UNIT group not found."));

    assertEquals(2, acqUnitGroup.getPolicies().size(), "ACQ_UNIT group should have 2 policies.");
    Set<String> acqUnitIds = acqUnitGroup.getPolicies().stream()
      .map(ExternalPolicy::getId)
      .collect(Collectors.toSet());
    assertTrue(acqUnitIds.containsAll(Arrays.asList("acq1", "acq2")), "ACQ_UNIT policies should contain acq1 and acq2.");
    assertEquals("POLICY_IDS_FOR_ACQ_UNIT", acqUnitGroup.getName(), "Name should be correctly generated.");

    // Check USER_GROUP group (should have 2 policies)
    GroupedExternalPolicies grantGroup = result.stream()
      .filter(g -> g.getType() == AccessPolicyType.KI_GRANT)
      .findFirst()
      .orElseThrow(() -> new AssertionError("KI_GRANT group not found."));

    assertEquals("POLICY_IDS_FOR_KI_GRANT", grantGroup.getName(), "Name should be correctly generated.");
    assertEquals(2, grantGroup.getPolicies().size(), "KI_GRANT group should have 2 policies.");
  }

  @Test
  void fromAccessPolicyList_shouldHandleEmptyList() {
    // ARRANGE
    Collection<DomainAccessPolicy> emptyList = Collections.emptyList();

    // ACT
    List<GroupedExternalPolicies> result = GroupedExternalPolicies.fromAccessPolicyList(emptyList);

    // ASSERT
    assertTrue(result.isEmpty(), "Result should be an empty list for empty input.");
  }

  // =================================================================================
  // Test for fromString
  // =================================================================================

  @Test
  void fromString_shouldParseAndGroupMultipleTypes() {
    String policyString = "ACQ_UNIT:acq1,KI_GRANT:groupA,ACQ_UNIT:acq2,ACQ_UNIT:acq3,ACQ_UNIT:acq4";

    List<GroupedExternalPolicies> result = GroupedExternalPolicies.fromString(policyString);

    assertEquals(2, result.size(), "Should have exactly two policy groups.");

    // Check ACQ_UNIT group
    GroupedExternalPolicies acqUnitGroup = result.stream()
      .filter(g -> g.getType() == AccessPolicyType.ACQ_UNIT)
      .findFirst()
      .orElseThrow(() -> new AssertionError("ACQ_UNIT group not found."));

    assertEquals(4, acqUnitGroup.getPolicies().size(), "ACQ_UNIT group should have 4 policies.");
  }

  @Test
  void fromString_shouldHandleNONEType() {
    String policyString = "NONE:NONE,ACQ_UNIT:acq1";

    List<GroupedExternalPolicies> result = GroupedExternalPolicies.fromString(policyString);

    assertEquals(2, result.size(), "Should have exactly two policy groups (NONE and ACQ_UNIT).");

    GroupedExternalPolicies noneGroup = result.stream()
      .filter(g -> g.getType() == AccessPolicyType.NONE)
      .findFirst()
      .orElseThrow(() -> new AssertionError("NONE group not found."));

    assertTrue(noneGroup.getPolicies().isEmpty(), "NONE group should have an empty policies list.");
    assertEquals("POLICY_IDS_FOR_NONE", noneGroup.getName());
  }

  @Test
  void fromString_shouldThrowExceptionForInvalidFormat() {
    String invalidString = "ACQ_UNIT-acq1,USER_GROUP:groupA"; // Invalid separator

    assertThrows(PolicyEngineException.class, () -> GroupedExternalPolicies.fromString(invalidString), "Should throw PolicyEngineException for incorrect format (missing ':').");
  }

  // =================================================================================
  // Test for convertListToPolicyLinkList
  // =================================================================================

  @Test
  void convertListToPolicyLinkList_shouldFlattenAndDecoratePolicies() {
    // BasicPolicy is the concrete implementation of ExternalPolicy
    BasicPolicy policy1 = BasicPolicy.builder().id("pol1").build();
    BasicPolicy policy2 = BasicPolicy.builder().id("pol2").build();
    BasicPolicy policy3 = BasicPolicy.builder().id("pol3").build();

    GroupedExternalPolicies groupA = GroupedExternalPolicies.builder()
      .type(AccessPolicyType.ACQ_UNIT)
      .name("Group A")
      .policies(Arrays.asList(policy1, policy2))
      .build();

    GroupedExternalPolicies groupB = GroupedExternalPolicies.builder()
      .type(AccessPolicyType.KI_GRANT)
      .name("Group B")
      .policies(Collections.singletonList(policy3))
      .build();

    Collection<GroupedExternalPolicies> groupedPolicies = Arrays.asList(groupA, groupB);

    // Note: Full class name used for PolicyLink since its import is not provided
    List<com.k_int.accesscontrol.core.http.bodies.PolicyLink> result =
      GroupedExternalPolicies.convertListToPolicyLinkList(groupedPolicies);

    // ASSERT
    assertEquals(3, result.size(), "The result should be a flat list containing all 3 policies.");

    // Check if the flattened list contains the correct links (Policy + Type)
    assertTrue(result.stream().anyMatch(link -> link.getPolicy() == policy1 && link.getType() == AccessPolicyType.ACQ_UNIT),
      "Policy 1 should be linked with ACQ_UNIT.");
    assertTrue(result.stream().anyMatch(link -> link.getPolicy() == policy2 && link.getType() == AccessPolicyType.ACQ_UNIT),
      "Policy 2 should be linked with ACQ_UNIT.");
    assertTrue(result.stream().anyMatch(link -> link.getPolicy() == policy3 && link.getType() == AccessPolicyType.KI_GRANT),
      "Policy 3 should be linked with KI_GRANT.");
  }
}