package com.k_int.accesscontrol.acqunits;

// FIXME not convinced by this structure yet to be honest
public class AcquisitionUnitPolicySubquery {
  static final String SQL_TEMPLATE = """
      (
        NOT EXISTS (
          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME ap1
          WHERE
            ap1.#ACCESS_POLICY_TYPE = 'ACQ_UNIT' AND
            ap1.#ACCESS_POLICY_RESOURCE_ID = #RESOURCE_ALIAS.#RESOURCE_ID AND
            ap1.#ACCESS_POLICY_RESOURCE_CLASS = '#RESOURCE_CLASS'
        ) OR (
          #RESOURCE_ALIAS.#RESOURCE_ID IN (
            SELECT ap2.#ACCESS_POLICY_RESOURCE_ID
            FROM #ACCESS_POLICY_TABLE_NAME ap2
            WHERE
                ap2.#ACCESS_POLICY_TYPE = 'ACQ_UNIT' AND
                ap2.#ACCESS_POLICY_RESOURCE_CLASS = '#RESOURCE_CLASS' AND
                ap2.#ACCESS_POLICY_ID IN (#MEMBER_RESTRICTIVE_UNITS)
          )
        ) OR (
          #RESOURCE_ALIAS.#RESOURCE_ID IN (
            SELECT ap3.#ACCESS_POLICY_RESOURCE_ID
            FROM #ACCESS_POLICY_TABLE_NAME ap3
            WHERE
                ap3.#ACCESS_POLICY_TYPE = 'ACQ_UNIT' AND
                ap3.#ACCESS_POLICY_RESOURCE_CLASS = '#RESOURCE_CLASS' AND
                ap3.#ACCESS_POLICY_ID IN (#NON_RESTRICTIVE_UNITS)
          ) AND (
            #RESOURCE_ALIAS.#RESOURCE_ID NOT IN (
              SELECT ap4.#ACCESS_POLICY_RESOURCE_ID
              FROM #ACCESS_POLICY_TABLE_NAME ap4
              WHERE
                  ap4.#ACCESS_POLICY_TYPE = 'ACQ_UNIT' AND
                  ap4.#ACCESS_POLICY_RESOURCE_CLASS = '#RESOURCE_CLASS' AND
                  ap4.#ACCESS_POLICY_ID IN (#NON_MEMBER_RESTRICTIVE_UNITS)
            )
          )
        )
      )
    """;

  public static String returnSql(
    String access_policy_table_name,
    String access_policy_type,
    String access_policy_id,
    String access_policy_resource_id,
    String access_policy_resource_class,
    String resource_alias,
    String resource_id,
    String resource_class,
    String non_restrictive_units,
    String member_restrictive_units,
    String non_member_restrictive_units
  ) {
    return SQL_TEMPLATE
      .replaceAll("#ACCESS_POLICY_TABLE_NAME", access_policy_table_name)
      .replaceAll("#ACCESS_POLICY_TYPE", access_policy_type)
      .replaceAll("#ACCESS_POLICY_ID", access_policy_id)
      .replaceAll("#ACCESS_POLICY_RESOURCE_ID", access_policy_resource_id)
      .replaceAll("#ACCESS_POLICY_RESOURCE_CLASS", access_policy_resource_class)
      .replaceAll("#RESOURCE_ALIAS", resource_alias)
      .replaceAll("#RESOURCE_ID", resource_id)
      .replaceAll("#RESOURCE_CLASS", resource_class)
      .replaceAll("#NON_RESTRICTIVE_UNITS", non_restrictive_units)
      .replaceAll("#MEMBER_RESTRICTIVE_UNITS", member_restrictive_units)
      .replaceAll("#NON_MEMBER_RESTRICTIVE_UNITS", non_member_restrictive_units);
  }
}
