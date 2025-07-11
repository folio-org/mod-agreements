package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.k_int.accesscontrol.acqunits.model.AcquisitionUnitMembership;
import lombok.Data;

import java.util.List;

/**
 * Represents the response containing a list of acquisition unit memberships.
 * <p>
 * This class encapsulates the response structure for acquisition unit memberships,
 * including a list of memberships and the total number of records.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcquisitionUnitMembershipResponse {
  List<AcquisitionUnitMembership> acquisitionsUnitMemberships;
  int totalRecords;
}

