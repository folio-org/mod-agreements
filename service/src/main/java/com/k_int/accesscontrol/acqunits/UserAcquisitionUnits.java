package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnit;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * In order to perform Acquisition unit querying on the Policies within one of our modules we need 3 lists.
 * The below example is for READ but the equivalent is true for any of the restrictions.
  1.  **List A** – Acquisition units the user _is a member of_ and which _restrict READ_ access.
  2.  **List B** – Acquisition units that _do not restrict READ_ access for anyone.
  3.  **List C** – Acquisition units the user _is NOT a member of_ but _restrict READ_ access.
 */
@Data
@Builder
public class UserAcquisitionUnits {
  List<AcquisitionUnit> nonRestrictiveUnits;
  List<AcquisitionUnit> memberRestrictiveUnits;
  List<AcquisitionUnit> nonMemberRestrictiveUnits;

  public List<String> getNonRestrictiveUnitIds() {
    return nonRestrictiveUnits.stream().map(AcquisitionUnit::getId).toList();
  }

  public List<String> getMemberRestrictiveUnitIds() {
    return memberRestrictiveUnits.stream().map(AcquisitionUnit::getId).toList();
  }

  public List<String> getNonMemberRestrictiveUnitIds() {
    return nonMemberRestrictiveUnits.stream().map(AcquisitionUnit::getId).toList();
  }
}
