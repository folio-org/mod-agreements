package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.AcquisitionsClient;
import com.k_int.accesscontrol.acqunits.Restriction;
import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.folio.FolioClientException;
import lombok.Getter;

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
      policyInformationBuilder.userAcquisitionUnits(acqClient.getUserAcquisitionUnits(headers, acqRestriction));
    }

    return policyInformationBuilder.build();
  }

}
