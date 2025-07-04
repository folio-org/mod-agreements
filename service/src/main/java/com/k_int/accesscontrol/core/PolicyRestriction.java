package com.k_int.accesscontrol.core;

/**
 * Enum of supported restriction types applicable to policies.
 * These map to high-level actions that can be restricted:
 * - `READ`: Read access to a resource
 * - `CREATE`: Creation of new records (not used with ACQ_UNIT)
 * - `CLAIM`: Associating a policy to an existing resource
 * - `UPDATE`: Modifying an existing record
 * - `DELETE`: Removing a record
 */
public enum PolicyRestriction {
  READ,
  CREATE, // acq_units can't restrict CREATE in general
  CLAIM, // in ACQ_UNITS world would be allowing the linking of an acquisition unit to a resource via a policy
  UPDATE,
  DELETE
}
