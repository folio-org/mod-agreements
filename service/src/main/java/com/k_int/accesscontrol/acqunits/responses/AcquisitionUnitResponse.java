package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import lombok.Data;

import java.util.List;

/**
 * The HTTP response we get from acquisition units
 * */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcquisitionUnitResponse {
  List<AcquisitionUnit> acquisitionsUnits;
  int totalRecords;
}

