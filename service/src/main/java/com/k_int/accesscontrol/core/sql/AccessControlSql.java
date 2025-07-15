package com.k_int.accesscontrol.core.sql;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a SQL query string along with its parameters and their types.
 * <p>
 * This class is used to encapsulate the SQL query, the parameters to be bound to the query,
 * and the types of those parameters. It is typically used in access control scenarios where
 * dynamic SQL queries are generated based on access policies.
 * </p>
 */
@Builder
@Data
public class AccessControlSql {
  String sqlString;
  Object[] parameters;
  AccessControlSqlType[] types;
}
