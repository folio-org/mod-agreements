package com.k_int.accesscontrol.acqunits.dto;

import com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction;
import lombok.Builder;
import lombok.Data;

/**
 * A data transfer object representing a pair of AcquisitionUnitRestriction and its protection status.
 */
@Builder
@Data
@SuppressWarnings("javadoc")
public class AcquisitionUnitRestrictionProtectedPair {
  /**
   * The acquisition unit restriction.
   * @param restriction the acquisition unit restriction
   * @return the acquisition unit restriction
   */
  AcquisitionUnitRestriction restriction;
  /**
   * Indicates whether the restriction is protected.
   * @param isProtected true if the restriction is protected, false otherwise
   * @return true if the restriction is protected, false otherwise
   */
  boolean isProtected;
}
