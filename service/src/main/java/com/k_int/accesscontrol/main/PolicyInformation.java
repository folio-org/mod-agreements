package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.UserAcquisitionUnits;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Builder
@Data
public class PolicyInformation {
  // For now we return the raw data from policies. This should probably be a named set of sql subqueries for the different AccessPolicyTypes
  @Nullable
  UserAcquisitionUnits userAcquisitionUnits;

  @Nullable
  String acquisitionSql; // Not 100% sure this is the right shape
}
