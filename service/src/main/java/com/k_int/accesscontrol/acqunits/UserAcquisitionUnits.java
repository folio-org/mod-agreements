package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents acquisition units associated with a user, grouped by restriction type.
 * <p>
 * Used to determine access control logic based on whether the user is a member of
 * restrictive acquisition units.
 * </p>
 */
@Data
@Builder
public class UserAcquisitionUnits {
  /**
   * Acquisition units that restrict access, but the user is explicitly listed as a member.
   * These units allow access for the user.
   */
  List<AcquisitionUnit> memberRestrictiveUnits;

  /**
   * Acquisition units that restrict access and the user is not a member.
   * These units deny access for the user.
   */
  List<AcquisitionUnit> nonMemberRestrictiveUnits;
}
