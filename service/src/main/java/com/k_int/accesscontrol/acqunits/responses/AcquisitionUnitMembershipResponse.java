package com.k_int.accesscontrol.acqunits.responses;

import lombok.Data;

import java.util.List;

/**
 * The HTTP response we get from acquisition units
 * */
@Data
public class AcquisitionUnitMembershipResponse {
  List<AcquisitionUnitMembership> acquisitionsUnitMemberships;
  int totalRecords;
}

