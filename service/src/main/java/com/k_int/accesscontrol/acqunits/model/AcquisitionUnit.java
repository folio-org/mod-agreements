package com.k_int.accesscontrol.acqunits.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import javax.annotation.Nullable;

/**
 * Represents an acquisition unit in the access control system.
 * <p>
 * This class encapsulates the properties of an acquisition unit, including its ID,
 * name, description, and various protection flags that determine the permissions
 * for creating, deleting, reading, and updating the unit.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcquisitionUnit {
  String id;
  boolean isDeleted;
  String name;

  @Nullable
  String description;

  boolean protectCreate;
  boolean protectDelete;
  boolean protectRead;
  boolean protectUpdate;
}
