package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnit;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Grouping of acquisition unit IDs relevant to a specific user and restriction type.
 * Used to calculate the proper SQL conditions for access control. The unit sets are:
 * 1. `memberRestrictiveUnits`: Do restrict, but user is explicitly listed as a member.
 * 2. `nonMemberRestrictiveUnits`: Do restrict, and user is not a member — deny access.
 * These groupings are used to compute the allow/deny logic in generated SQL.
 */
@Data
@Builder
public class UserAcquisitionUnits {
  List<AcquisitionUnit> memberRestrictiveUnits;
  List<AcquisitionUnit> nonMemberRestrictiveUnits;
}
