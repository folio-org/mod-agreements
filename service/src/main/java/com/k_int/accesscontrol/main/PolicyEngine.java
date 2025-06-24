package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.AcquisitionUnitPolicySubquery;
import com.k_int.accesscontrol.acqunits.AcquisitionsClient;
import com.k_int.accesscontrol.acqunits.Restriction;
import com.k_int.accesscontrol.acqunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.folio.FolioClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PolicyEngine {
  private final PolicyEngineConfiguration config;

  @Getter
  private final AcquisitionsClient acqClient;

  public PolicyEngine(PolicyEngineConfiguration config) {
    this.config = config;

    this.acqClient = new AcquisitionsClient(config.getFolioClientConfig());
  }

  // Policy information object probably shouldn't be raw data, rather a shared set of named policy subqueries?
  public PolicyInformation getPolicyInformation(String[] headers, PolicyRestriction pr) throws FolioClientException {
    PolicyInformation.PolicyInformationBuilder policyInformationBuilder = PolicyInformation.builder();

    if (config.acquisitionUnits) {
      // Do the acquisition unit logic
      Restriction acqRestriction = Restriction.getRestrictionFromPolicyRestriction(pr);

      // Temporarily lets have this here so we can build the sql ourselves...
      UserAcquisitionUnits temporaryUserAcquisitionUnits = acqClient.getUserAcquisitionUnits(headers, acqRestriction);

      // We should probably have a `PolicyEngineImplementor` or something in acq units, which can take in the full PolicyEngineConfiguration
      // and then spin up an acq client, get user acquisition units, then build the SQL and return it in a known subquery shape or something
      String theSql = AcquisitionUnitPolicySubquery.returnSql(
        "access_policy",
        "acc_pol_type",
        "acc_pol_policy_id",
        "acc_pol_resource_id",
        "access_policy_resource_class",
        "sa",
        "id",
        String.join(",", temporaryUserAcquisitionUnits.getNonRestrictiveUnitIds()),
        String.join(",", temporaryUserAcquisitionUnits.getMemberRestrictiveUnitIds()),
        String.join(",", temporaryUserAcquisitionUnits.getNonMemberRestrictiveUnitIds())
        );

      log.info("LOGDEBUG THE SQL: {}", theSql);

      policyInformationBuilder.userAcquisitionUnits(temporaryUserAcquisitionUnits);
    }

    return policyInformationBuilder.build();
  }

  /*

  RELEVANT FIELDS (In grails anyway... these need to come from an interface or something)
  acc_pol_type
  acc_pol_policy_id
  acc_pol_resource_class
  acc_pol_resource_id


  SQL -- WE NEED

  SELECT *
  FROM subscription_agreement sa
  WHERE NOT EXISTS (
    SELECT 1 FROM access_policy ap1
    WHERE
      ap1.type = ACQ_UNIT AND
      ap1.resource_id = sa.id
  ) OR (
    sa.id IN (
      SELECT ap2.resource_id
      FROM access_policy ap2
      WHERE
          ap2.type = ACQ_UNIT AND
          ap2.policy_id IN (:listA)
    )
  ) OR (
    sa.id IN (
      SELECT ap3.resource_id
      FROM access_policy ap3
      WHERE
          ap3.type = ACQ_UNIT AND
          ap3.policy_id IN (:listB)
    )
    AND sa.id NOT IN (
      SELECT ap4.resource_id
      FROM access_policy ap4
      WHERE
          ap4.type = ACQ_UNIT AND
          ap4.policy_id IN (:listC)
    )
  )


  Specifically we need the "WHERE" clauses
   */

}
