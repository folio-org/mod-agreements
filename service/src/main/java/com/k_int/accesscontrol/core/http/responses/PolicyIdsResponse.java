package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.AccessPolicyTypeIds;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.List;

/** Helper class to represent the response of access control policy IDs. An implementation can choose to ignore this and return their own API should they wish */
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
  @Nullable
  List<AccessPolicyTypeIds> applyPolicyIds;
}
