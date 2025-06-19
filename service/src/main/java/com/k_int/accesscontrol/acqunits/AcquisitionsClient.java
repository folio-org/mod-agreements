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

  public AcquisitionsClient(String baseUrl, String tenant) {
    super(baseUrl, tenant);
  }

  private static Map<String, String> BASE_QUERY_PARAMS = new HashMap<String, String>() {{
    put("limit", "2147483647");
    put("query", "%28acquisitionsUnitId%3D%3Dba7b8596-16db-420e-ab9b-be19230b4c91%29");
  }};


  public AcquisitionUnitResponse getAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws IOException, FolioClientException, InterruptedException {
    return get(ACQUISITION_UNIT_PATH, headers, combineQueryParams(BASE_QUERY_PARAMS, queryParams), AcquisitionUnitResponse.class);
  }

//  public AcquisitionUnitMembershipResponse getAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) {
//    return get(ACQUISITION_UNIT_MEMBERSHIP_PATH, headers, combineQueryParams(BASE_QUERY_PARAMS, queryParams), AcquisitionUnitMembershipResponse);
//  }
}
