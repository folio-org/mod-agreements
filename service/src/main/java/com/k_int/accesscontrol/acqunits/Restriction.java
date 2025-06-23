package com.k_int.accesscontrol.acqunits;

import lombok.Getter;

@Getter
public enum Restriction {
  READ("protectRead"),
  CREATE("protectCreate"),
  UPDATE("protectUpdate"),
  DELETE("protectDelete");

  private final String restrictionAccessor;

  // enum constructor - cannot be public or protected
  Restriction(String restrictionAccessor)
  {
    this.restrictionAccessor = restrictionAccessor;
  }
}
