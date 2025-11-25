package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.dto.AcquisitionUnitRestrictionProtectedPair;
import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembershipResponse;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitPolicy;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitResponse;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnitsMetadata;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionsUnitSubset;
import com.k_int.folio.FolioClient;
import com.k_int.folio.FolioClientConfig;
import com.k_int.folio.FolioClientException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Client for interacting with FOLIO's acquisition unit and membership APIs.
 * Provides both synchronous and asynchronous access to key data structures
 * relevant to resource protection (e.g., acquisition unit restrictions).
 */
public class AcquisitionsClient extends FolioClient {
  /**
   * Base path for acquisition unit API endpoints.
   * This is used to construct the full URL for API requests.
   */
  public static final String ACQUISITION_UNIT_PATH = "/acquisitions-units/units";
  /**
   * Path for acquisition unit membership API endpoints.
   * This is used to fetch memberships associated with acquisition units.
   */
  public static final String ACQUISITION_UNIT_MEMBERSHIP_PATH = "/acquisitions-units/memberships";

  /**
   * Constructs an AcquisitionsClient with the specified base URL, tenant, patron ID, user login, and password.
   *
   * @param baseUrl Base URL for the FOLIO instance
   * @param tenant Tenant identifier for the FOLIO instance
   * @param patronId Patron ID for the user
   * @param userLogin User login name
   * @param userPassword User password
   */
  public AcquisitionsClient(String baseUrl, String tenant, String patronId, String userLogin, String userPassword) {
    super(baseUrl, tenant, patronId, userLogin, userPassword);
  }

  /**
   * Constructs an AcquisitionsClient using a FolioClientConfig object.
   * This allows for more flexible configuration options, such as custom headers or timeouts.
   *
   * @param config Configuration object containing base URL, tenant, patron ID, user login, and password
   */
  public AcquisitionsClient(FolioClientConfig config) {
    super(config);
  }

  /**
   * Default limit for acquisition unit queries.
   * This is set to the maximum integer value to allow fetching all records.
   */
  private static final Map<String, String> BASE_LIMIT_PARAM = new HashMap<>() {{
    put("limit", "2147483647");
  }};

  /**
   * Default query parameters for acquisition unit queries.
   * This includes a query to fetch all records sorted by name.
   * It combines with the base limit parameter to ensure all units are fetched.
   */
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
   * Asynchronously fetches acquisition units from FOLIO using provided query parameters.
   * This does not apply any default sorting or filtering unless passed explicitly in the queryParams.
   *
   * @param headers Request headers
   * @param queryParams Query parameters to include in the request
   * @return CompletableFuture containing the acquisition unit response
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncAcquisitionUnits(String[] headers, Map<String,String> queryParams) {
    return getAsync(
      ACQUISITION_UNIT_PATH,
      headers,
      combineQueryParams(
        BASE_LIMIT_PARAM,
        queryParams
      ),
      AcquisitionUnitResponse.class
    );
  }

  /**
   * Synchronously fetches acquisition units from FOLIO using provided query parameters.
   *
   * @param headers Request headers
   * @param queryParams Query parameters to include in the request
   * @return AcquisitionUnitResponse containing matching acquisition units
   * @throws FolioClientException if the asynchronous request fails or is interrupted
   */
  public AcquisitionUnitResponse getAcquisitionUnits(String[] headers, Map<String,String> queryParams) {
    return asyncFolioClientExceptionHelper(() -> getAsyncAcquisitionUnits(headers, queryParams));
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

    return getAsyncAcquisitionUnits(
      headers,
      combineQueryParams(
        restrictionQueryParams,
        queryParams
      )
    );
  }

  /**
   * Asynchronously fetches acquisition units filtered by multiple restriction-protection pairs.
   * <p>
   * This method constructs a single CQL query using a logical OR operation across the provided
   * restriction and protection flag pairs, then fetches the matching units from the FOLIO API.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters (e.g., sorting, additional filtering)
   * @param restrictionPairs Collection of restriction-protection pairs to filter by (e.g., [READ=true, CREATE=false])
   * @return Future with acquisition unit response containing all units that match any of the pairs
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, Collection<AcquisitionUnitRestrictionProtectedPair> restrictionPairs) {
    Map<String, String> restrictionQueryParams;

    String queryString = restrictionPairs
      .stream()
      .map(pair -> {
        if (pair.getRestriction() == AcquisitionUnitRestriction.NONE) {
          return null;
        }
        return "(" + pair.getRestriction().getRestrictionAccessor() + "==" + pair.isProtected() + ")";
      })
      .filter(Objects::nonNull)
      .collect(Collectors.joining(" OR "));

    if (queryString.isEmpty()) {
      restrictionQueryParams = new HashMap<>();
    } else {
      restrictionQueryParams = new HashMap<>() {{
        put("query", queryString);
      }};
    }

    // Turn CompletableFuture<AcquisitionUnitResponse> into CompletableFuture<Map<AcquisitionUnitRestrictionProtectedPair, AcquisitionUnitResponse>> HERE
    return getAsyncAcquisitionUnits(
      headers,
      combineQueryParams(
        restrictionQueryParams,
        queryParams
      )
    );
  }

  /**
   * Synchronously fetches acquisition units filtered by multiple restriction-protection pairs.
   * <p>
   * Wraps the asynchronous variant to provide a synchronous call, handling checked exceptions.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param restrictionPairs Collection of restriction-protection pairs to filter by
   * @return Filtered acquisition units
   * @throws FolioClientException If the async path fails or is interrupted
   */
  public AcquisitionUnitResponse getRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, Collection<AcquisitionUnitRestrictionProtectedPair> restrictionPairs) {
    return asyncFolioClientExceptionHelper(() -> getAsyncRestrictionAcquisitionUnits(headers, queryParams, restrictionPairs));
  }

  /**
   * Asynchronously fetches acquisition units split by multiple restriction-protection pairs.
   * <p>
   * This method first fetches a superset of units matching any of the given pairs in a single
   * API call, and then post-processes the results to map each {@link AcquisitionUnitRestrictionProtectedPair}
   * to a separate {@link AcquisitionUnitResponse} containing only the units that specifically match that pair.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param restrictionPairs Collection of restriction-protection pairs to filter by and map results to
   * @return Future with map of restriction-protection pairs to acquisition unit responses
   */
  public CompletableFuture<Map<AcquisitionUnitRestrictionProtectedPair, AcquisitionUnitResponse>> getAsyncMappedRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, Collection<AcquisitionUnitRestrictionProtectedPair> restrictionPairs) {
    // Turn CompletableFuture<AcquisitionUnitResponse> into CompletableFuture<Map<AcquisitionUnitRestrictionProtectedPair, AcquisitionUnitResponse>> HERE
    CompletableFuture<AcquisitionUnitResponse> response = getAsyncRestrictionAcquisitionUnits(
      headers,
      queryParams,
      restrictionPairs
    );

    return response.thenApply(acquisitionUnitResponse -> {
      Map<AcquisitionUnitRestrictionProtectedPair, AcquisitionUnitResponse> result = new HashMap<>();
      // We need to map each restriction pair to the units that match it
      for (AcquisitionUnitRestrictionProtectedPair restrictionPair : restrictionPairs) {
        List<AcquisitionUnit> filteredUnits = acquisitionUnitResponse.getAcquisitionsUnits()
          .stream()
          // Find all the AcquisitionUnits that match this restriction/protection pair
          .filter(au -> au.getProtectionFromRestriction(restrictionPair.getRestriction()) == restrictionPair.isProtected())
          .toList();

        // Set up (Builder paradigm caused isssues with the HTTP response shape casting
        AcquisitionUnitResponse aur = new AcquisitionUnitResponse();
        aur.setAcquisitionsUnits(filteredUnits);
        aur.setTotalRecords(filteredUnits.size());

        result.put(
          restrictionPair,
          aur
        );
      }

      return result;
    });
  }

  /**
   * Synchronously fetches acquisition units split by multiple restriction-protection pairs.
   * <p>
   * Wraps the asynchronous variant to provide a synchronous call, handling checked exceptions.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param restrictionPairs Collection of restriction-protection pairs to filter by
   * @return Map of restriction-protection pairs to filtered acquisition units
   * @throws FolioClientException If the async path fails or is interrupted
   */
  public Map<AcquisitionUnitRestrictionProtectedPair, AcquisitionUnitResponse> getMappedRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, Collection<AcquisitionUnitRestrictionProtectedPair> restrictionPairs) {
    return asyncFolioClientExceptionHelper(() -> getAsyncMappedRestrictionAcquisitionUnits(headers, queryParams, restrictionPairs));
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
   * Asynchronously fetches acquisition units by ID list.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param unitIds List of acquisition unit UUIDs to fetch
   * @return Future with acquisition unit response
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncIdAcquisitionUnits(String[] headers, Map<String,String> queryParams, Collection<String> unitIds) {
    Map<String, String> idQueryParams = new HashMap<>();
    if (unitIds != null && !unitIds.isEmpty()) {
      String idQuery = unitIds.stream()
        .map(id -> "id == \"" + id + "\"")
        .collect(Collectors.joining(" OR ")
      );

      idQueryParams.put("query", "(" + idQuery + ")");
    }

    return getAsyncAcquisitionUnits(
      headers,
      combineQueryParams(
        idQueryParams,
        queryParams
      )
    );
  }

  /**
   * Synchronously fetches acquisition units by ID list.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param unitIds List of acquisition unit UUIDs to fetch
   * @return AcquisitionUnitResponse containing matching units
   * @throws FolioClientException If the async path fails or is interrupted
   */
  public AcquisitionUnitResponse getIdAcquisitionUnits(String[] headers, Map<String,String> queryParams, Collection<String> unitIds) {
    return asyncFolioClientExceptionHelper(() -> getAsyncIdAcquisitionUnits(headers, queryParams, unitIds));
  }

  /**
   * Asynchronously fetches all acquisition units with default query params.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @return Future with acquisition unit response
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncAllAcquisitionUnits(String[] headers, Map<String,String> queryParams) {
    return getAsyncAcquisitionUnits(headers, combineQueryParams(BASE_UNIT_QUERY_PARAMS, queryParams));
  }

  /**
   * Synchronously fetches all acquisition units with default query params.
   *
   * @param headers Headers for the request
   * @param queryParams Extra query parameters
   * @return AcquisitionUnitResponse
   * @throws FolioClientException For failed or invalid responses
   */
  public AcquisitionUnitResponse getAllAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncAllAcquisitionUnits(headers, queryParams));
  }

  /**
   * Asynchronously fetches and maps {@link UserAcquisitionUnits} for multiple {@link AcquisitionUnitRestriction} types.
   * <p>
   * This method optimizes API calls by fetching all necessary restrictive and non-restrictive
   * units for all specified restrictions in one batch call, and membership data in a parallel
   * call. It then combines and filters the data to build a complete {@link UserAcquisitionUnits} object
   * for each restriction, based on the requested {@link UserAcquisitionsUnitSubset}s.
   *
   * @param headers Request headers
   * @param restrictions Collection of restriction types (e.g., READ, CREATE) to fetch data for
   * @param fetchSubsets Subset of user acquisition units to fetch (e.g., MEMBER_RESTRICTIVE, NON_MEMBER_NON_RESTRICTIVE)
   * @return Future with a map where the key is the restriction type and the value is the calculated user's acquisition unit subsets for that restriction
   */
  public CompletableFuture<Map<AcquisitionUnitRestriction, UserAcquisitionUnits>> getAsyncMappedRestrictionUserAcquisitionUnits(String[] headers, Collection<AcquisitionUnitRestriction> restrictions, Set<UserAcquisitionsUnitSubset> fetchSubsets) {
    // When called for Restriction.NONE, the nonRestrictiveUnits will be all units, and the memberRestrictiveUnits/nonMemberRestrictiveUnits will comprise all the units the patron is/isn't a member of

    // Construct metadata for the user acquisition units we're about to fetch
    UserAcquisitionUnitsMetadata userAcquisitionUnitsMetadata = new UserAcquisitionUnitsMetadata(fetchSubsets);

    // Set up which fetches to make
    List<AcquisitionUnitRestrictionProtectedPair> protectedPairs = new ArrayList<>();
    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch memberRestrictive or nonMemberRestrictive units,
     * we will fetch the restrictive units for ALL the restrictions. Otherwise, we will complete the future with null.
     */
    if (userAcquisitionUnitsMetadata.isMemberRestrictive() || userAcquisitionUnitsMetadata.isNonMemberRestrictive()) {
      restrictions.forEach(restriction -> protectedPairs.add(
        AcquisitionUnitRestrictionProtectedPair.builder()
          .restriction(restriction)
          .isProtected(true)
          .build()
      ));
    }

    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch nonRestrictive units,
     * we will fetch them. Otherwise, we will complete the future with null.
     */
    if (userAcquisitionUnitsMetadata.isNonRestrictive() || userAcquisitionUnitsMetadata.isMemberNonRestrictive() || userAcquisitionUnitsMetadata.isNonMemberNonRestrictive()) {
      restrictions.forEach(restriction -> protectedPairs.add(
        AcquisitionUnitRestrictionProtectedPair.builder()
          .restriction(restriction)
          .isProtected(false)
          .build()
      ));
    }

    // Fetch both restrictive AND non-restrictive in one
    CompletableFuture<Map<AcquisitionUnitRestrictionProtectedPair, AcquisitionUnitResponse>> acquisitionsUnitSplitResponse = getAsyncMappedRestrictionAcquisitionUnits(
      headers,
      Collections.emptyMap(),
      protectedPairs
    );

    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch any memberships,
     * we will fetch the acquisition unit memberships. Otherwise, we will complete the future with null.
     */
    CompletableFuture<AcquisitionUnitMembershipResponse> acquisitionUnitMembershipsResponse =
      (userAcquisitionUnitsMetadata.isNonRestrictive() || userAcquisitionUnitsMetadata.isMemberRestrictive() || userAcquisitionUnitsMetadata.isNonMemberRestrictive())
        ?  getAsyncUserAcquisitionUnitMemberships(headers, Collections.emptyMap())
        : CompletableFuture.completedFuture(null);


    CompletableFuture<Map<AcquisitionUnitRestriction, UserAcquisitionUnits>> result =
      CompletableFuture.completedFuture(new HashMap<>());

    for (AcquisitionUnitRestriction restriction : restrictions) {
      /*
       * If the userAcquisitionUnitsMetadata indicates that we want to fetch memberRestrictive units,
       * we will filter the restrictive units response to get only those units where the user is a member.
       * Otherwise, we will complete the future with null.
       */
      CompletableFuture<List<AcquisitionUnit>> memberRestrictiveUnits = userAcquisitionUnitsMetadata.isMemberRestrictive() ?
        acquisitionsUnitSplitResponse.thenCombine(acquisitionUnitMembershipsResponse, (ausr, aumr) ->
          ausr.get(
              AcquisitionUnitRestrictionProtectedPair
                .builder()
                .restriction(restriction)
                .isProtected(true)
                .build()
            )
            .getAcquisitionsUnits()
            .stream()
            .filter(au -> aumr.getAcquisitionsUnitMemberships()
              .stream()
              .anyMatch(aum ->
                Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
                  Objects.equals(aum.getUserId(), this.getPatronId())
              )
            )
            .toList()
        ) : CompletableFuture.completedFuture(null);


      /*
       * If the userAcquisitionUnitsMetadata indicates that we want to fetch nonMemberRestrictive units,
       * we will filter the restrictive units response to get only those units where the user is not a member.
       * Otherwise, we will complete the future with null.
       */
      CompletableFuture<List<AcquisitionUnit>> nonMemberRestrictiveUnits = userAcquisitionUnitsMetadata.isNonMemberRestrictive() ?
        acquisitionsUnitSplitResponse.thenCombine(acquisitionUnitMembershipsResponse, (ausr, aumr) ->
          ausr.get(
              AcquisitionUnitRestrictionProtectedPair
                .builder()
                .restriction(restriction)
                .isProtected(true)
                .build()
            )
            .getAcquisitionsUnits()
            .stream()
            .filter(au -> aumr.getAcquisitionsUnitMemberships()
              .stream()
              .noneMatch(aum ->
                Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
                  Objects.equals(aum.getUserId(), this.getPatronId())
              )
            )
            .toList()
        ) : CompletableFuture.completedFuture(null);

      /*
       * If the userAcquisitionUnitsMetadata indicates that we want to fetch nonMemberNonRestrictive units,
       * we will filter the restrictive units response to get only those non restrictive units where the user is not a member.
       * Otherwise, we will complete the future with null.
       */
      CompletableFuture<List<AcquisitionUnit>> nonMemberNonRestrictiveUnits = userAcquisitionUnitsMetadata.isNonMemberNonRestrictive() ?
        acquisitionsUnitSplitResponse.thenCombine(acquisitionUnitMembershipsResponse, (ausr, aumr) ->
          ausr.get(
              AcquisitionUnitRestrictionProtectedPair
                .builder()
                .restriction(restriction)
                .isProtected(false)
                .build()
            )
            .getAcquisitionsUnits()
            .stream()
            .filter(au -> aumr.getAcquisitionsUnitMemberships()
              .stream()
              .noneMatch(aum ->
                Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
                  Objects.equals(aum.getUserId(), this.getPatronId())
              )
            )
            .toList()
        ) : CompletableFuture.completedFuture(null);

      /*
       * If the userAcquisitionUnitsMetadata indicates that we want to fetch memberNonRestrictive units,
       * we will filter the restrictive units response to get only those units where the user is a member.
       * Otherwise, we will complete the future with null.
       */
      CompletableFuture<List<AcquisitionUnit>> memberNonRestrictiveUnits = userAcquisitionUnitsMetadata.isMemberNonRestrictive() ?
        acquisitionsUnitSplitResponse.thenCombine(acquisitionUnitMembershipsResponse, (ausr, aumr) ->
          ausr.get(
              AcquisitionUnitRestrictionProtectedPair
                .builder()
                .restriction(restriction)
                .isProtected(false)
                .build()
            )
            .getAcquisitionsUnits()
            .stream()
            .filter(au -> aumr.getAcquisitionsUnitMemberships()
              .stream()
              .anyMatch(aum ->
                Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
                  Objects.equals(aum.getUserId(), this.getPatronId())
              )
            )
            .toList()
        ) : CompletableFuture.completedFuture(null);

      /*
       * Combine all the futures and return a UserAcquisitionUnits object containing the results.
       */
      CompletableFuture<UserAcquisitionUnits> fut = CompletableFuture.allOf(
          memberRestrictiveUnits,
          nonMemberRestrictiveUnits,
          acquisitionsUnitSplitResponse,
          memberNonRestrictiveUnits,
          nonMemberNonRestrictiveUnits
        )
        .thenApply(ignoredVoid -> {
          // Get the non-restrictive units from the acquisitionsUnit split response.
          Map<AcquisitionUnitRestrictionProtectedPair, AcquisitionUnitResponse> completedSplitResponse = acquisitionsUnitSplitResponse.join();
          List<AcquisitionUnit> nonRestrictiveUnits = null;
          if (completedSplitResponse != null) {
            nonRestrictiveUnits = completedSplitResponse.get(AcquisitionUnitRestrictionProtectedPair.builder().restriction(restriction).isProtected(false).build()).getAcquisitionsUnits();
          }

          return UserAcquisitionUnits
            .builder()
            .memberRestrictiveUnits(memberRestrictiveUnits.join())
            .nonMemberRestrictiveUnits(nonMemberRestrictiveUnits.join())
            .nonRestrictiveUnits(nonRestrictiveUnits)
            .memberNonRestrictiveUnits(memberNonRestrictiveUnits.join())
            .nonMemberNonRestrictiveUnits(nonMemberNonRestrictiveUnits.join())
            .userAcquisitionUnitsMetadata(userAcquisitionUnitsMetadata)
            .build();
        });

      // Asynchronously combine into result map once complete
      result = result.thenCombine(fut, (map, value) -> {
        map.put(restriction, value);
        return map;
      });
    }

    return result;
  }

  /**
   * Synchronously fetches and maps {@link UserAcquisitionUnits} for multiple {@link AcquisitionUnitRestriction} types.
   * <p>
   * Wraps the asynchronous variant to provide a synchronous call, handling checked exceptions.
   *
   * @param headers Request headers
   * @param restrictions Collection of restriction types to fetch data for
   * @param fetchSubsets Subset of user acquisition units to fetch
   * @return Map where the key is the restriction type and the value is the calculated user's acquisition unit subsets for that restriction
   * @throws FolioClientException If the async path fails or is interrupted
   */
  public Map<AcquisitionUnitRestriction, UserAcquisitionUnits> getMappedRestrictionUserAcquisitionUnits(String[] headers, Collection<AcquisitionUnitRestriction> restrictions, Set<UserAcquisitionsUnitSubset> fetchSubsets) {
    return asyncFolioClientExceptionHelper(() -> getAsyncMappedRestrictionUserAcquisitionUnits(headers, restrictions, fetchSubsets));
  }

  /**
   * Asynchronously fetches {@link UserAcquisitionUnits} for a single {@link AcquisitionUnitRestriction}.
   * <p>
   * This is a shorthand method that delegates to {@link #getAsyncMappedRestrictionUserAcquisitionUnits}
   * and extracts the single resulting {@link UserAcquisitionUnits} object from the map.
   *
   * @param headers Request headers
   * @param restriction The single restriction type (e.g., READ) to fetch data for
   * @param fetchSubsets Subset of user acquisition units to fetch
   * @return Future with the calculated user's acquisition unit subsets for the specified restriction
   */
  public CompletableFuture<UserAcquisitionUnits> getAsyncUserAcquisitionUnits(String[] headers, AcquisitionUnitRestriction restriction, Set<UserAcquisitionsUnitSubset> fetchSubsets) {
    return getAsyncMappedRestrictionUserAcquisitionUnits(headers, Collections.singleton(restriction), fetchSubsets).thenApply(map -> map.get(restriction));
  }

  /**
   * Synchronously fetches {@link UserAcquisitionUnits} for a single {@link AcquisitionUnitRestriction}.
   * <p>
   * Wraps the asynchronous variant to provide a synchronous call, handling checked exceptions.
   *
   * @param headers Request headers
   * @param restriction The single restriction type to fetch data for
   * @param fetchSubsets Subset of user acquisition units to fetch
   * @return The calculated user's acquisition unit subsets for the specified restriction
   * @throws FolioClientException If the async path fails or is interrupted
   */
  public UserAcquisitionUnits getUserAcquisitionUnits(String[] headers, AcquisitionUnitRestriction restriction, Set<UserAcquisitionsUnitSubset> fetchSubsets) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncUserAcquisitionUnits(headers, restriction, fetchSubsets));
  }

  /**
   * Asynchronously fetches {@link AcquisitionUnitPolicy} instances for the specified acquisition unit IDs.
   * <p>
   * This method retrieves acquisition units and the current user's acquisition unit memberships in parallel,
   * then maps each acquisition unit to an {@link AcquisitionUnitPolicy}, including whether the user is a member.
   *
   * @param headers Request headers, including tenant and auth token
   * @param unitIds Collection of acquisition unit UUIDs to fetch and evaluate policies for
   * @return A {@link CompletableFuture} resolving to a list of {@link AcquisitionUnitPolicy} objects
   */
  public CompletableFuture<List<AcquisitionUnitPolicy>> getAsyncAcquisitionUnitPolicies(String[] headers, Collection<String> unitIds) {
    CompletableFuture<AcquisitionUnitResponse> acqUnitResponse = getAsyncIdAcquisitionUnits(headers, Collections.emptyMap(), unitIds);
    CompletableFuture<AcquisitionUnitMembershipResponse> acqUnitMembershipResponse = getAsyncUserAcquisitionUnitMemberships(headers, Collections.emptyMap());

    // Return a full policy per acq unit
    return CompletableFuture.allOf(
      acqUnitResponse,
      acqUnitMembershipResponse
    ).thenApply(ignoredVoid ->
      acqUnitResponse
        .join()
        .getAcquisitionsUnits()
        .stream()
        .map(acqUnit -> {
          // Work out if member
          Boolean isMember = acqUnitMembershipResponse.join().getAcquisitionsUnitMemberships().stream().anyMatch(aum -> Objects.equals(aum.getAcquisitionsUnitId(), acqUnit.getId()));

          return AcquisitionUnitPolicy.fromAcquisitionUnit(acqUnit, isMember);
        })
        .toList()
    );
  }

  /**
   * Synchronously fetches {@link AcquisitionUnitPolicy} instances for the specified acquisition unit IDs.
   * <p>
   * Wraps the asynchronous variant and converts checked exceptions to a {@link FolioClientException} if necessary.
   *
   * @param headers Request headers, including tenant and auth token
   * @param unitIds Collection of acquisition unit UUIDs to fetch and evaluate policies for
   * @return List of {@link AcquisitionUnitPolicy} objects for the given acquisition units
   * @throws FolioClientException if the asynchronous operation fails or is interrupted
   */
  public List<AcquisitionUnitPolicy> getAcquisitionUnitPolicies(String[] headers, Collection<String> unitIds) {
    return asyncFolioClientExceptionHelper(() -> getAsyncAcquisitionUnitPolicies(headers, unitIds));
  }
}
