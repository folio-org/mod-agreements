package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcquisitionUnitMembership {
  String acquisitionsUnitId;
  String id;
  String userId;
}
