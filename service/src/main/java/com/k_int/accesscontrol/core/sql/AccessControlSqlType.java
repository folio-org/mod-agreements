package com.k_int.accesscontrol.core.sql;

/**
 * An internal way to represent the type of SQL parameters used in access control queries.
 * Implementations can then map to eg Hibernate types or other ORM frameworks.
 */
public enum AccessControlSqlType {
  STRING,
  INTEGER,
  BOOLEAN,
  UUID, // If you're passing java.util.UUID objects
  DATE, // For java.sql.Date
  TIMESTAMP, // For java.sql.Timestamp
  BIG_DECIMAL, // For java.math.BigDecimal
  BYTE_ARRAY // For byte[]
}
