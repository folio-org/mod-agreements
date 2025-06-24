package com.k_int.accesscontrol.core;

// I don't know if we need these.
public enum PolicyRestriction {
  READ,
  CREATE, // acq_units can't restrict CREATE in general
  CLAIM, // in ACQ_UNITS world would be allowing the linking of an acquisition unit to a resource via a policy
  UPDATE,
  DELETE
}
