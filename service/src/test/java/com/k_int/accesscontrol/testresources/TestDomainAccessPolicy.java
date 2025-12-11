package com.k_int.accesscontrol.testresources;

import com.k_int.accesscontrol.core.AccessPolicyType;
import com.k_int.accesscontrol.core.DomainAccessPolicy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDomainAccessPolicy {

  /**
   * Helper for creating a mock implementation of the DomainAccessPolicy interface.
   */
  public static DomainAccessPolicy createDomainAccessPolicy(AccessPolicyType type, String policyId) {
    DomainAccessPolicy mockPolicy = mock(DomainAccessPolicy.class);
    // Configure the mocked methods required by GroupedExternalPolicies.fromAccessPolicyList
    when(mockPolicy.getType()).thenReturn(type);
    when(mockPolicy.getPolicyId()).thenReturn(policyId);
    return mockPolicy;
  }
}
