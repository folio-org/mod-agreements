package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembershipResponse;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitResponse;
import com.k_int.folio.FolioClient;
import com.k_int.folio.FolioClientConfig;
import com.k_int.folio.FolioClientException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Client for interacting with FOLIO's acquisition unit and membership APIs.
 * Provides both synchronous and asynchronous access to key data structures
 * relevant to resource protection (e.g., acquisition unit restrictions).
 */
public class AcquisitionsClient extends FolioClient {
  public static final String ACQUISITION_UNIT_PATH = "/acquisitions-units/units";
  public static final String ACQUISITION_UNIT_MEMBERSHIP_PATH = "/acquisitions-units/memberships";

  public AcquisitionsClient(String baseUrl, String tenant, String patronId, String userLogin, String userPassword) {
    super(baseUrl, tenant, patronId, userLogin, userPassword);
  }

  public AcquisitionsClient(FolioClientConfig config) {
    super(config);
  }

  private static final Map<String, String> BASE_LIMIT_PARAM = new HashMap<>() {{
    put("limit", "2147483647");
  }};

  private static final Map<String, String> BASE_UNIT_QUERY_PARAMS = combineQueryParams(new HashMap<>() {{
    put("query", "cql.allRecords=1 sortby name");
  }}, BASE_LIMIT_PARAM);


  /**
   * Asynchronously fetches all acquisition unit memberships with default limits.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @return Future with unit membership response
   */
  public CompletableFuture<AcquisitionUnitMembershipResponse> getAsyncAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) {
    return getAsync(
      ACQUISITION_UNIT_MEMBERSHIP_PATH,
      headers,
      combineQueryParams(BASE_LIMIT_PARAM, queryParams),
      AcquisitionUnitMembershipResponse.class
    );
  }

  /**
   * Synchronously fetches all acquisition unit memberships with default limits.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @return AcquisitionUnitMembershipResponse
   * @throws FolioClientException For failed or invalid responses
   */
  public AcquisitionUnitMembershipResponse getAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncAcquisitionUnitMemberships(headers, queryParams));
  }

  /**
   * Asynchronously fetches unit memberships for the current patron.
   *
   * @param headers Request headers
   * @param queryParams Query parameters
   * @return Future with unit membership response
   */
  public CompletableFuture<AcquisitionUnitMembershipResponse> getAsyncUserAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) {
    return getAsync(
      ACQUISITION_UNIT_MEMBERSHIP_PATH,
      headers,
      combineQueryParams(
        combineQueryParams(
          BASE_LIMIT_PARAM,
          new HashMap<>() {{
            put("query", "(userId==" + getPatronId() + ")");
          }}
        ),
        queryParams
      ),
      AcquisitionUnitMembershipResponse.class
    );
  }

  /**
   * Synchronously fetches unit memberships for the current patron by blocking on the async path.
   *
   * @param headers Request headers
   * @param queryParams Query parameters
   * @return Membership response
   * @throws FolioClientException If the request fails or is interrupted
   */
  public AcquisitionUnitMembershipResponse getUserAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncUserAcquisitionUnitMemberships(headers, queryParams));
  }

  /**
   * Asynchronously fetches acquisition units filtered by a restriction flag.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param restriction Type of restriction (READ, CREATE, etc.)
   * @param restrictBool Whether the restriction is expected to be true or false
   * @return Future with filtered acquisition units
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, AcquisitionUnitRestriction restriction, boolean restrictBool) {
    Map<String, String> restrictionQueryParams;
    // Handle "no restriction" case
    if (restriction == AcquisitionUnitRestriction.NONE) {
      restrictionQueryParams = new HashMap<>();
    } else {
      restrictionQueryParams = new HashMap<>() {{
        put("query", "(" + restriction.getRestrictionAccessor() + "==" + restrictBool + ")");
      }};
    }

    return getAsync(
      ACQUISITION_UNIT_PATH,
      headers,
      combineQueryParams(
        BASE_LIMIT_PARAM,
        combineQueryParams(
          restrictionQueryParams,
          queryParams
        )
      ),
      AcquisitionUnitResponse.class);
  }

  /**
   * Asynchronously fetches all acquisition units with default query params.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @return Future with acquisition unit response
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncAcquisitionUnits(String[] headers, Map<String,String> queryParams) {
    return getAsyncRestrictionAcquisitionUnits(headers, combineQueryParams(BASE_UNIT_QUERY_PARAMS, queryParams), AcquisitionUnitRestriction.NONE, false);
  }

  /**
   * Synchronously fetches all acquisition units with default query params.
   *
   * @param headers Headers for the request
   * @param queryParams Extra query parameters
   * @return AcquisitionUnitResponse
   * @throws FolioClientException For failed or invalid responses
   */
  public AcquisitionUnitResponse getAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncAcquisitionUnits(headers, queryParams));
  }

  /**
   * Synchronously fetches acquisition units filtered by restriction flag.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param restriction Type of restriction (READ, CREATE, etc.)
   * @param restrictBool Whether the restriction is expected to be true or false
   * @return Filtered acquisition units
   * @throws FolioClientException If the async path fails
   */
  public AcquisitionUnitResponse getRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, AcquisitionUnitRestriction restriction, boolean restrictBool) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncRestrictionAcquisitionUnits(headers, queryParams, restriction, restrictBool));
  }

  /**
   * Asynchronously constructs and returns the 3 acquisition unit lists used for access control.
   * This includes non-restrictive units, restrictive units the user is a member of,
   * and restrictive units the user is not a member of.
   *
   * @param headers Request headers
   * @param restriction Restriction type (READ, etc.)
   * @return Future with UserAcquisitionUnits object
   */
  public CompletableFuture<UserAcquisitionUnits> getAsyncUserAcquisitionUnits(String[] headers, AcquisitionUnitRestriction restriction) {
    // When called for Restriction.NONE, the nonRestrictiveUnits will be all units, and the memberRestrictiveUnits/nonMemberRestrictiveUnits will comprise all the units the patron is/isn't a member of

    CompletableFuture<AcquisitionUnitResponse> restrictiveUnitsResponse = getAsyncRestrictionAcquisitionUnits(headers, Collections.emptyMap(), restriction, true);
    CompletableFuture<AcquisitionUnitMembershipResponse> acquisitionUnitMembershipsResponse = getAsyncUserAcquisitionUnitMemberships(headers, Collections.emptyMap());

    CompletableFuture<List<AcquisitionUnit>> memberRestrictiveUnits = restrictiveUnitsResponse.thenCombine(acquisitionUnitMembershipsResponse, (rur, aumr) ->
      rur.getAcquisitionsUnits()
        .stream()
        .filter(au -> aumr.getAcquisitionsUnitMemberships()
          .stream()
          .anyMatch(aum ->
            Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
              Objects.equals(aum.getUserId(), this.getPatronId())
          )
        )
        .toList()
    );

    CompletableFuture<List<AcquisitionUnit>> nonMemberRestrictiveUnits = restrictiveUnitsResponse.thenCombine(acquisitionUnitMembershipsResponse, (nrur, aumr) ->
      nrur.getAcquisitionsUnits()
        .stream()
        .filter(au -> aumr.getAcquisitionsUnitMemberships()
          .stream()
          .noneMatch(aum ->
            Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
              Objects.equals(aum.getUserId(), this.getPatronId())
          )
        )
        .toList()
    );

    return CompletableFuture.allOf(
        memberRestrictiveUnits,
        nonMemberRestrictiveUnits
      )
      .thenApply(ignoredVoid -> UserAcquisitionUnits
        .builder()
        .memberRestrictiveUnits(memberRestrictiveUnits.join())
        .nonMemberRestrictiveUnits(nonMemberRestrictiveUnits.join())
        .build());
  }

  /**
   * Synchronously constructs and returns the 3 acquisition unit lists for access control,
   * using the async version internally.
   *
   * @param headers Request headers
   * @param restriction Restriction type (READ, etc.)
   * @return UserAcquisitionUnits
   * @throws FolioClientException If any async call fails
   */
  public UserAcquisitionUnits getUserAcquisitionUnits(String[] headers, AcquisitionUnitRestriction restriction) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncUserAcquisitionUnits(headers, restriction));
  }
}
