package com.k_int.accesscontrol.acqunits.responses;

import lombok.Data;

@Data
public class AcquisitionUnit {
  String id;
  boolean isDeleted;
  String name;

  boolean protectCreate;
  boolean protectDelete;
  boolean protectRead;
  boolean protectUpdate;
}
