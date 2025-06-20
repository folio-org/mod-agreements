package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembershipResponse;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitResponse;
import com.k_int.folio.FolioClient;
import com.k_int.folio.FolioClientException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AcquisitionsClient extends FolioClient {
  public static final String ACQUISITION_UNIT_PATH = "/acquisitions-units/units";
  public static final String ACQUISITION_UNIT_MEMBERSHIP_PATH = "/acquisitions-units/memberships";

  public static final String DIKU_USER_ID = "a432e091-e445-40e7-a7a6-e31c035cd51a"; // FIXME this shouldn't be static obvs

  public AcquisitionsClient(String baseUrl, String tenant) {
    super(baseUrl, tenant);
  }

  private static final Map<String, String> BASE_LIMIT_PARAM = new HashMap<String, String>() {{
    put("limit", "2147483647");
  }};

  private static final Map<String, String> BASE_UNIT_QUERY_PARAMS = combineQueryParams(new HashMap<String, String>() {{
    put("query", "cql.allRecords=1 sortby name");
  }}, BASE_LIMIT_PARAM);

  private static final Map<String, String> BASE_UNIT_MEMBERSHIP_QUERY_PARAMS = combineQueryParams(new HashMap<String, String>() {{
    put("query", "(userId==" + DIKU_USER_ID + ")");
  }}, BASE_LIMIT_PARAM);

  public AcquisitionUnitResponse getAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return get(ACQUISITION_UNIT_PATH, headers, combineQueryParams(BASE_UNIT_QUERY_PARAMS, queryParams), AcquisitionUnitResponse.class);
  }

  public AcquisitionUnitMembershipResponse getAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return get(ACQUISITION_UNIT_MEMBERSHIP_PATH, headers, combineQueryParams(BASE_UNIT_MEMBERSHIP_QUERY_PARAMS, queryParams), AcquisitionUnitMembershipResponse.class);
  }
}
