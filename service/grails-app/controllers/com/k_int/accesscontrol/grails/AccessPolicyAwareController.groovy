package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.GroupedExternalPolicies
import com.k_int.accesscontrol.core.DomainAccessPolicy
import com.k_int.accesscontrol.core.http.bodies.PolicyLink
import com.k_int.accesscontrol.core.http.filters.PoliciesFilter
import com.k_int.accesscontrol.core.http.responses.CanAccessResponse
import com.k_int.accesscontrol.core.http.responses.OwnerPolicyLinkList
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadata
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.EnrichedRestrictionTree
import com.k_int.accesscontrol.core.policyengine.EvaluatedClaimPolicies
import com.k_int.accesscontrol.core.sql.AccessControlSql
import com.k_int.accesscontrol.core.AccessPolicyQueryType
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.sql.AccessControlSqlType
import com.k_int.accesscontrol.core.sql.PolicySubquery
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters
import com.k_int.accesscontrol.grails.criteria.AccessControlHibernateTypeMapper
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.grails.criteria.MultipleAliasSQLCriterion
import com.k_int.utils.Json
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import org.hibernate.Criteria
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.query.NativeQuery
import org.hibernate.type.Type
import org.json.JSONException
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.PostConstruct
import java.time.Duration

import static org.springframework.http.HttpStatus.*
/**
 * Extends com.k_int.okapi.OkapiTenantAwareController to incorporate access policy enforcement for resources.
 * This controller provides methods to check read, update, and delete permissions based on defined
 * access policies, including handling complex ownership chains and dynamic policy evaluation.
 *
 * @param <T> The type of the resource entity managed by this controller.
 */
@CurrentTenant
class AccessPolicyAwareController<T> extends PolicyEngineController<T> {
  /**
   * The alias name for the domain access policy (AccessPolicyEntity) table in SQL queries
   */
  final static String DOMAIN_ACCESS_POLICY_TABLE_ALIAS = "domain_access_policies"

  /**
   * The Class object representing the resource entity type managed by this controller.
   */
  final Class<T> resourceClass

  /**
   * Manages policy-controlled metadata and ownership chains for the resource.
   */
  final PolicyControlledManager policyControlledManager

  // We need to inject the hibernate session factory to map between AccessControl types and Hibernate types while obeying dialect etc etc.
  @Autowired
  SessionFactory hibernateSessionFactory

  /**
   * A mapper for converting AccessControl SQL types to Hibernate types.
   */
  AccessControlHibernateTypeMapper typeMapper // Initialised in PostConstruct


  /**
   * Constructs an {@code AccessPolicyAwareController} for a given resource class.
   * @param resource The Class object of the resource entity.
   */
  AccessPolicyAwareController(Class<T> resource) {
    super(resource)
    this.resourceClass = resource
    this.policyControlledManager = new PolicyControlledManager(resource)
  }

  /**
   * Constructs an {@code AccessPolicyAwareController} for a given resource class,
   * with an option to mark it as read-only.
   * @param resource The Class object of the resource entity.
   * @param readOnly A boolean indicating if the controller should operate in read-only mode.
   */
  AccessPolicyAwareController(Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
    this.resourceClass = resource
    this.policyControlledManager = new PolicyControlledManager(resource)
  }

  /**
   * Initializes the AccessControlHibernateTypeMapper once dependencies are injected.
   */
  @PostConstruct
  void initTypeMapper() {
    // hibernateSessionFactory is guaranteed to be injected by now
    this.typeMapper = new AccessControlHibernateTypeMapper(hibernateSessionFactory as SessionFactoryImplementor)
  }


  /**
   * Generates a list of SQL fragments (policy subqueries) based on a given policy restriction,
   * query type, and the resource ID to which the policy applies.
   * This method communicates with the {@link PolicyEngine} to retrieve the policy definitions
   * and formats them into SQL suitable for database queries.
   *
   * @param restriction The type of policy restriction (e.g., READ, UPDATE, DELETE).
   * @param queryType The type of query (e.g., SINGLE for individual resource checks, LIST for collection checks).
   * @param leafResourceId The ID of the resource in hand we apply the policy against. Can be {@code null} for LIST queries.
   * @return A list of SQL string fragments representing the access policies.
   */
  protected List<AccessControlSql> getPolicySql(PolicyRestriction restriction, AccessPolicyQueryType queryType, String leafResourceId) {
    return getPolicySql(restriction, queryType, leafResourceId, null)
  }

  /**
   * Generates a list of SQL fragments (policy subqueries) based on a given policy restriction,
   * query type, the resource ID to which the policy applies, and optional policy filters.
   * This method communicates with the {@link PolicyEngine} to retrieve the policy definitions
   * and formats them into SQL suitable for database queries.
   *
   * @param restriction The type of policy restriction (e.g., READ, UPDATE, DELETE).
   * @param queryType The type of query (e.g., SINGLE for individual resource checks, LIST for collection checks).
   * @param resourceId The ID of the resource in hand we apply the policy against. Can be {@code null} for LIST queries.
   * @param filters Optional list of {@link PoliciesFilter} to refine which policies are considered.
   * @return A list of SQL string fragments representing the access policies.
   */
  protected List<AccessControlSql> getPolicySql(PolicyRestriction restriction, AccessPolicyQueryType queryType, String resourceId, List<PoliciesFilter> filters) {
    /* ------------------------------- ACTUALLY DO THE WORK FOR EACH POLICY RESTRICTION ------------------------------- */

    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    // Attempt to work out which policySubqueries and which subqueryParams we will need
    // Starting at the leaf, work out whether we need to get hold of subqueries at this level, pass through to parent (with mapping?) or break out.

    // TODO I'm not 100% behind this approach, but it definitely feels like we're keeping the framework layer and engine layer more separate
    GrailsOwnerLevelParameterProvider parameterProvider = new GrailsOwnerLevelParameterProvider(
      typeMapper,
      policyControlledManager,
      resourceId,
      restriction != PolicyRestriction.CREATE ? 0 : 1 // For CREATE we are working on the owner NOT on the leaf object itself since it doesn't yet have an id
    )

    // Is this the right pattern?
    EnrichedRestrictionTree restrictionTree = policyEngine.enrichRestrictionTree(
      grailsHeaders,
      queryType,
      filters,
      policyControlledManager.getRestrictionTreeMap().get(restriction),
      parameterProvider
    )

    // TODO See comment about "orphaned" CREATE elsewhere in the controller. If we are performing a CREATE and we have
    //  no resourceId then for now assume that the restriction tree is invalid beyond the current level.
    //  It may eventually be necessary to instead walk up the tree to the first non-CREATE mapping, as being able to
    //  CREATE on the parent resource is also not id bound. But I'm leaving that use case unfinished right now as it's
    //  not one that we need.
    if (!resourceId && restriction == PolicyRestriction.CREATE) {
      restrictionTree.setParent(null)
    }

    // We now have the complex Map from above ready to build the params and combine the SQL
    return restrictionTree.getSql()
  }


  // CLAIM is not supported with canAccess, and is instead handled by the AccessPolicyController
  /* --------------------- DYNAMICALLY ASSIGNED ACCESSCONTROL METHODS --------------------- */

  /**
   * Returns a set of {@link PolicyRestriction} enums that are considered valid
   * for single resource access checks (e.g., in {@code canAccess} method).
   *
   * <p>Individual controllers can override this method to customize which
   * policy restrictions are supported for direct access checks.</p>
   *
   * <p>The {@code @SuppressWarnings('GrMethodMayBeStatic')} annotation is used
   * to suppress IDE warnings, as this method might be overridden in subclasses
   * where it could potentially depend on instance state.</p>
   *
   * @return An {@link EnumSet} containing the valid policy restrictions for access checks.
   */
  protected static Set<PolicyRestriction> getCanAccessValidPolicyRestrictions() {
    return EnumSet.of(
      PolicyRestriction.CREATE,
      PolicyRestriction.DELETE,
      PolicyRestriction.UPDATE,
      PolicyRestriction.READ,
      PolicyRestriction.APPLY_POLICIES,
    )
  }

  /**
   * Determines if a single resource can be accessed for a given {@link PolicyRestriction}.
   * This method resolves the root owner ID (if applicable), retrieves policy SQL fragments,
   * and executes a native SQL query combining these fragments to check access.
   *
   * @param pr The {@link PolicyRestriction} to check. The validity of this restriction
   * is determined by {@link #getCanAccessValidPolicyRestrictions()}.
   * @return {@code true} if access is allowed according to the policies, {@code false} otherwise.
   *
   * @throws PolicyEngineException if the provided restriction type is not supported as per {@link #getCanAccessValidPolicyRestrictions()}.
   */
  protected boolean canAccess(PolicyRestriction pr) {
    AccessPolicyEntity.withSession { Session sess ->
      // Handle OWNER logic

      if (!getCanAccessValidPolicyRestrictions().contains(pr)) {
        throw new PolicyEngineException("Restriction: ${pr.toString()} is not accessible here", PolicyEngineException.INVALID_RESTRICTION)
      }

      // We have a valid restriction, lets get the policySql

      // For CREATE we need to include the owner id, not the params id, where we are creating a child object
      String resourceId = params.id
      if (
        pr == PolicyRestriction.CREATE &&
          policyControlledManager.hasOwners()
      ) {
        JSONObject body = getObjectToBind() as JSONObject
        String ownerField = policyControlledManager.getOwnerLevelMetadata(0).getOwnerField()

        // Check whether the POST body has an owner
        if (body.has(ownerField)) {
          try {
            def rawOwnerValue = body.get(ownerField)

            // We have historically allowed both "owner": "the-owner-uuid" AND "owner": { "id": "the-owner-uuid" } so allow for either here
            if (rawOwnerValue instanceof String) {
              resourceId = rawOwnerValue
            } else {
              String ownerIdField = policyControlledManager.getOwnerLevelMetadata(1).getResourceIdField()
              resourceId = rawOwnerValue.get(ownerIdField)
            }
          } catch (JSONException jex) {
            throw new PolicyEngineException("Error attempting to resolve owner identifier from CREATE body", PolicyEngineException.GENERIC_ERROR, jex)
          }
        } else {
          // TODO In some cases we may have an "orphaned" CREATE. As things stand if a CREATE call comes in with no owner
          //  we will let it through here, it's not up to this library to prevent something that MUST have an owner not
          //  having one. However we _do_ have an issue if an orphaned create comes through and there are standalone CREATE
          //  calls AND mapped parent (say UPDATE) calls. UPDATE will sometimes require an id, and so it is up to the
          //  library to understand that we are in an "orphaned CREATE" situation and to only perform CREATE type checks
          //  at the base level. See the sibling comment inside getPolicySql for how we handle null resourceId in the
          //  CREATE case. If this solution doesn't prove sufficient then this logic may need revisiting.
        }
      }

      List<AccessControlSql> policySqlFragments = getPolicySql(pr, AccessPolicyQueryType.SINGLE, resourceId)

      // If we have no SQL here, just shortcut to true
      if (policySqlFragments.size() == 0) {
        return true
      }

      log.trace("AccessControl generated PolicySql: ${policySqlFragments.collect{ it.sqlString }.join(', ')}")
      log.trace("AccessControl generated PolicySql parameters: ${policySqlFragments.collect{ it.parameters.collect { it.toString() } }.join(', ')}")
      log.trace("AccessControl generated PolicySql types: ${policySqlFragments.collect{ it.types.collect { it.toString() } }.join(', ')}")

      boolean result = performHibernateQuery(buildCanAccessSql(policySqlFragments), null)[0]
      return result
    }
  }

  boolean canUserRead() {
    return canAccess(PolicyRestriction.READ)
  }

  boolean canUserUpdate() {
    return canAccess(PolicyRestriction.UPDATE)
  }

  boolean canUserDelete() {
    return canAccess(PolicyRestriction.DELETE)
  }

  boolean canUserCreate() {
    return canAccess(PolicyRestriction.CREATE)
  }

  boolean canUserApplyPolicies() {
    return canAccess(PolicyRestriction.APPLY_POLICIES)
  }

  /**
   * Checks if the currently authenticated user has read access to the resource identified by {@code params.id}.
   * The result is returned in the response map.
   */
  @Transactional
  def canRead() {
    log.trace("AccessPolicyAwareController::canRead")
    respond CanAccessResponse.builder().canRead(canUserRead()).build()
  }

  /**
 * Checks if the currently authenticated user has update access to the resource identified by {@code params.id}.
 * The result is returned in the response map.
 */
  @Transactional
  def canUpdate() {
    log.trace("AccessPolicyAwareController::canUpdate")
    respond CanAccessResponse.builder().canUpdate(canUserUpdate()).build()
  }

  /**
   * Checks if the currently authenticated user has delete access to the resource identified by {@code params.id}.
   * The result is returned in the response map.
   */
  @Transactional
  def canDelete() {
    log.trace("AccessPolicyAwareController::canDelete")
    respond CanAccessResponse.builder().canDelete(canUserDelete()).build()
  }

  /**
   * Checks if the currently authenticated user has permission to create new resources of the type managed by this controller.
   * The result is returned in the response map.
   */
  @Transactional
  def canCreate() {
    log.trace("AccessPolicyAwareController::canCreate")
    respond CanAccessResponse.builder().canCreate(canUserCreate()).build()
  }

  /**
   * Checks if the currently authenticated user can apply policies to the resource identified by {@code params.id}.
   * This method is used to determine if the user has permission to apply access policies to the resource.
   * The result is returned in the response map.
   */
  @Transactional
  def canApplyPolicies() {
    log.trace("AccessPolicyAwareController::canApplyPolicies")
    respond CanAccessResponse.builder().canApplyPolicies(canUserApplyPolicies()).build()
  }

  /**
   * Checks if a given list of policies are valid for a specific policy restriction.
   * This method consults the {@link PolicyEngine} to validate the policies against
   * the current user's permissions and the specified restriction.
   *
   * @param pr The {@link PolicyRestriction} to check against.
   * @param policies A list of {@link GroupedExternalPolicies} representing the policies to validate.
   * @return {@code true} if all provided policies are valid for the given restriction, {@code false} otherwise.
   */
  protected boolean arePoliciesValid(PolicyRestriction pr, List<GroupedExternalPolicies> policies) {
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)
    return policyEngine.arePoliciesValid(grailsHeaders, pr, policies)
  }

/**
 * Checks if a given list of policies are valid for the {@code CREATE} policy restriction.
 *
 * @param policyIds A list of {@link GroupedExternalPolicies} representing the policies to validate.
 * @return {@code true} if all provided policies are valid for CREATE, {@code false} otherwise.
 */
  protected boolean areCreatePoliciesValid(List<GroupedExternalPolicies> policies) {
    return arePoliciesValid(PolicyRestriction.CREATE, policies)
  }

  /**
   * Checks if a given list of policies are valid for the {@code READ} policy restriction.
   *
   * @param policyIds A list of {@link com.k_int.accesscontrol.core.GroupedExternalPolicies} representing the policies to validate.
   * @return {@code true} if all provided policies are valid for READ, {@code false} otherwise.
   */
  protected boolean areReadPoliciesValid(List<GroupedExternalPolicies> policies) {
    return arePoliciesValid(PolicyRestriction.READ, policies)
  }

  /**
   * Checks if a given list of policies are valid for the {@code UPDATE} policy restriction.
   *
   * @param policyIds A list of {@link GroupedExternalPolicies} representing the policies to validate.
   * @return {@code true} if all provided policies are valid for UPDATE, {@code false} otherwise.
   */
  protected boolean areUpdatePoliciesValid(List<GroupedExternalPolicies> policies) {
    return arePoliciesValid(PolicyRestriction.UPDATE, policies)
  }

  /**
   * Checks if a given list of policies are valid for the {@code DELETE} policy restriction.
   *
   * @param policyIds A list of {@link GroupedExternalPolicies} representing the policies to validate.
   * @return {@code true} if all provided policies are valid for DELETE, {@code false} otherwise.
   */
  protected boolean areDeletePoliciesValid(List<GroupedExternalPolicies> policies) {
    return arePoliciesValid(PolicyRestriction.DELETE, policies)
  }

  /**
   * Checks if a given list of policies are valid for the {@code CLAIM} policy restriction.
   *
   * @param policyIds A list of {@link GroupedExternalPolicies} representing the policies to validate.
   * @return {@code true} if all provided policies are valid for CLAIM, {@code false} otherwise.
   */
  protected boolean areClaimPoliciesValid(List<GroupedExternalPolicies> policies) {
    return arePoliciesValid(PolicyRestriction.CLAIM, policies)
  }

  /**
   * Checks if a given list of policies are valid for the {@code APPLY_POLICIES} policy restriction.
   *
   * @param policyIds A list of {@link GroupedExternalPolicies} representing the policies to validate.
   * @return {@code true} if all provided policies are valid for APPLY_POLICIES, {@code false} otherwise.
   */
  protected boolean areApplyPoliciesPoliciesValid(List<GroupedExternalPolicies> policies) {
    return arePoliciesValid(PolicyRestriction.APPLY_POLICIES, policies)
  }


  // Comment out to allow quick revert to default index behaviour for development
  /**
   * Overrides the default index method to apply access control policies when listing resources.
   * This method uses the {@link AccessPolicyEntity} to enforce {@link PolicyRestriction#READ} restrictions based on the user's access policies.
   * It constructs SQL criteria dynamically based on the configured ownership chain and policy restrictions.
   */
  @Transactional
  def index(Integer max) {
    // Protect the index method with access control -- replace the built in "index" method
    AccessPolicyEntity.withSession {
      // We have special logic for filtering by access control policies here.
      // If the user sends down queryParams policiesFilter=A,B,C then we will perform an ORed filter on those policies
      // If the user sends down queryParams policiesFilter=A, policiesFilter=B, policiesFilter=C then we will perform an ANDed filter on those policies.

      // This means we can perform a query like: ( A OR B ) AND C, but can't go arbitrarily deep at the moment.

      // Policy filters (eg "A") MUST be formatted like: "AccessPolicyType:AccessPolicyEntity.id" eg "ACQ_UNIT:e7aa4d3a-d686-42b4-8371-a7055ce95239"
      // Build the PoliciesFilter list here from the params pushed down.
      Collection<String> policiesFilterParams = params.list('policiesFilter') // This will be a List of Strings, or an empty list if not present
      List<PoliciesFilter> policiesFilters = PoliciesFilter.fromStringCollection(policiesFilterParams)

      // Remove policiesFilter params from the params map so that the default criteria builder doesn't try to handle them
      params.remove('policiesFilter')

      log.trace("policiesFilters: ${"(" + policiesFilters.collect{ it.filters.collect {"${it.type}:[${it.policies.collect {it.id }.join(', ')}]"}.join(', ') }.join(', ') + ")"}")

      List<AccessControlSql> policySql = getPolicySql(PolicyRestriction.READ, AccessPolicyQueryType.LIST, null, policiesFilters)
      log.trace("AccessControl generated PolicySql: ${policySql.collect{ it.sqlString }.join(', ')}")
      log.trace("AccessControl generated PolicySql parameters: ${policySql.collect{ it.parameters.collect { it.toString() } }.join(', ')}")
      log.trace("AccessControl generated PolicySql types: ${policySql.collect{ it.types.collect { it.toString() } }.join(', ')}")

      long beforeLookup = System.nanoTime()
      respond doTheLookup(resourceClass) {

        // To handle nested levels of ownership, we have pre-parsed the owner tree
        MultipleAliasSQLCriterion.SubCriteriaAliasContainer[] subCriteria = policyControlledManager.getNonLeafOwnershipChain().collect { pcm ->
          Criteria aliasCriteria = criteria.createCriteria(pcm.getAliasOwnerField(), pcm.getAliasName())
          return new MultipleAliasSQLCriterion.SubCriteriaAliasContainer(pcm.getAliasName(), aliasCriteria)
        }

        // This is effectively an AND across all policySql entries
        // We would need Restrictions.disjunction for OR
        policySql.each {psql ->
          String sqlString = psql.getSqlString()
          Object[] parameters = psql.getParameters()
          Type[] types = psql.getTypes().collect { acst -> typeMapper.getHibernateType(acst) } as Type[]

          criteria.add(new MultipleAliasSQLCriterion(sqlString, parameters, types, subCriteria))
        }

        // Ensure we return criteria at the bottom?
        return criteria
      }

      long afterLookup = System.nanoTime()
      log.trace("AccessPolicyAwareController::testReadRestrictedList query time: {}", Duration.ofNanos(afterLookup - beforeLookup))
    }
  }

  /**
   * Overrides the show method to apply access control policies when retrieving a single resource.
   * This method checks if the user has read access to the resource identified by {@code params.id}
   * and responds accordingly.
   *
   * @return A response containing the resource if access is granted, or an error message if access is denied.
   */
  @Transactional
  def show() {
    if (canUserRead()) {
      super.show()
      return
    }

    respond ([ message: "PolicyRestriction.READ check failed in access control" ], status: FORBIDDEN )
  }

  /**
   * Overrides the save method to apply access control policies when creating a new resource.
   * This method checks if the user has create access and responds accordingly.
   *
   * @return A response containing the created resource if access is granted, or an error message if access is denied.
   */
  def save() {
    if (canUserCreate()) {
      super.save()
      return
    }

    respond ([ message: "PolicyRestriction.CREATE check failed in access control" ], status: FORBIDDEN )
  }

  /**
   * Overrides the update method to apply access control policies when updating an existing resource.
   * This method checks if the user has update access to the resource identified by {@code params.id}
   * and responds accordingly.
   *
   * @return A response containing the updated resource if access is granted, or an error message if access is denied.
   */
  @Transactional
  def update() {
    if (canUserUpdate()) {
      super.update()
      return
    }

    respond ([ message: "PolicyRestriction.UPDATE check failed in access control" ], status: FORBIDDEN )
  }

  /**
   * Overrides the delete method to apply access control policies when deleting a resource.
   * This method checks if the user has delete access to the resource identified by {@code params.id}
   * and responds accordingly.
   *
   * @return A response indicating successful deletion if access is granted, or an error message if access is denied.
   */
  def delete() {
    if (!canUserDelete()) {
      // Overwrite the default behaviour
      request.withFormat {
        '*' { respond([message: "PolicyRestriction.DELETE check failed in access control"], status: FORBIDDEN) }
      }
      return
    }

    AccessPolicyEntity.withTransaction { transactionStatus -> {
      try {
        // We also need to get rid of any AccessPolicyEntity objects for this level

        if (policyControlledManager.hasStandalonePolicies(0)) {
          String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

          List<PolicySubquery> apeSubqueryList = policyEngine.getPolicyEntitySubqueries(grailsHeaders)

          // Getting owner ids with a parameter provider
          GrailsOwnerLevelParameterProvider parameterProvider = new GrailsOwnerLevelParameterProvider(
            typeMapper,
            policyControlledManager,
            params.id,
            0,
            DOMAIN_ACCESS_POLICY_TABLE_ALIAS
          )

          List<AccessControlSql> accessControlSqlList = apeSubqueryList.collect {it.getSql(parameterProvider.provideParameters(0))}
          List<AccessPolicyEntity> entityList = performHibernateQuery(buildAccessPolicyEntitySql(accessControlSqlList), AccessPolicyEntity.class)

          entityList.forEach {ape -> {
            ape.delete()
          }}
        }

        // Perform the original delete from super controller if we got to this stage
        super.delete()
      } catch (Exception e) {
        String failureMessage = "Failed to delete entity or associated AccessPolicyEntity objects"
        log.error(failureMessage, e)

        // Some of the deletes failed, rollback here.
        transactionStatus.setRollbackOnly()
        request.withFormat {
          '*' { respond([message: failureMessage], status: INTERNAL_SERVER_ERROR) }
        }
      }
    }}
  }

  /**
   * Handles the claiming of a resource by checking if the user has permission to apply policies.
   * If the user has the {@link PolicyRestriction#APPLY_POLICIES} permission, it processes the claim.
   * Otherwise, it responds with a 403 Forbidden status.
   *
   * The POST body should contain the policies to be applied to the resource,
   * and these are expected to be valid for the {@link PolicyRestriction#CLAIM} restriction.
   * DOES NOT follow the _delete pattern, as it is not a GORM entity. Instead a POST without some AccessPolicyEntity id will remove said policy
   *
   * @return A response indicating the result of the claim operation.
   */
  def claim(GrailsClaimBody claimBody) {
    if (claimBody.hasErrors()) {
      // If there are errors, respond with a 400 Bad Request and the errors object
      respond claimBody.errors, status: BAD_REQUEST
      return
    }

    if (!policyControlledManager.hasStandalonePolicies() && policyControlledManager.hasOwners()) {
      // If there are owners and the resource does NOT support standalone policies then don't allow claiming
      String message = "Claiming is not supported on resource ${resourceClass.getName()}"

      respond ([ message: message ], status: METHOD_NOT_ALLOWED )
      return
    }

    // Strategy - Check whether APPLY_POLICIES is allowed on the resource, and if so, then check whether all policies in the request body are valid for CLAIM.
    if (!canUserApplyPolicies()) {
      respond ([ message: "PolicyRestriction.APPLY_POLICIES check failed in access control" ], status: FORBIDDEN )
      return
    }

    // At this point, we know that the user has permission to apply policies

    // Get the identifier from the request params
    String resourceId = params.id

    boolean success = true
    boolean changesMade = true
    String failureMessage = ''
    int failureCode = BAD_REQUEST.value()
    AccessPolicyEntity.withSession { sess ->
      AccessPolicyEntity.withTransaction { transactionStatus ->
        // Fetch the original policies for this resource
        List<AccessPolicyEntity> accessPoliciesForResource = AccessPolicyEntity.findAllByResourceIdAndResourceClass(resourceId, resourceClass.getName())

        // Set up the evaluated claim policies object
        EvaluatedClaimPolicies evaluatedClaimPolicies
        try {
          // Attempt to evaluate the claimBody against the existing policies for this resource, returning the policies to add/remove/update
          evaluatedClaimPolicies = policyEngine.evaluateClaimPolicies(claimBody, accessPoliciesForResource, resourceId, resourceClass.getName())
        } catch (PolicyEngineException pee) {
          // We can catch the PolicyEngineException here and return a 400 with the message -- we're expecting it in cases where the changed policies are invalid for some reason
          if ([
            PolicyEngineException.ACCESS_POLICY_ID_NOT_FOUND,
            PolicyEngineException.ACCESS_POLICY_RESOURCE_ID_DOES_NOT_MATCH,
            PolicyEngineException.ACCESS_POLICY_RESOURCE_CLASS_DOES_NOT_MATCH,
            PolicyEngineException.PREEXISTING_ACCESS_POLICY_FOR_POLICY_ID
          ].contains(pee.code)) {
            success = false
            failureMessage = pee.getMessage()
            return // Kick out to the post-transaction success check
          } else {
            throw pee
          }
        }

        if (
          evaluatedClaimPolicies.policiesToAdd.size() == 0 &&
          evaluatedClaimPolicies.policiesToRemove.size() == 0 &&
          evaluatedClaimPolicies.policiesToUpdate.size() == 0
        ) {
          // No changes to make, so just return a 200 OK
          success = true
          changesMade = false
          return // Kick out to the post-transaction success check
        }

        // We must now check whether all policies to add/remove/update are valid for CLAIM
        // EvaluatedClaimPolicies includes a helper method to transform to List<GroupedPolicyList> for use in arePoliciesValid
        List<GroupedExternalPolicies> changedPolicies = evaluatedClaimPolicies.changedPolicies()

        if (!areClaimPoliciesValid(changedPolicies)) {
          success = false
          failureMessage = "PolicyRestriction.CLAIM not valid for one or more changed policies in claims"
          failureCode = FORBIDDEN.value()
          log.error(failureMessage)
          return // Kick out to the post-transaction success check
        }

        // Finally we can get to the actual DB changes

        try {
          // Firstly delete any AccessPolicyEntities for this resource NOT in the claimBody
          // We will then add/update policies from the claimBody
          // We do the delete first so that if we are accidentally replacing a policy like-for-like without an id,
          // we don't fail to add it thanks to duplicate check below, and then remove it, leaving resource unprotected
          for (DomainAccessPolicy policy : evaluatedClaimPolicies.policiesToRemove) {
            AccessPolicyEntity policyEntity = accessPoliciesForResource.find {AccessPolicyEntity ape -> ape.id == policy.id }

            // This shouldn't happen since the removed ids will be from the existing policies, but just in case
            if (!policyEntity) {
              // This is happening in a try/catch, so we can just throw here and it will be caught below and rolled back without attempting the next updates
              throw new RuntimeException("Could not find AccessPolicyEntity with id ${policy.id} to remove")
            }

            policy.delete(flush: true, failOnError: true)
          }

          // Now we can update policies from the claimBody
          for (DomainAccessPolicy policy  : evaluatedClaimPolicies.policiesToUpdate) {
            AccessPolicyEntity policyEntity = accessPoliciesForResource.find {AccessPolicyEntity ape -> ape.id == policy.id }

            // This shouldn't happen since the updated ids will be from the existing policies, but just in case
            if (!policyEntity) {
              // This is happening in a try/catch, so we can just throw here and it will be caught below and rolled back without attempting the next updates
              throw new RuntimeException("Could not find AccessPolicyEntity with id ${policy.id} to update")
            }

            policyEntity.description = policy.description
            policyEntity.save(flush: true, failOnError: true)
          }

          // Finally we can add any new policies from the claimBody
          for (DomainAccessPolicy policy  : evaluatedClaimPolicies.policiesToAdd) {
            new AccessPolicyEntity(
              policyId: policy.policyId,
              type: policy.type,
              description: policy.description,
              resourceId: resourceId,
              resourceClass: resourceClass.getName()
            ).save(flush: true, failOnError: true)
          }
        } catch(Exception e) {
          // Something went wrong with the DB changes -- Rollback and return 500
          success = false
          failureMessage = "Something went wrong saving access policies to the database"
          failureCode = INTERNAL_SERVER_ERROR.value()
          log.error("${failureMessage}: ${e.getMessage()}", e)

          transactionStatus.setRollbackOnly()
        }
      }
      sess.flush() // Ensure all changes are flushed to the database
    }

    // If the claims failed, respond with the failure message and code
    if (!success) {
      respond ([ message: failureMessage ], status: failureCode )
      return
    }

    if (!changesMade) {
      respond ([ message: "No changes to the access policies for this resource" ], status: OK )
      return
    }

    respond ([ message: "Access policies updated for this resource" ], status: CREATED )
  }

  @Transactional
  def policies() {
    AccessPolicyEntity.withSession { Session sess ->
      String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

      List<PolicySubquery> apeSubqueryList = policyEngine.getPolicyEntitySubqueries(grailsHeaders)

      // Getting owner ids with a parameter provider
      GrailsOwnerLevelParameterProvider parameterProvider = new GrailsOwnerLevelParameterProvider(
        typeMapper,
        policyControlledManager,
        params.id,
        0,
        DOMAIN_ACCESS_POLICY_TABLE_ALIAS
      )

      List<OwnerPolicyLinkList> ownerPolicyLinks = new ArrayList<>()

      // We're going to build this map.
      Map<String, List<AccessPolicyEntity>> policyEntityMap = new HashMap<>()
      // For each owner level, get all relevant policies.
      policyControlledManager.ownershipChain.stream().forEach { PolicyControlledMetadata metadata -> {
        String ownerId = parameterProvider.ownerIdProvider(metadata.ownerLevel)

        OwnerPolicyLinkList ownerPolicyLinkList = OwnerPolicyLinkList.builder()
          .resourceClass(metadata.getResourceClassName())
          .ownerLevel(metadata.getOwnerLevel())
          .ownerId(ownerId)
          .build()

        // Check if we have any policies at this level
        if (policyControlledManager.hasStandalonePolicies(metadata.ownerLevel)) {
          ownerPolicyLinkList.hasStandalonePolicies(true)
          PolicySubqueryParameters params = parameterProvider.provideParameters(metadata.ownerLevel)

          // Set up list of AccessControlSql
          List<AccessControlSql> accessControlSqlList = apeSubqueryList.collect {it.getSql(params)}


          List<AccessPolicyEntity> entityList = performHibernateQuery(buildAccessPolicyEntitySql(accessControlSqlList), AccessPolicyEntity.class)

          policyEntityMap.put(ownerId, entityList)
        }

        ownerPolicyLinks.add(ownerPolicyLinkList)
      }}

      // We have a Map from id -> List<AccessPolicyEntities>. We want to enrich these into PolicyLinks to render out,
      // but don't want to make multiple calls to the policyEngine. So instead we'll flatten to a single list, then map
      // back onto the proper shape after enrichment
      Set<AccessPolicyEntity> flattenedAccessPolicyEntities = policyEntityMap.values().flatten().toSet() as Set<AccessPolicyEntity>
      List<PolicyLink> enrichedPolicyLinks = policyEngine.getPolicyLinksFromAccessPolicyList(grailsHeaders, flattenedAccessPolicyEntities)


      ownerPolicyLinks.forEach { ownerPolicyLinkList -> {
        List<PolicyLink> relevantPolicyLinks = policyEntityMap.get(ownerPolicyLinkList.getOwnerId()).collect(ape -> enrichedPolicyLinks.find(epl -> epl.getId() === ape.getId()))
        ownerPolicyLinkList.setPolicies(relevantPolicyLinks)
      }}

      // Grails gets confused with the Policy extensions, so instead lets render it out with Jackson
      render text: Json.toJson(ownerPolicyLinks), contentType: 'application/json'
    }
  }

  /* **************** SQL HELPERS **************** */
  protected static AccessControlSql buildAccessPolicyEntitySql(Collection<AccessControlSql> subqueries) {
    AccessControlSql combinedSubqueries = AccessControlSql.combineSqlSubqueries(subqueries, " OR ")

    String sqlString = "SELECT * FROM ${AccessPolicyEntity.TABLE_NAME} AS ${DOMAIN_ACCESS_POLICY_TABLE_ALIAS} WHERE ${combinedSubqueries.getSqlString()}"

    return AccessControlSql.builder()
      .sqlString(sqlString)
      .parameters(combinedSubqueries.getParameters())
      .types(combinedSubqueries.getTypes())
      .build()
  }

  protected static AccessControlSql buildCanAccessSql(Collection<AccessControlSql> subqueries) {
    AccessControlSql combinedSubqueries = AccessControlSql.combineSqlSubqueries(subqueries, " AND ")

    String sqlString = "SELECT ${combinedSubqueries.getSqlString()} AS access_allowed"

    return AccessControlSql.builder()
      .sqlString(sqlString)
      .parameters(combinedSubqueries.getParameters())
      .types(combinedSubqueries.getTypes())
      .build()
  }

  protected <S> List<S> performHibernateQuery(AccessControlSql theSql, Class<S> clazz) {
    // We know this class exists, so use it to get a hold of the session.
    AccessPolicyEntity.withSession { Session sess ->
      NativeQuery nativeQuery = sess.createNativeQuery(theSql.getSqlString())

      if (clazz != null) {
        nativeQuery.addEntity(clazz) // Ensure we get back the right class
      }

      // Set all the parameters and types on the request
      theSql.getParameters().eachWithIndex{ Object param, int i ->
        nativeQuery.setParameter(i + 1, param, (Type) typeMapper.getHibernateType((AccessControlSqlType) theSql.getTypes()[i]))
      }

      return nativeQuery.list()
    }
  }
}