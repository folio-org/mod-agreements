package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.main.PolicyEngineConfiguration;
import com.k_int.folio.FolioClientException;
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

    try {
      long beforeLogin = System.nanoTime();
      /* ------------------------------- LOGIN LOGIC ------------------------------- */
      String[] finalHeaders;

      if (config.isExternalFolioLogin()) {
        // Only perform a separate login if configured to
        finalHeaders = acqClient.getFolioAccessTokenCookie(new String[]{ });

      } else {
        finalHeaders = AcquisitionsClient.getFolioHeaders(headers);
      }

      /* ------------------------------- END LOGIN LOGIC ------------------------------- */
      long afterLogin = System.nanoTime();
      log.trace("AcquisitionUnitPolicyEngineImplementor login time: {}", Duration.ofNanos(afterLogin - beforeLogin));

      long beforePolicyLookup = System.nanoTime();
      // Do the acquisition unit logic
      AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);
      // Temporarily lets have this here so we can build the sql ourselves...
      UserAcquisitionUnits temporaryUserAcquisitionUnits = acqClient.getUserAcquisitionUnits(finalHeaders, acqRestriction);

      long afterPolicyLookup = System.nanoTime();
      log.trace("AcquisitionUnitPolicyEngineImplementor policy lookup time: {}", Duration.ofNanos(afterPolicyLookup - beforePolicyLookup));

//      log.trace("LOGDEBUG ({}) MemberRestrictiveUnits: {}", pr, temporaryUserAcquisitionUnits.getMemberRestrictiveUnits());
//      log.trace("LOGDEBUG ({}) NonMemberRestrictiveUnits: {}", pr, temporaryUserAcquisitionUnits.getNonMemberRestrictiveUnits());

      /* In theory we could have a separate individual PolicySubquery class for every Restriction,
       * but READ/UPDPATE/DELETE are all the same for Acq Units (with slight tweak when LIST vs SINGLE),
       * and CREATE is simple, so we'll do all the work on one class.
       *
       * If we want to make this more performant we could shortcut in the "CREATE" case since that doesn't need the acquisition unit fetch
       */
      policySubqueries.add(
        AcquisitionUnitPolicySubquery
          .builder()
          .userAcquisitionUnits(temporaryUserAcquisitionUnits)
          .queryType(queryType)
          .restriction(pr)
          .build()
      );
    } catch (FolioClientException fce) {
      Throwable cause = fce.getCause();
      if (cause != null) {
        throw new PolicyEngineException("FolioClientException thrown fetching Acquisition units: " + fce.getCause().getMessage(), fce);
      }
      throw new PolicyEngineException("FolioClientException thrown fetching Acquisition units", fce);
    } catch (InterruptedException | IOException exc) {
      Throwable cause = exc.getCause();
      if (cause != null) {
        throw new PolicyEngineException("Something went wrong fetching Acquisition units: " + cause.getMessage(), exc);
      }
      throw new PolicyEngineException("Something went wrong fetching Acquisition units", exc);
    }

    return policySubqueries;
  }
}
