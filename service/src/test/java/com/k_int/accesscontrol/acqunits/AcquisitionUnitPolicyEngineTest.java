package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitPolicy;
import com.k_int.accesscontrol.acqunits.subqueries.AcquisitionUnitPolicyEntitySubquery;
import com.k_int.accesscontrol.acqunits.subqueries.AcquisitionUnitPolicySubquery;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionsUnitSubset;
import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.main.PolicyEngineConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


// We mock AcquisitionUnitPolicyEngine... we need to do better than this though, this is an AI inspired test
public class AcquisitionUnitPolicyEngineTest {

  private AcquisitionsClient mockAcqClient;
  private AcquisitionUnitPolicyEngine engine;

  private final String[] HEADERS = new String[] {"X-Okapi-Tenant", "testTenant"};

  @BeforeEach
  void setup() {
    mockAcqClient = mock(AcquisitionsClient.class);
    PolicyEngineConfiguration mockPolicyEngineConfig = mock(PolicyEngineConfiguration.class);
    AcquisitionUnitPolicyEngineConfiguration mockAcqConfig = mock(AcquisitionUnitPolicyEngineConfiguration.class);

    when(mockPolicyEngineConfig.getAcquisitionUnitPolicyEngineConfiguration()).thenReturn(mockAcqConfig);

    // We default to not using external login for these tests (so handleLoginAndGetHeaders uses provided headers)
    when(mockAcqConfig.isExternalFolioLogin()).thenReturn(false);

    engine = new AcquisitionUnitPolicyEngine(mockAcqClient, mockPolicyEngineConfig);
  }

  @Test
  @DisplayName("getPolicyEntitySubqueries returns a list containing AcquisitionUnitPolicyEntitySubquery")
  void testGetPolicyEntitySubqueries() {
    List<com.k_int.accesscontrol.core.sql.PolicySubquery> subqueries = engine.getPolicyEntitySubqueries(HEADERS);

    assertNotNull(subqueries);
    assertFalse(subqueries.isEmpty());
    assertEquals(1, subqueries.size());
    assertInstanceOf(AcquisitionUnitPolicyEntitySubquery.class, subqueries.get(0));
  }

  @Test
  @DisplayName("getPolicySubqueries builds a subquery for given policy restriction")
  void testGetPolicySubqueries_basic() {
    PolicyRestriction pr = PolicyRestriction.READ;

    // Prepare a fake UserAcquisitionUnits which will be returned by the acquisitions client
    UserAcquisitionUnits uaus = mock(UserAcquisitionUnits.class);

    // The engine will call acqClient.getMappedRestrictionUserAcquisitionUnits with the mapped AcquisitionUnitRestriction
    // We don't need to construct that key here precisely — simply return a Map containing whatever key the engine will ask for.
    // Create a map keyed by the conversion result so test remains robust:
    com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction mapped = com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);
    Map<com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction, UserAcquisitionUnits> mockedMap = new HashMap<>();
    mockedMap.put(mapped, uaus);

    // stub the client call
    when(mockAcqClient.getMappedRestrictionUserAcquisitionUnits(
      any(), any(), any()))
      .thenReturn(mockedMap);

    // Call: third param is AccessPolicyQueryType; choose a value (replace with actual if different)
    com.k_int.accesscontrol.core.AccessPolicyQueryType queryType = AccessPolicyQueryType.LIST;

    Map<PolicyRestriction, List<com.k_int.accesscontrol.core.sql.PolicySubquery>> result =
      engine.getPolicySubqueries(HEADERS, Collections.singleton(pr), queryType);

    assertNotNull(result);
    assertTrue(result.containsKey(pr));
    List<com.k_int.accesscontrol.core.sql.PolicySubquery> subs = result.get(pr);
    assertNotNull(subs);
    assertEquals(1, subs.size());
    assertInstanceOf(AcquisitionUnitPolicySubquery.class, subs.get(0));
  }

  @Test
  @DisplayName("getRestrictionPolicies returns grouped external policies derived from UserAcquisitionUnits")
  void testGetRestrictionPolicies() {
    PolicyRestriction pr = PolicyRestriction.READ;
    com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction mapped = com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);

    // mock userAcquisitionUnits and its policy lists
    UserAcquisitionUnits uaus = mock(UserAcquisitionUnits.class);

    AcquisitionUnitPolicy ep1 = mock(AcquisitionUnitPolicy.class);
    when(ep1.getId()).thenReturn("policy-1");
    AcquisitionUnitPolicy ep2 = mock(AcquisitionUnitPolicy.class);
    when(ep2.getId()).thenReturn("policy-2");

    when(uaus.getMemberRestrictiveUnitPolicies()).thenReturn(List.of(ep1));
    when(uaus.getNonMemberNonRestrictiveUnitPolicies()).thenReturn(List.of(ep2));
    when(uaus.getMemberNonRestrictiveUnitPolicies()).thenReturn(Collections.emptyList());

    // stub acqClient.getUserAcquisitionUnits to return our mocked object
    when(mockAcqClient.getUserAcquisitionUnits(any(), eq(mapped), any()))
      .thenReturn(uaus);

    List<GroupedExternalPolicies> policies = engine.getRestrictionPolicies(HEADERS, pr);

    assertNotNull(policies);
    assertEquals(3, policies.size());

    // verify the first group corresponds to MEMBER_RESTRICTIVE with ep1
    GroupedExternalPolicies first = policies.get(0);
    assertEquals(AccessPolicyType.ACQ_UNIT, first.getType());
    assertEquals(UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE.toString(), first.getName());
    assertEquals(1, first.getPolicies().size());
    assertEquals("policy-1", first.getPolicies().get(0).getId());
  }

  @Test
  @DisplayName("arePoliciesValid returns true when policies are present in user acquisition units, false when not")
  void testArePoliciesValid_trueAndFalse() {
    PolicyRestriction pr = PolicyRestriction.READ;
    com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction mapped = com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);

    // Build two ExternalPolicy objects (these represent requested policy ids)
    ExternalPolicy requested1 = mock(ExternalPolicy.class);
    when(requested1.getId()).thenReturn("u1");
    ExternalPolicy requested2 = mock(ExternalPolicy.class);
    when(requested2.getId()).thenReturn("u2");

    GroupedExternalPolicies group = GroupedExternalPolicies.builder()
      .type(AccessPolicyType.ACQ_UNIT)
      .name("some")
      .policies(List.of(requested1, requested2))
      .build();

    // UserAcquisitionUnits where only "u1" exists
    UserAcquisitionUnits uaus = mock(UserAcquisitionUnits.class);

    com.k_int.accesscontrol.acqunits.model.AcquisitionUnit au1 = mock(com.k_int.accesscontrol.acqunits.model.AcquisitionUnit.class);
    when(au1.getId()).thenReturn("u1");
    // no unit for u2

    when(uaus.getMemberRestrictiveUnits()).thenReturn(List.of(au1));
    when(uaus.getNonRestrictiveUnits()).thenReturn(Collections.emptyList());

    // stub client to return uaus
    when(mockAcqClient.getUserAcquisitionUnits(any(), eq(mapped), any()))
      .thenReturn(uaus);

    // First assert -> false because u2 is missing
    boolean valid = engine.arePoliciesValid(HEADERS, pr, List.of(group));
    assertFalse(valid);

    // Now adapt userAcquisitionUnits to include both
    com.k_int.accesscontrol.acqunits.model.AcquisitionUnit au2 = mock(com.k_int.accesscontrol.acqunits.model.AcquisitionUnit.class);
    when(au2.getId()).thenReturn("u2");
    when(uaus.getNonRestrictiveUnits()).thenReturn(List.of(au2));

    // stub again (same mock returns mutated lists)
    when(mockAcqClient.getUserAcquisitionUnits(any(), eq(mapped), any()))
      .thenReturn(uaus);

    boolean validNow = engine.arePoliciesValid(HEADERS, pr, List.of(group));
    assertTrue(validNow);
  }

  @Test
  @DisplayName("enrichPolicies replaces ExternalPolicy entries with AcquisitionUnitPolicy returned from acqClient")
  void testEnrichPolicies_replacesWithAcqPolicy() {
    // Make two ExternalPolicy placeholders: pA (should be replaced), pB (no matching Acq policy - should remain the old object)
    ExternalPolicy pA = mock(ExternalPolicy.class);
    when(pA.getId()).thenReturn("policy-A");

    ExternalPolicy pB = mock(ExternalPolicy.class);
    when(pB.getId()).thenReturn("policy-B");

    GroupedExternalPolicies incoming = GroupedExternalPolicies.builder()
      .type(AccessPolicyType.ACQ_UNIT)
      .name("g")
      .policies(List.of(pA, pB))
      .build();

    // Prepare AcquisitionsClient to return a matching AcquisitionUnitPolicy for policy-A only
    AcquisitionUnitPolicy acqPolicyA = mock(AcquisitionUnitPolicy.class);
    when(acqPolicyA.getId()).thenReturn("policy-A");

    when(mockAcqClient.getAcquisitionUnitPolicies(any(), eq(Set.of("policy-A", "policy-B"))))
      .thenReturn(List.of(acqPolicyA)); // only one candidate returned

    List<GroupedExternalPolicies> res = engine.enrichPolicies(HEADERS, List.of(incoming));

    assertNotNull(res);
    assertEquals(1, res.size());

    GroupedExternalPolicies out = res.get(0);
    assertEquals(2, out.getPolicies().size());

    // First element should now be the acquisition unit policy (id "policy-A")
    assertInstanceOf(AcquisitionUnitPolicy.class, out.getPolicies().get(0));
    assertEquals("policy-A", out.getPolicies().get(0).getId());

    // Second element had no matching ACQ policy, so it should still be the original ExternalPolicy (mock)
    assertEquals("policy-B", out.getPolicies().get(1).getId());
  }

  // TODO Jack -- This test is heavily AIed, but seems to be a decent set of examples. I'm not convinced we should be
  //  mocking _everything_, or that this is actually testing everything it needs to.
}
