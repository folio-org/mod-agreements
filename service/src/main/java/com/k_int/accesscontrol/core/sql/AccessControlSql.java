package com.k_int.accesscontrol.core.sql;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents a SQL query string along with its parameters and their types.
 * <p>
 * This class is used to encapsulate the SQL query, the parameters to be bound to the query,
 * and the types of those parameters. It is typically used in access control scenarios where
 * dynamic SQL queries are generated based on access policies.
 * </p>
 */
@Builder
@Getter
@EqualsAndHashCode
@ToString
@SuppressWarnings("javadoc")
public class AccessControlSql {
  /**
   * The SQL query string to be executed.
   * This string may contain placeholders for parameters that will be bound at runtime. (assumes ? as the placeholder)
   * @param sqlString The SQL query string to be executed.
   * @return The SQL query string to be executed.
   */
  final String sqlString;
  /**
   * An array of parameters to be bound to the SQL query.
   * These parameters correspond to the placeholders in the SQL string.
   * @param parameters An array of parameters to be bound to the SQL query.
   * @return An array of parameters to be bound to the SQL query.
   */
  final Object[] parameters;
  /**
   * An array of types for the parameters.
   * This is used to specify the SQL types of the parameters, which can be important for
   * correct binding and execution of the SQL query.
   * @param types An array of types for the parameters.
   * @return An array of types for the parameters.
   */
  final AccessControlSqlType[] types;

  /**
   * Combines a list of {@link AccessControlSql} fragments into a single composite SQL statement.
   *  This method performs the following operations:
   *  <ol>
   *    <li>Wraps each individual SQL fragment in parentheses to preserve operator precedence.</li>
   *    <li>Joins the fragments using the provided {@code operator} (e.g., " OR ", " AND ").</li>
   *    <li>Wraps the entire resulting string in an outer set of parentheses.</li>
   *    <li>Aggregates all parameters and types from the fragments into single arrays, maintaining order.</li>
   *  </ol>
   *
   *
   * <p>
   *  <strong>Example:</strong><br>
   *  Given fragments <code>["id = ?", "status = ?"]</code> and operator <code>" OR "</code>,<br>
   *  The resulting SQL string will be: <code>"((id = ?) OR (status = ?))"</code>
   * </p>
   *
   * @param fragments The list of {@link AccessControlSql} objects to combine. Must not be null or empty.
   * @param operator  The SQL operator used to join the fragments (e.g., " AND ", " OR ").
   * Ensure this string includes necessary spacing.
   * @return A new {@link AccessControlSql} object containing the combined SQL string and flattened parameters/types.
   * @throws IllegalArgumentException if the {@code fragments} list is null or empty.
   * @throws IllegalArgumentException if the operator (trimmed) does not case insensitively match "or" or "and"
   */
  public static AccessControlSql combineSqlSubqueries(Collection<AccessControlSql> fragments, String operator) {
    if (fragments == null || fragments.isEmpty()) {
      throw new IllegalArgumentException("Cannot combine an empty list of AccessControlSql statements");
    }

    if (
      !operator.trim().equalsIgnoreCase("or") &&
        !operator.trim().equalsIgnoreCase("and")
    ) {
      throw new IllegalArgumentException("Illegal SQL operator for joining subqueries: " + operator);
    }

    // Join strings: (sql1) OR (sql2), or (sql1) AND (sql2)
    String combinedSql = fragments.stream()
      .map(f -> "(" + f.getSqlString() + ")")
      .collect(Collectors.joining(operator));

    // Flatten parameters -- stream should stay sequential
    Object[] allParams = fragments.stream()
      .map(f -> Optional.ofNullable(f.getParameters()))
      .flatMap(Optional::stream)
      .flatMap(Arrays::stream)
      .toArray(Object[]::new);

    // Flatten types -- stream should stay sequential
    AccessControlSqlType[] allTypes = fragments.stream()
      .map(f -> Optional.ofNullable(f.getTypes()))
      .flatMap(Optional::stream)
      .flatMap(Arrays::stream)
      .toArray(AccessControlSqlType[]::new);

    return AccessControlSql.builder()
      .sqlString(combinedSql)
      .types(allTypes)
      .parameters(allParams)
      .build();
  }
}
