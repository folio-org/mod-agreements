package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.core.PolicyRestriction;
import lombok.Getter;

// FIXME could restriction be something more generic at the grails level?

// For ACQ_UNITS
// read -> restrict read for rows
// CREATE -> restrict the ability to create FOR this acquisition unit ... sort of a "claim"
// UPDATE -> restrict the ability to update a row
// DELETE -> restrict the ability to delete a row

/**
 * Enum representing the internal acquisition unit flags used in FOLIO (e.g., `protectRead`, `protectUpdate`).
 * Each restriction maps to a policy restriction and its corresponding JSON key in FOLIO responses.
 * Also provides a helper for converting from the high-level `PolicyRestriction`.
 */
@Getter
public enum AcquisitionUnitRestriction {
  READ("protectRead"),
  CREATE("protectCreate"),
  UPDATE("protectUpdate"),
  DELETE("protectDelete"),

  NONE("none");

  private final String restrictionAccessor;

  // enum constructor - cannot be public or protected
  AcquisitionUnitRestriction(String restrictionAccessor)
  {
    this.restrictionAccessor = restrictionAccessor;
  }

  public static AcquisitionUnitRestriction getRestrictionFromPolicyRestriction(PolicyRestriction pr) {
    return switch (pr) {
      case READ -> AcquisitionUnitRestriction.READ;
      case CLAIM -> AcquisitionUnitRestriction.CREATE;
      case DELETE -> AcquisitionUnitRestriction.DELETE;
      case UPDATE -> AcquisitionUnitRestriction.UPDATE;
      default -> AcquisitionUnitRestriction.NONE;
    };
  }
}
