package com.k_int.accesscontrol.core.responses;

import com.k_int.accesscontrol.core.AccessPolicyTypeIds;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.List;

@Data
@Builder
public class PolicyIdsResponse {
  @Nullable
  List<AccessPolicyTypeIds> readPolicyIds;
  @Nullable
  List<AccessPolicyTypeIds> updatePolicyIds;
  @Nullable
  List<AccessPolicyTypeIds> createPolicyIds;
  @Nullable
  List<AccessPolicyTypeIds> deletePolicyIds;
  @Nullable
  List<AccessPolicyTypeIds> claimPolicyIds;
}
