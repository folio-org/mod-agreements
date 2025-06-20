package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * The HTTP response we get from acquisition units
 * */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcquisitionUnitMembershipResponse {
  List<AcquisitionUnitMembership> acquisitionsUnitMemberships;
  int totalRecords;
}

