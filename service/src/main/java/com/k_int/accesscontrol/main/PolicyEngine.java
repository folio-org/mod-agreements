package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.AcquisitionUnitPolicySubquery;
import com.k_int.accesscontrol.acqunits.AcquisitionUnitRestriction;
import com.k_int.accesscontrol.acqunits.AcquisitionsClient;
import com.k_int.accesscontrol.acqunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.PolicySubquery;
import com.k_int.folio.FolioClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Core entry point for evaluating policy restrictions within the access control system.
 * The engine fetches necessary access policy data (e.g., from acquisition units)
 * and composes SQL fragments that can be used to filter Hibernate queries.
 * Configuration is driven by `PolicyEngineConfiguration`.
 */
@Slf4j
public class PolicyEngine {
  private final PolicyEngineConfiguration config;

  @Getter
  private final AcquisitionsClient acqClient;

  public PolicyEngine(PolicyEngineConfiguration config) {
    this.config = config;

    this.acqClient = new AcquisitionsClient(config.getFolioClientConfig());
  }

  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr) throws FolioClientException {
    List<PolicySubquery> policySubqueries = new ArrayList<>();
    if (config.acquisitionUnits) {
      // Do the acquisition unit logic
      AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);
      // Temporarily lets have this here so we can build the sql ourselves...
      UserAcquisitionUnits temporaryUserAcquisitionUnits = acqClient.getUserAcquisitionUnits(headers, acqRestriction);

      log.info("LOGDEBUG ({}) MemberRestrictiveUnits: {}", pr, temporaryUserAcquisitionUnits.getMemberRestrictiveUnits());
      log.info("LOGDEBUG ({}) NonMemberRestrictiveUnits: {}", pr, temporaryUserAcquisitionUnits.getNonMemberRestrictiveUnits());

      log.info("LOGDEBUG ({}) MemberRestrictiveUnits SIZE: {}", pr, temporaryUserAcquisitionUnits.getMemberRestrictiveUnits().size());
      log.info("LOGDEBUG ({}) NonMemberRestrictiveUnits SIZE: {}", pr, temporaryUserAcquisitionUnits.getNonMemberRestrictiveUnits().size());

      // FIXME We should probably have a `PolicyEngineImplementor` or something in acq units, which can take in the full PolicyEngineConfiguration
      // and then spin up an acq client, get user acquisition units, then build the PolicySubquery
      policySubqueries.add(
        AcquisitionUnitPolicySubquery
          .builder()
          .userAcquisitionUnits(temporaryUserAcquisitionUnits)
          .build()
      );
    }

    return policySubqueries;
  }
}
