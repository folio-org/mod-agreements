package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.core.PolicyRestriction;
import lombok.Getter;

// FIXME could restriction be something more generic at the grails level?

// For ACQ_UNITS
// read -> restrict read for rows
// CREATE -> restrict the ability to create FOR this acquisition unit ... sort of a "claim"
// UPDATE -> restrict the ability to update a row
// DELETE -> restrict the ability to delete a row

@Getter
public enum Restriction {
  READ("protectRead"),
  CREATE("protectCreate"),
  UPDATE("protectUpdate"),
  DELETE("protectDelete"),

  NONE("none");

  private final String restrictionAccessor;

  // enum constructor - cannot be public or protected
  Restriction(String restrictionAccessor)
  {
    this.restrictionAccessor = restrictionAccessor;
  }

  Restriction getRestrictionFromPolicyRestriction(PolicyRestriction pr) {
    switch (pr) {
      case READ:
        return Restriction.READ;
      case CLAIM:
        return Restriction.CREATE;
      case DELETE:
        return Restriction.DELETE;
      case UPDATE:
        return Restriction.UPDATE;
      default:
        return Restriction.NONE;
    }
  }
}
