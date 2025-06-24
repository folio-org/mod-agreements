package com.k_int.accesscontrol.main;

import com.k_int.folio.FolioClientConfig;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PolicyEngineConfiguration {
  boolean acquisitionUnits; // An ON/OFF switch for whether to make queries for acquisition units or ignore
  FolioClientConfig folioClientConfig; // Include the folioConfig in the policy engine configuration

  // boolean kiGrants; // An ON/OFF switch for whether to make queries for kiGrants or ignore. This is an EXTENSION for later
}
