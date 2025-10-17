package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.*;
import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.core.http.bodies.ClaimBody;
import com.k_int.accesscontrol.core.http.bodies.PolicyLink;
import com.k_int.accesscontrol.core.http.filters.PoliciesFilter;
import com.k_int.accesscontrol.core.policyengine.EvaluatedClaimPolicies;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineImplementor;
import com.k_int.accesscontrol.core.sql.FilterPolicySubquery;
import com.k_int.accesscontrol.core.sql.PolicySubquery;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core entry point for evaluating policy restrictions within the access control system.
 * The engine fetches necessary access policy data (e.g., from acquisition units)
 * and composes SQL fragments that can be used to filter Hibernate queries.
 * Configuration is driven by `PolicyEngineConfiguration`.
 */
@Slf4j
@SuppressWarnings("javadoc")
public class PolicyEngine implements PolicyEngineImplementor {
  /**
   * Configuration for the policy engine, including whether to use acquisition units.
   * This is set during construction and used to determine which policy types to query.
   * @return The configuration for the policy engine, including acquisition unit settings
   */
  @Getter
  private final PolicyEngineConfiguration config;

  /**
   * The acquisition unit policy engine implementor, which handles policy subquery generation
   * for acquisition units. This is initialized based on the configuration.
   * @return The acquisition unit policy engine implementor, or null if acquisition units are not enabled
   */
  @Getter
  private final AcquisitionUnitPolicyEngine acquisitionUnitPolicyEngine;

  /**
   * Constructs a new PolicyEngine with the provided configuration.
   * Initializes the acquisition unit policy engine if acquisition units are enabled in the configuration.
   *
   * @param config the configuration for the policy engine, including acquisition unit settings
   */
  public PolicyEngine(PolicyEngineConfiguration config) {
    this.config = config;

    // Initialize the acquisition unit policy engine if acquisition units are configured
    AcquisitionUnitPolicyEngineConfiguration acquisitionUnitConfig = config.getAcquisitionUnitPolicyEngineConfiguration();

    if (acquisitionUnitConfig.isEnabled()) {
      this.acquisitionUnitPolicyEngine = new AcquisitionUnitPolicyEngine(config);
    } else {
      this.acquisitionUnitPolicyEngine = null;
    }
    // In future we may add more policy engines here, such as KI Grants
  }

  /**
   * There are two types of AccessPolicy query that we might want to handle, LIST: "Show me all records for which I can do RESTRICTION" and SINGLE: "Can I do RESTRICTION for resource X?"
   *
   * @param headers The request context headers -- used mainly to connect to FOLIO (or other "internal" services)
   * @param pr The policy restriction which we want to filter by
   * @param queryType Whether to return boolean queries for single use or filter queries for all records
   * @return A list of PolicySubqueries, either for boolean restriction or for filtering.
   * @throws PolicyEngineException -- the understanding is that within a request context this should be caught and return a 500
   */
  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType) throws PolicyEngineException {
    List<PolicySubquery> policySubqueries = new ArrayList<>();

    if (pr.equals(PolicyRestriction.CLAIM)) {
      throw new PolicyEngineException("getPolicySubqueries is not valid for PolicyRestriction.CLAIM", PolicyEngineException.INVALID_RESTRICTION);
    }

    if (acquisitionUnitPolicyEngine != null) {
      policySubqueries.addAll(acquisitionUnitPolicyEngine.getPolicySubqueries(headers, pr, queryType));
    }

    return policySubqueries;
  }

  /**
   * Extended version of getPolicySubqueries which also takes in a list of PoliciesFilter objects.
   * These filters will be ANDed together in the final SQL, with the internal list of GroupedExternalPolicies
   * within each PoliciesFilter being ORed together.
   *
   * @param headers The request context headers -- used mainly to connect to FOLIO (or other "internal" services)
   * @param pr The policy restriction which we want to filter by
   * @param queryType Whether to return boolean queries for single use or filter queries for all records
   * @param filters A list of PoliciesFilter objects representing additional filters to apply
   * @return A list of PolicySubqueries, either for boolean restriction or for filtering, including the provided filters
   * @throws PolicyEngineException -- the understanding is that within a request context this should be caught and return a 500
   */
  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType, List<PoliciesFilter> filters) throws PolicyEngineException {

    List<PolicySubquery> policySubqueries = getPolicySubqueries(headers, pr, queryType);
    if (filters == null || filters.isEmpty()) {
      return policySubqueries;
    }

    // We have some filters, so we should create a PolicySubquery for them and add to the list
    FilterPolicySubquery filterPolicySubquery = FilterPolicySubquery.builder()
      .policiesFilters(filters)
      .queryType(queryType)
      .build();
    policySubqueries.add(filterPolicySubquery);

    return policySubqueries;
  }

  /**
   * Retrieves a list of access policies grouped by their type for the given policy restriction.
   * This method is used to fetch valid policies that can be used for operations like claims.
   *
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @param pr      the policy restriction to filter by
   * @return a list of {@link GroupedExternalPolicies} containing policies grouped by type
   * @throws PolicyEngineException if an error occurs while fetching policy IDs
   */
  public List<GroupedExternalPolicies> getRestrictionPolicies(String[] headers, PolicyRestriction pr) throws PolicyEngineException {
    List<GroupedExternalPolicies> policyIds = new ArrayList<>();

    if (acquisitionUnitPolicyEngine != null) {
      policyIds.addAll(acquisitionUnitPolicyEngine.getRestrictionPolicies(headers, pr));
    }

    return policyIds;
  }

  /**
   * Validates the provided policy IDs against the access control system.
   * This method checks if the policy IDs are valid for the given policy restriction.
   *
   * @param headers  the request context headers, used for FOLIO/internal service authentication
   * @param pr       the policy restriction to filter by
   * @param policies the list of policy IDs to validate
   * @return true if all policy IDs are valid, false otherwise
   * @throws PolicyEngineException if an error occurs during validation
   */
  public boolean arePoliciesValid(String[] headers, PolicyRestriction pr, List<GroupedExternalPolicies> policies) throws PolicyEngineException {
    boolean isValid = true;

    // Check if isValid is true for each sub policyEngine, so that we can short-circuit if any engine returns false.
    // No need to check for the first engine, as it will always start true
    if (acquisitionUnitPolicyEngine != null) {
      isValid = acquisitionUnitPolicyEngine.arePoliciesValid(headers, pr, policies.stream().filter(pid -> pid.getType() == AccessPolicyType.ACQ_UNIT).toList());
    }

    return isValid;
  }

  /**
   * A helper method which returns the engines enabled in the config, as sorted by AccessPolicyType
   * @return A map of each AccessPolicyType and whether it is enabled or not
   */
  public Map<AccessPolicyType, Boolean> getEnabledEngines() {
    return Map.of(
      AccessPolicyType.ACQ_UNIT, getConfig().getAcquisitionUnitPolicyEngineConfiguration().isEnabled()
    );
  }

  /**
   * A helper method which returns the engines enabled in the config as a Set
   * @return A set of each enabled AccessPolicyType
   */
  public Set<AccessPolicyType> getEnabledEngineSet() {
    Map<AccessPolicyType, Boolean> enabledEngines = getEnabledEngines();
    return enabledEngines.keySet().stream().filter(key -> enabledEngines.get(key) == true).collect(Collectors.toSet());
  }

  /**
   * A function which takes in a list of {@link GroupedExternalPolicies} objects, likely with a
   * {@link IExternalPolicy} implementation of
   * {@link com.k_int.accesscontrol.core.http.responses.BasicPolicy}
   * @param policies a list of AccessPolicy objects to enrich, it will use the "type" and the "policy.id" fields to enrich
   * with Policy implementations from the individual engine plugins
   * @return A list of AccessPolicy objects with all information provided by the policyEngineImplementors
   */
  public List<GroupedExternalPolicies> enrichPolicies(String[] headers, List<GroupedExternalPolicies> policies) {
    List<GroupedExternalPolicies> enrichedPolicies = new ArrayList<>();

    if (acquisitionUnitPolicyEngine != null) {
      enrichedPolicies.addAll(acquisitionUnitPolicyEngine.enrichPolicies(headers, policies));
    }

    return enrichedPolicies;
  }

  /**
   * Converts a list of {@link IDomainAccessPolicy} entities into a list of {@link PolicyLink} DTOs,
   * preserving enriched policy metadata and per-resource assignment details.
   * <p>
   * This is used primarily when serializing claims or presenting assigned policies
   * for a specific domain object.
   * </p>
   *
   * @param headers        the request headers
   * @param policyEntities the access policy entities assigned to a resource
   * @return a list of enriched {@link PolicyLink} instances
   */
  public List<PolicyLink> getPolicyLinksFromAccessPolicyList(String[] headers, Collection<IDomainAccessPolicy> policyEntities) {
    // We want to turn this into the shape List<PolicyLink> (We want the enriched Policy information)
    // enrichPolicies needs ids and types, so send as GroupedExternalPolicies object

    // This will lose us all id and description information on the AccessPolicy objects for the resource -- we will add those back later
    List<GroupedExternalPolicies> groupedExternalPolicies = GroupedExternalPolicies.fromAccessPolicyList(policyEntities);

    // use "enrich" method from policyEngine to get List<GroupedExternalPolicies>
    List<GroupedExternalPolicies> enrichedGroupedExternalPolicies = enrichPolicies(headers, groupedExternalPolicies);

    // Then convert into List<PolicyLink> so it's in roughly the same shape we'd send down in a ClaimBody
    List<PolicyLink> policyLinkList = GroupedExternalPolicies.convertListToPolicyLinkList(enrichedGroupedExternalPolicies);

    // Finally, we have to add back the descriptions and ids that were set on the AccessPolicyEntities for resource
    // This isn't strictly necessary, but if not done then description will be reset and the policies will churn on each set
    return policyLinkList.stream()
      .map(pll -> {

        Optional<IDomainAccessPolicy> relevantAccessPolicyOpt = policyEntities.stream()
          .filter(ape -> Objects.equals(ape.getPolicyId(), pll.getPolicy().getId()))
          .findFirst();

        if (relevantAccessPolicyOpt.isEmpty()) {
          return pll; // Return the PolicyLink as is if we don't have a relevant AccessPolicy from the DB
        }
        IDomainAccessPolicy relevantAccessPolicy = relevantAccessPolicyOpt.get();

        pll.setId(relevantAccessPolicy.getId());
        if (relevantAccessPolicy.getDescription() != null && !Objects.equals(relevantAccessPolicy.getDescription(), pll.getDescription())) {
          pll.setDescription(relevantAccessPolicy.getDescription());
        }

        return pll;
      })
      .toList();
  }

  /**
   * Evaluates an incoming {@link ClaimBody} against existing access policies for a resource,
   * determining which policies need to be added, removed, or updated.
   * <p>
   * This method compares the claims in the {@link ClaimBody} with the provided list of existing
   * {@link IDomainAccessPolicy} objects, identifying discrepancies and preparing lists of policies
   * to be added, removed, or updated accordingly.
   * </p>
   *
   * @param claimBody       the incoming claim body containing desired policy claims
   * @param existingPolicies the current access policies assigned to the resource
   * @param resourceId      the identifier of the resource claims are being evaluated against
   * @param resourceClass  the class/type of the resource claims are being evaluated against
   * @return an {@link EvaluatedClaimPolicies} object containing lists of policies to add, remove, or update
   * @throws PolicyEngineException if validation fails (e.g., non-existent policy IDs, mismatched resource IDs/classes)
   */
  public EvaluatedClaimPolicies evaluateClaimPolicies(
    ClaimBody claimBody,
    List<IDomainAccessPolicy> existingPolicies,
    String resourceId,
    String resourceClass
  ) throws PolicyEngineException {
    // This method will evaulate an incoming claimBody, returning 3 lists of AccessPolicy objects.
    // One each for "add", "remove" and "update".
    // This method will throw if

    List<IDomainAccessPolicy> accessPoliciesToAdd = new ArrayList<>();
    List<IDomainAccessPolicy> accessPoliciesToRemove = new ArrayList<>();
    List<IDomainAccessPolicy> accessPoliciesToUpdate = new ArrayList<>();
    for (IDomainAccessPolicy policy : existingPolicies) {
      if (claimBody.getClaims().stream().noneMatch(claim -> Objects.equals(claim.getId(), policy.getId()))) {
        accessPoliciesToRemove.add(policy);
      }
    }

    for(PolicyLink claim  : claimBody.getClaims()) {
      if (claim.getId() != null) {
        // If the claim has an ID, we assume it is an existing policy that needs to be updated
        IDomainAccessPolicy existingPolicy = existingPolicies.stream().filter (ape -> Objects.equals(ape.getId(), claim.getId())).findFirst().orElse(null);

        // If we're handed a non existing policy ID, we should fail the request
        if (existingPolicy == null) {
          String failureMessage = "Access policy with ID " + claim.getId() + " does not exist.";
          throw new PolicyEngineException(failureMessage, PolicyEngineException.ACCESS_POLICY_ID_NOT_FOUND);
        }

        // If existing policy is for a different resource, we should also fail the request
        if (!Objects.equals(existingPolicy.getResourceId(), resourceId)) {
          String failureMessage = "Access policy " + existingPolicy.getId() + " has resource ID: " + existingPolicy.getResourceId() + " which does not match resource ID " + resourceId + ".";
          throw new PolicyEngineException(failureMessage, PolicyEngineException.ACCESS_POLICY_RESOURCE_ID_DOES_NOT_MATCH);
        }

        // If existing policy is for a different class, we should also fail the request
        if (!Objects.equals(existingPolicy.getResourceClass(), resourceClass)) {
          String failureMessage = "Access policy " + existingPolicy.getId() + " has resource class: " + existingPolicy.getResourceClass() + " which does not match resource class " + resourceClass + ".";
          throw new PolicyEngineException(failureMessage, PolicyEngineException.ACCESS_POLICY_RESOURCE_CLASS_DOES_NOT_MATCH);
        }

        if (!Objects.equals(existingPolicy.getDescription(), claim.getDescription())) {
          // Only add to update list if something has actually changed

          accessPoliciesToUpdate.add(
            // Construct BasicAccessPolicy from PolicyLink to keep all objects in the same shape (We will only be updating the description anyway in the framework layer)
            claim.createBasicAccessPolicy(resourceId, resourceClass)
          );
        }
      } else if (
          // We don't have an ID, so we are creating a new policy
          // Only create if there is no existing policy with the same policyId and type for this resource
          existingPolicies.stream().anyMatch(ap -> Objects.equals(ap.getPolicyId(), claim.getPolicy().getId()) && ap.getType() == claim.getType())
        ) {
        String failureMessage = "Resource " + resourceId + " already has an access policy with policyId " + claim.getPolicy().getId() + ".";
        throw new PolicyEngineException(failureMessage, PolicyEngineException.PREEXISTING_ACCESS_POLICY_FOR_POLICY_ID);
      } else {
        // If no ID, we create a new policy
        accessPoliciesToAdd.add(
          // Construct BasicAccessPolicy from PolicyLink to keep all objects in the same shape
          claim.createBasicAccessPolicy(resourceId, resourceClass)
        );
      }
    }

    return EvaluatedClaimPolicies.builder()
      .policiesToAdd(accessPoliciesToAdd)
      .policiesToRemove(accessPoliciesToRemove)
      .policiesToUpdate(accessPoliciesToUpdate)
      .build();
  }
}
