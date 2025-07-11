package com.k_int.accesscontrol.core;

/**
 * Enum of supported restriction types applicable to policies within the access control system.
 * These types map to high-level actions that can be restricted on a resource or for a system
 */
public enum PolicyRestriction {
  /**
   * Represents the restriction on read access to a resource.
   * If this restriction is active, a user may not be able to view the resource's data.
   */
  READ,
  /**
   * Represents the restriction on the creation of new records.
   * <p>Note: The mapping from `AcquisitionUnitRestriction.CREATE` is not always a direct relationship.
   * In the context of Acquisition Units, {@code PolicyRestriction.CREATE} maps to the
   * `AcquisitionUnitRestriction.NONE` flag, since creation is never restricted by ACQ_UNITs.</p>
   */
  CREATE,
  /**
   * Represents the restriction on associating a policy (e.g., an acquisition unit).
   * <p>Note: The mapping from `AcquisitionUnitRestriction.CLAIM` is not always a direct relationship.
   * In the context of Acquisition Units, {@code PolicyRestriction.CLAIM} maps to the
   * `AcquisitionUnitRestriction.CREATE` flag.</p>
   */
  CLAIM,
  /**
   * Represents the restriction on modifying an existing record.
   * If this restriction is active, a user may not be able to make changes to the resource.
   */
  UPDATE,
  /**
   * Represents the restriction on removing a record.
   * If this restriction is active, a user may not be able to delete the resource.
   */
  DELETE
}
