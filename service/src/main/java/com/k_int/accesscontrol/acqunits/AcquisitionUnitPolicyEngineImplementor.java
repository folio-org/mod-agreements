package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.main.PolicyEngineConfiguration;
import com.k_int.folio.FolioClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AcquisitionUnitPolicyEngineImplementor implements PolicyEngineImplementor {
  private final AcquisitionsClient acqClient;
  private final PolicyEngineConfiguration config;

  public AcquisitionUnitPolicyEngineImplementor(PolicyEngineConfiguration config) {
    this.config = config;
    this.acqClient = new AcquisitionsClient(config.getFolioClientConfig());
  }

  /**
   * @param headers The request context headers -- used mainly to connect to FOLIO (or other "internal" services)
   * @param pr The policy restriction which we want to filter by
   * @param queryType Whether to return boolean queries for single use or filter queries for all records
   * @return A list of PolicySubqueries, either for boolean restriction or for filtering.
   */
  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType) {
    List<PolicySubquery> policySubqueries = new ArrayList<>();

    if (queryType.equals(AccessPolicyQueryType.SINGLE)) {
      throw new PolicyEngineException("AccessPolicyQueryType.SINGLE is not yet implemented for AcquisitionUnits", PolicyEngineException.INVALID_QUERY_TYPE);
    }

    try {
      long beforeLogin = System.nanoTime();
      /* ------------------------------- LOGIN LOGIC ------------------------------- */
      String[] finalHeaders = new String[]{};

      if (config.isExternalFolioLogin()) {
        // Only perform a separate login if configured to
        finalHeaders = acqClient.getFolioAccessTokenCookie(new String[]{ });

      } else {
        finalHeaders = AcquisitionsClient.getFolioHeaders(headers);
      }
      log.info("LOGDEBUG FINAL HEADERS: {}", finalHeaders);

      /* ------------------------------- END LOGIN LOGIC ------------------------------- */
      long afterLogin = System.nanoTime();
      log.debug("LOGDEBUG login time: {}", Duration.ofNanos(afterLogin - beforeLogin));

      long beforePolicyLookup = System.nanoTime();
      // Do the acquisition unit logic
      AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);
      // Temporarily lets have this here so we can build the sql ourselves...
      UserAcquisitionUnits temporaryUserAcquisitionUnits = acqClient.getUserAcquisitionUnits(finalHeaders, acqRestriction);

      log.info("LOGDEBUG ({}) MemberRestrictiveUnits: {}", pr, temporaryUserAcquisitionUnits.getMemberRestrictiveUnits());
      log.info("LOGDEBUG ({}) NonMemberRestrictiveUnits: {}", pr, temporaryUserAcquisitionUnits.getNonMemberRestrictiveUnits());

      log.info("LOGDEBUG ({}) MemberRestrictiveUnits SIZE: {}", pr, temporaryUserAcquisitionUnits.getMemberRestrictiveUnits().size());
      log.info("LOGDEBUG ({}) NonMemberRestrictiveUnits SIZE: {}", pr, temporaryUserAcquisitionUnits.getNonMemberRestrictiveUnits().size());
      long afterPolicyLookup = System.nanoTime();
      log.debug("LOGDEBUG policy lookup time: {}", Duration.ofNanos(afterPolicyLookup - beforePolicyLookup));


      // FIXME We should probably have a `PolicyEngineImplementor` or something in acq units, which can take in the full PolicyEngineConfiguration
      // and then spin up an acq client, get user acquisition units, then build the PolicySubquery
      policySubqueries.add(
        AcquisitionUnitPolicySubquery
          .builder()
          .userAcquisitionUnits(temporaryUserAcquisitionUnits)
          .build()
      );

    } catch (FolioClientException | InterruptedException | IOException exc) {
      throw new PolicyEngineException("Something went wrong fetching acquisition units", exc);
    }

    return policySubqueries;
  }
}
