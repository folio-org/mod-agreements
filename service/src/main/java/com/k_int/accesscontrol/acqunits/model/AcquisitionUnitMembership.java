package com.k_int.accesscontrol.acqunits.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Represents a membership of a user in an acquisition unit.
 * <p>
 * This class encapsulates the relationship between a user and an acquisition unit,
 * including the IDs of both the acquisition unit and the user.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcquisitionUnitMembership {
  String acquisitionsUnitId;
  String id;
  String userId;
}
