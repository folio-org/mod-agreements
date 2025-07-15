package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.core.sql.AccessControlSql;
import com.k_int.accesscontrol.core.sql.AccessControlSqlType;
import com.k_int.accesscontrol.core.sql.PolicySubquery;
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builder for generating SQL WHERE clause subqueries for acquisition unit access control.
 * This generates a reusable SQL pattern using string substitution, allowing
 * embedding of user-specific acquisition unit IDs and table/column names.
 * The generated SQL enforces the logic:
 * - If no policies exist for the resource → allow
 * - If only non-restrictive policies exist → allow
 * - If any restrictive policies exist for which the user *is* a member → allow
 * - If only restrictive policies exist for which the user is *not* a member → deny
 * All field names and table names are passed in as strings to ensure this can
 * be used from outside Hibernate contexts (e.g., raw SQL) or in future MN applications
 */
@Data
@Builder
public class AcquisitionUnitPolicySubquery implements PolicySubquery {
  UserAcquisitionUnits userAcquisitionUnits;
  AccessPolicyQueryType queryType;
  PolicyRestriction restriction;

    /* Original query was to find situations where
     *
     * No policy exists for the resource OR
     * Resource id is in the list of ids from policies which are restrictive and user is member OR
     * Resource id is in the list of ids from policies which are non-restrictive AND
     *    resource id is not in the list of ids from policies which are restrictive and user is not a member of
     *
     *
     * Policy | Restricts | Member
     * A      | YES       | YES
     * B      | YES       | NO
     * C      | NO        | (irrelevant)
     *
     * Resource | Policies | Can view
     * 1        |          | Yes
     * 2        | A        | Yes
     * 3        | AB       | Yes
     * 4        | C        | Yes
     * 5        | AC       | Yes
     * 6        | B        | No
     * 7        | BC       | No
     * 8        | AB       | Yes
     *
     *
     * New strategy, find all resources where
     * There are no restrictive policies for which user is not a member OR
     * There is at least one restrictive policy for which user IS a member
     */
    static final String SQL_TEMPLATE = """
      (
        NOT EXISTS (
          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME ap1
          WHERE
            ap1.#ACCESS_POLICY_TYPE_COLUMN_NAME = 'ACQ_UNIT' AND
            ap1.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ID_MATCH AND
            ap1.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
            ap1.#ACCESS_POLICY_ID_COLUMN_NAME IN (#NON_MEMBER_RESTRICTIVE_UNITS)
          LIMIT 1
        ) OR EXISTS (
          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME ap2
          WHERE
            ap2.#ACCESS_POLICY_TYPE_COLUMN_NAME = 'ACQ_UNIT' AND
            ap2.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ID_MATCH AND
            ap2.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
            ap2.#ACCESS_POLICY_ID_COLUMN_NAME IN (#MEMBER_RESTRICTIVE_UNITS)
          LIMIT 1
        )
      )
    """;

  public AccessControlSql getSql(PolicySubqueryParameters parameters) {
    // This shouldn't be possible thanks to PolicyEngine checks
    if (getRestriction() == PolicyRestriction.CLAIM) {
      throw new PolicyEngineException("AcquisitionUnitPolicySubquery::getSql is not valid for PolicyRestriction.CLAIM", PolicyEngineException.INVALID_RESTRICTION);
    }

    // Firstly we can handle the "CREATE" logic, since Acq Units never restricts CREATE
    if (getRestriction() == PolicyRestriction.CREATE) {
      return AccessControlSql.builder()
        .sqlString("1")
        .build();
    }

    // Spin up a list of all SQL parameters;
    List<String> allParameters = new ArrayList<>();
    // Keep track of their types as well.
    List<AccessControlSqlType> allTypes = new ArrayList<>();

    // For any other restriction we can set up our SQL subquery
    // TODO is it worth having a "getIdsList" helper method?
    List<String> memberRestrictiveUnits = userAcquisitionUnits.getMemberRestrictiveUnits().stream().map(AcquisitionUnit::getId).toList();
    List<String> nonMemberRestrictiveUnits = userAcquisitionUnits.getNonMemberRestrictiveUnits().stream().map(AcquisitionUnit::getId).toList();

    if (memberRestrictiveUnits.isEmpty()) memberRestrictiveUnits = List.of("this-is-a-made-up-impossible-value");
    if (nonMemberRestrictiveUnits.isEmpty()) nonMemberRestrictiveUnits = List.of("this-is-a-made-up-impossible-value");


    // If getQueryType() == LIST then we need #RESOURCEIDMATCH = {alias}.id (for hibernate), IF TYPE SINGLE THEN #RESOURCEIDMATCH = <UUID of resource>
    String resourceIdMatch = parameters.getResourceAlias() + "." + parameters.getResourceIdColumnName();
    if (getQueryType() == AccessPolicyQueryType.SINGLE) {
      if (parameters.getResourceId() == null) {
        throw new PolicyEngineException("PolicySubqueryParameters for AccessPolicyQueryType.SINGLE must include resourceId", PolicyEngineException.INVALID_QUERY_PARAMETERS);
      }
      resourceIdMatch = "?"; // We will bind an extra parameter for these, using parameters.getResourceId().
      //resourceIdMatch = "'" + parameters.getResourceId() + "'";
    }

    // Fill out the SQL parameters with the non-member and member restrictive units, as well as their types (STRING for all)

    // Resource id match for non member clause
    if (getQueryType() == AccessPolicyQueryType.SINGLE) {
      allParameters.add(parameters.getResourceId());
      allTypes.add(AccessControlSqlType.STRING); // Assuming resourceId is a UUID, we use STRING type.
    }

    // Mapping resource class for non member clause
    allParameters.add(parameters.getResourceClass());
    allTypes.add(AccessControlSqlType.STRING); // Assuming resourceId is a UUID, we use STRING type.

    allParameters.addAll(nonMemberRestrictiveUnits);
    allTypes.addAll(Collections.nCopies(nonMemberRestrictiveUnits.size(), AccessControlSqlType.STRING));

    // Resource id match for member clause
    if (getQueryType() == AccessPolicyQueryType.SINGLE) {
      allParameters.add(parameters.getResourceId());
      allTypes.add(AccessControlSqlType.STRING); // Assuming resourceId is a UUID, we use STRING type.
    }

    // Mapping resource class for non member clause
    allParameters.add(parameters.getResourceClass());
    allTypes.add(AccessControlSqlType.STRING); // Assuming resourceId is a UUID, we use STRING type.

    allParameters.addAll(memberRestrictiveUnits);
    allTypes.addAll(Collections.nCopies(memberRestrictiveUnits.size(), AccessControlSqlType.STRING));

    return AccessControlSql.builder()
      .sqlString(SQL_TEMPLATE
        .replaceAll("#ACCESS_POLICY_TABLE_NAME", parameters.getAccessPolicyTableName())
        .replaceAll("#ACCESS_POLICY_TYPE_COLUMN_NAME", parameters.getAccessPolicyTypeColumnName())
        .replaceAll("#ACCESS_POLICY_ID_COLUMN_NAME", parameters.getAccessPolicyIdColumnName())
        .replaceAll("#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME", parameters.getAccessPolicyResourceIdColumnName())
        .replaceAll("#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME", parameters.getAccessPolicyResourceClassColumnName())
        .replaceAll("#RESOURCE_ID_MATCH", resourceIdMatch)
        .replaceAll("#RESOURCE_CLASS", "?") // Map resource class to a parameter
        // Fill out "?" placeholders, one per id
        .replaceAll("#NON_MEMBER_RESTRICTIVE_UNITS", String.join(",", Collections.nCopies(nonMemberRestrictiveUnits.size(), "?")))
        .replaceAll("#MEMBER_RESTRICTIVE_UNITS", String.join(",", Collections.nCopies(memberRestrictiveUnits.size(), "?")))
      )
      .parameters(allParameters.toArray())
      .types(allTypes.toArray(new AccessControlSqlType[0]))
      .build();
  }
}
