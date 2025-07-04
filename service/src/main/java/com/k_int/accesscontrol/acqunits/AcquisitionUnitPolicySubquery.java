package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.core.PolicySubquery;
import com.k_int.accesscontrol.core.PolicySubqueryParameters;
import lombok.Builder;
import lombok.Data;

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
            ap1.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ALIAS.#RESOURCE_ID_COLUMN_NAME AND
            ap1.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = '#RESOURCE_CLASS' AND
            ap1.#ACCESS_POLICY_ID_COLUMN_NAME IN (#NON_MEMBER_RESTRICTIVE_UNITS)
          LIMIT 1
        ) OR EXISTS (
          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME ap2
          WHERE
            ap2.#ACCESS_POLICY_TYPE_COLUMN_NAME = 'ACQ_UNIT' AND
            ap2.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ALIAS.#RESOURCE_ID_COLUMN_NAME AND
            ap2.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = '#RESOURCE_CLASS' AND
            ap2.#ACCESS_POLICY_ID_COLUMN_NAME IN (#MEMBER_RESTRICTIVE_UNITS)
          LIMIT 1
        )
      )
    """;

  public String getSql(PolicySubqueryParameters parameters) {
    // TODO is it worth having a "getIdsList" helper method?
    String memberRestrictiveUnits = String.join(",", userAcquisitionUnits.getMemberRestrictiveUnits().stream().map(AcquisitionUnit::getId).map(id -> "'" + id + "'").toList());
    String nonMemberRestrictiveUnits = String.join(",", userAcquisitionUnits.getNonMemberRestrictiveUnits().stream().map(AcquisitionUnit::getId).map(id -> "'" + id + "'").toList());

    return SQL_TEMPLATE
      .replaceAll("#ACCESS_POLICY_TABLE_NAME", parameters.getAccessPolicyTableName())
      .replaceAll("#ACCESS_POLICY_TYPE_COLUMN_NAME", parameters.getAccessPolicyTypeColumnName())
      .replaceAll("#ACCESS_POLICY_ID_COLUMN_NAME", parameters.getAccessPolicyIdColumnName())
      .replaceAll("#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME", parameters.getAccessPolicyResourceIdColumnName())
      .replaceAll("#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME", parameters.getAccessPolicyResourceClassColumnName())
      .replaceAll("#RESOURCE_ALIAS", parameters.getResourceAlias())
      .replaceAll("#RESOURCE_ID_COLUMN_NAME", parameters.getResourceIdColumnName())
      .replaceAll("#RESOURCE_CLASS", parameters.getResourceClass())
      .replaceAll("#MEMBER_RESTRICTIVE_UNITS", memberRestrictiveUnits)
      .replaceAll("#NON_MEMBER_RESTRICTIVE_UNITS", nonMemberRestrictiveUnits);
  }
}
