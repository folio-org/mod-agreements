package com.k_int.accesscontrol.acqunits.subqueries;

import com.k_int.accesscontrol.core.AccessPolicyType;
import com.k_int.accesscontrol.core.sql.AccessControlSql;
import com.k_int.accesscontrol.core.sql.AccessControlSqlType;
import com.k_int.accesscontrol.core.sql.PolicySubquery;
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PolicySubquery} implementation that produces the SQL fragment used to
 * retrieve {@link com.k_int.accesscontrol.core.DomainAccessPolicy} rows that are linked
 * to a resource via acquisition-unit–based policies.
 *
 * <p>This subquery is responsible for generating a simple predicate that filters
 * DomainAccessPolicy records by:
 *
 * <ul>
 *   <li>the resource class,</li>
 *   <li>the resource ID, and</li>
 *   <li>the access policy type {@link AccessPolicyType#ACQ_UNIT}.</li>
 * </ul>
 *
 * <p>The resulting SQL fragment can be injected into a larger query that fetches
 * all policy entities for a given resource at a specific ownership level. The
 * {@code parameters.resourceAlias} identifies the aliased
 * <code>DomainAccessPolicy</code> table that the clause must refer to.
 *
 * <p>This class forms part of the acquisition-unit policy engine and is used
 * whenever the {@link com.k_int.accesscontrol.main.PolicyEngine} is configured to operate with acquisition
 * units enabled.
 */
public class AcquisitionUnitPolicyEntitySubquery implements PolicySubquery {
  // Using the resource alias for the entities.
  String POLICY_ENTITY_SQL_TEMPLATE = """
    /* #CLAUSE_LABEL */
    (
      #ACCESS_POLICY_ENTITY_ALIAS.#ACCESS_POLICY_ENTITY_RESOURCE_CLASS_COLUMN = #RESOURCE_CLASS AND
      #ACCESS_POLICY_ENTITY_ALIAS.#ACCESS_POLICY_ENTITY_RESOURCE_ID_COLUMN = #RESOURCE_ID AND
      #ACCESS_POLICY_ENTITY_ALIAS.#ACCESS_POLICY_ENTITY_TYPE_COLUMN = #ACQUISITION_UNIT
    )
  """;

  /**
   * Generates the {@link AccessControlSql} representation of the acquisition-unit
   * policy entity subquery for the given parameters.
   *
   * <p>This method expands the static SQL template, inserting the appropriate table
   * aliases and column names from the provided {@link PolicySubqueryParameters}.
   * All dynamic values—specifically the resource class, the resource ID, and the
   * policy type ({@link AccessPolicyType#ACQ_UNIT})—are bound as JDBC parameters
   * using <code>?</code> placeholders to ensure safe and consistent SQL generation.
   *
   * <p>The resulting SQL fragment forms a single predicate that selects
   * {@link com.k_int.accesscontrol.core.DomainAccessPolicy} rows associated with a
   * resource through acquisition-unit–based access rules. The predicate has the form:
   *
   * <pre>
   *   (policy_alias.resource_class = ?
   *    AND policy_alias.resource_id = ?
   *    AND policy_alias.type = ?)
   * </pre>
   *
   * <p>The returned {@link AccessControlSql} bundles:
   * <ul>
   *   <li>the rendered SQL string,</li>
   *   <li>the ordered list of parameter values, and</li>
   *   <li>the corresponding {@link AccessControlSqlType} entries.</li>
   * </ul>
   *
   * <p>The {@code clauseLabel} is injected into the SQL as a comment to aid query
   * introspection and debugging, especially when multiple policy subqueries are
   * combined by the enclosing policy engine.
   *
   * @param parameters contextual information describing the resource, aliasing,
   *                   and column names used to construct the SQL fragment
   * @return a fully-bound {@link AccessControlSql} object containing the SQL
   *         predicate and its typed parameters
   */
  @Override
  public AccessControlSql getSql(PolicySubqueryParameters parameters) {
    // Spin up a list of all SQL parameters;
    List<String> allParameters = new ArrayList<>();
    // Keep track of their types as well.
    List<AccessControlSqlType> allTypes = new ArrayList<>();

    String clauseLabel = "ACQUISITION UNIT POLICIES SUBQUERY FOR " + PolicySubquery.sqlSafe(parameters.getResourceClass(), "resource class") + " ON " + PolicySubquery.uuidSafe(parameters.getResourceId(), "resource id");


    // Set up SQL String initially
    String sqlString = POLICY_ENTITY_SQL_TEMPLATE
      .replaceAll("#CLAUSE_LABEL", clauseLabel)
      .replaceAll("#ACCESS_POLICY_ENTITY_ALIAS", PolicySubquery.sqlSafe(parameters.getResourceAlias(), "domain access policy alias"))
      .replaceAll("#ACCESS_POLICY_ENTITY_RESOURCE_CLASS_COLUMN", PolicySubquery.sqlSafe(parameters.getAccessPolicyResourceClassColumnName(), "domain access policy resource class column"))
      .replaceAll("#ACCESS_POLICY_ENTITY_RESOURCE_ID_COLUMN", PolicySubquery.sqlSafe(parameters.getAccessPolicyResourceIdColumnName(), "domain access policy resource id column"))
      .replaceAll("#ACCESS_POLICY_ENTITY_TYPE_COLUMN", PolicySubquery.sqlSafe(parameters.getAccessPolicyTypeColumnName(), "domain access policy type column"));

    // Now parameter setup
    /* **** Resource class **** */
    sqlString = sqlString.replaceAll("#RESOURCE_CLASS", "?");
    allParameters.add(parameters.getResourceClass());
    allTypes.add(AccessControlSqlType.STRING);

    /* **** Resource id **** */
    sqlString = sqlString.replaceAll("#RESOURCE_ID", "?");
    allParameters.add(parameters.getResourceId());
    allTypes.add(AccessControlSqlType.STRING);

    /* **** Access policy type **** */
    sqlString = sqlString.replaceAll("#ACQUISITION_UNIT", "?");
    allParameters.add(AccessPolicyType.ACQ_UNIT.toString());
    allTypes.add(AccessControlSqlType.STRING);

    return AccessControlSql.builder()
      .sqlString(sqlString)
      .types(allTypes.toArray(new AccessControlSqlType[0]))
      .parameters(allParameters.toArray())
      .build();
  }
}
