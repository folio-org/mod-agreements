package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.core.policyengine.PolicyEngineImplementorConfiguration;
import com.k_int.folio.FolioClientConfig;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AcquisitionUnitPolicyEngineConfiguration implements PolicyEngineImplementorConfiguration {
  boolean enabled;

  FolioClientConfig folioClientConfig; // Configuration for the FOLIO client, including authentication and API settings
  boolean externalFolioLogin; // When configured for an EXTERNAL folio client, ensure we perform a login first.
}
