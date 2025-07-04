package com.k_int.accesscontrol.acqunits.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import javax.annotation.Nullable;

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
