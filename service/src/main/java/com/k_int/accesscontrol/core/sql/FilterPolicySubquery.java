package com.k_int.accesscontrol.core.sql;

import com.k_int.accesscontrol.core.AccessPolicies;
import com.k_int.accesscontrol.core.http.filters.PoliciesFilter;
import com.k_int.accesscontrol.core.http.responses.Policy;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

@Builder
@Slf4j
public class FilterPolicySubquery implements PolicySubquery {
  public static final String FILTER_TEMPLATE = """
    (
      EXISTS (
        SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME #ACCESS_POLICY_TABLE_ALIAS
        WHERE
        #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_TYPE_COLUMN_NAME = #THE_TYPE AND
        #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
        #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_ID_COLUMN_NAME IN (#FILTER_UNITS)
        LIMIT 1
      )
    )
  """;
  List<PoliciesFilter> policiesFilters; // Set up the policiesFilters, so we can return the correct shape right out the back of getSql

  public AccessControlSql getSql(PolicySubqueryParameters parameters) {
    // Spin up a list of all SQL parameters;
    List<String> allParameters = new ArrayList<>();
    // Keep track of their types as well.
    List<AccessControlSqlType> allTypes = new ArrayList<>();


    String filterSql = "(\n" +
      String.join(
        "\n AND \n", // Take each top level PoliciesFilter and AND them together
        IntStream.range(0, policiesFilters.size()) // Use IntStream to map WITH pfIndex
          .mapToObj(pfIndex -> {
            PoliciesFilter pf = policiesFilters.get(pfIndex);
            return "(\n" +
              String.join(
                "\n OR \n",
                IntStream.range(0, pf.getFilters().size()) // Use IntStream to get AccessPolicies index
                  .mapToObj(apIndex -> {
                    AccessPolicies ap = pf.getFilters().get(apIndex);

                    allParameters.add(parameters.getResourceClass()); // Add resource class
                    allTypes.add(AccessControlSqlType.STRING); // Resource class is a string

                    allParameters.addAll(ap.getPolicies().stream().map(Policy::getId).toList()); // Add policy ids
                    allTypes.addAll(Collections.nCopies(ap.getPolicies().size(), AccessControlSqlType.STRING)); // all policy ids are strings

                    return """
                      (
                        EXISTS (
                          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME #ACCESS_POLICY_TABLE_ALIAS
                          WHERE
                          #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_TYPE_COLUMN_NAME = #THE_TYPE AND
                          #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
                          #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_ID_COLUMN_NAME IN (#FILTER_UNITS)
                          LIMIT 1
                        )
                      )
                    """
                      .replaceAll("#ACCESS_POLICY_TABLE_NAME", parameters.getAccessPolicyTableName())
                      .replaceAll("#ACCESS_POLICY_TABLE_ALIAS", "apFilters" + pfIndex + "_" + apIndex) // Unique alias per EXISTS subquery
                      .replaceAll("#ACCESS_POLICY_TYPE_COLUMN_NAME", parameters.getAccessPolicyTypeColumnName())
                      .replaceAll("#THE_TYPE", "'" + ap.getType().toString() + "'")
                      .replaceAll("#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME", parameters.getAccessPolicyResourceClassColumnName())
                      .replaceAll("#RESOURCE_CLASS", "?") // MAPPING RESOURCE CLASS TO A PARAMETER
                      .replaceAll("#ACCESS_POLICY_ID_COLUMN_NAME", parameters.getAccessPolicyIdColumnName())
                      .replaceAll("#FILTER_UNITS", String.join(",", Collections.nCopies(ap.getPolicies().size(), "?"))); // MAPPING FILTER UNITS TO PARAMETERS
                  })
                  .toList()
              ) +
              "\n)" ;
          })
          .toList()
      ) + "\n)";

    log.trace("FilterPolicySubquery::getSql : filterSql = {}", filterSql);

    return AccessControlSql.builder()
      .sqlString(filterSql)
      .parameters(allParameters.toArray())
      .types(allTypes.toArray(new AccessControlSqlType[0]))
      .build();
  }
}
