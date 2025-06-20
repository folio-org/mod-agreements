package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembershipResponse;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitResponse;
import com.k_int.folio.FolioClient;
import com.k_int.folio.FolioClientConfig;
import com.k_int.folio.FolioClientException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AcquisitionsClient extends FolioClient {
  public static final String ACQUISITION_UNIT_PATH = "/acquisitions-units/units";
  public static final String ACQUISITION_UNIT_MEMBERSHIP_PATH = "/acquisitions-units/memberships";

  public AcquisitionsClient(String baseUrl, String tenant, String patronId, String userLogin, String userPassword) {
    super(baseUrl, tenant, patronId, userLogin, userPassword);
  }

  public AcquisitionsClient(FolioClientConfig config) {
    super(config);
  }

  private static final Map<String, String> BASE_LIMIT_PARAM = new HashMap<String, String>() {{
    put("limit", "2147483647");
  }};

  private static final Map<String, String> BASE_UNIT_QUERY_PARAMS = combineQueryParams(new HashMap<String, String>() {{
    put("query", "cql.allRecords=1 sortby name");
  }}, BASE_LIMIT_PARAM);


  public AcquisitionUnitResponse getAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return get(ACQUISITION_UNIT_PATH, headers, combineQueryParams(BASE_UNIT_QUERY_PARAMS, queryParams), AcquisitionUnitResponse.class);
  }

  public AcquisitionUnitMembershipResponse getAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return get(ACQUISITION_UNIT_MEMBERSHIP_PATH, headers, combineQueryParams(BASE_LIMIT_PARAM, queryParams), AcquisitionUnitMembershipResponse.class);
  }

  // This now uses built in "patronId", consider one for generic user id?
  public AcquisitionUnitMembershipResponse getPatronAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return get(
      ACQUISITION_UNIT_MEMBERSHIP_PATH,
      headers,
      combineQueryParams(
        combineQueryParams(
          BASE_LIMIT_PARAM,
          new HashMap<String, String>() {{
            put("query", "(userId==" + getPatronId() + ")");
          }}
        ),
        queryParams
      ),
      AcquisitionUnitMembershipResponse.class
    );
  }

  // FIXME we need to consider what to do about "isDeleted" acq units

  /**
   * As far as I can tell, there is NO good way to find things like: Acquisition units the user _is a member of_ and which _restrict READ_ access.
   * Since the unit's restrictions are only available on the unit call, and memberships are only available on the membership call. Examples:
   * <br/><a href="https://github.com/folio-org/mod-finance/blob/v5.1.1/src/main/java/org/folio/services/protection/ProtectionService.java">mod-finance protection service</a>
   * <br/><a href="https://github.com/folio-org/mod-finance/blob/v5.1.1/src/main/java/org/folio/rest/util/HelperUtils.java#L99-L102">mod-finance CQL id joiner</a>
   * <br/>
   *
   * Necessitates pulling in a list of IDs and making a second and/or third call :(
   */
  public AcquisitionUnitResponse getRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, String restrictField, boolean restrictBool) throws IOException, FolioClientException, InterruptedException {
    return get(
      ACQUISITION_UNIT_PATH,
      headers,
      combineQueryParams(
        BASE_LIMIT_PARAM,
        combineQueryParams(
          new HashMap<String, String>() {{
            put("query", "(" + restrictField + "==" + restrictBool + ")");
          }},
          queryParams
        )
      ),
      AcquisitionUnitResponse.class);
  }

  public AcquisitionUnitResponse getRestrictReadAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return getRestrictionAcquisitionUnits(headers, queryParams, "protectRead", true);
  }

  public AcquisitionUnitResponse getNoRestrictReadAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return getRestrictionAcquisitionUnits(headers, queryParams, "protectRead", false);
  }

}
