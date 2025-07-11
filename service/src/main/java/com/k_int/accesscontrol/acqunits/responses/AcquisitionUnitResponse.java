package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import lombok.Data;

import java.util.List;

/**
 * Represents the response containing a list of acquisition units.
 * <p>
 * This class encapsulates the response structure for acquisition units,
 * including a list of acquisition units and the total number of records.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcquisitionUnitResponse {
  List<AcquisitionUnit> acquisitionsUnits;
  int totalRecords;
}

