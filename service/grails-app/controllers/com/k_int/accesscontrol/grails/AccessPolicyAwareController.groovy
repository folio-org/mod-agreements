package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyQueryType
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadata
import com.k_int.accesscontrol.core.PolicyEngineException
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.PolicySubquery
import com.k_int.accesscontrol.core.PolicySubqueryParameters
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.main.PolicyEngineConfiguration
import com.k_int.accesscontrol.grails.criteria.MultipleAliasSQLCriterion

import com.k_int.folio.FolioClientConfig

import com.k_int.okapi.OkapiClient
import com.k_int.okapi.OkapiTenantAwareController
import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import org.hibernate.Criteria
import org.hibernate.Session
import org.springframework.security.core.userdetails.UserDetails

import javax.servlet.http.HttpServletRequest
import java.time.Duration

/**
 * Extends com.k_int.okapi.OkapiTenantAwareController to incorporate access policy enforcement for resources.
 * This controller provides methods to check read, update, and delete permissions based on defined
 * access policies, including handling complex ownership chains and dynamic policy evaluation.
 *
 * @param <T> The type of the resource entity managed by this controller.
 */
class AccessPolicyAwareController<T> extends OkapiTenantAwareController<T> {
  /**
   * The Okapi client used for interacting with the FOLIO Okapi gateway.
   */
  OkapiClient okapiClient
  /**
   * The Class object representing the resource entity type managed by this controller.
   */
  final Class<T> resourceClass

  /**
   * Manages policy-controlled metadata and ownership chains for the resource.
   */
  final PolicyControlledManager policyControlledManager

  /**
   * Converts HTTP request headers into a simple String array of key-value pairs.
   * This is used to pass headers to the policy engine for context.
   * @param req The HttpServletRequest object.
   * @return A String array where elements alternate between header names and header values.
   */
  private static String[] convertGrailsHeadersToStringArray(HttpServletRequest req) {
    List<String> result = new ArrayList<>()

    Collections.list(req.getHeaderNames()).forEach(headerName -> {
      Collections.list(req.getHeaders(headerName)).forEach(headerValue -> {
        result.add(headerName)
        result.add(headerValue)
      })
    })

    return result as String[]
  }

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
   * Resolves the ID of the ultimate owner (root) in an ownership chain, starting from a leaf resource ID.
   * If no ownership chain is configured, the leaf resource itself is considered the root.
   * This method dynamically constructs an HQL query to traverse the chain and fetch the root ID.
   *
   * @param leafResourceId The ID of the leaf resource for which to find the root owner.
   * @return The ID of the root owner, or the {@code leafResourceId} if no owners are configured or resolution fails.
   */
  protected String resolveRootOwnerId(String leafResourceId) {
    if (!policyControlledManager.hasOwners()) {
      // If there are no configured owners, the leaf resource itself is the "root" for policy purposes
      return leafResourceId
    }
    List<PolicyControlledMetadata> ownershipChain = policyControlledManager.getOwnershipChain()
    PolicyControlledMetadata leafMetadata = policyControlledManager.getLeafPolicyControlledMetadata()
    PolicyControlledMetadata rootMetadata = policyControlledManager.getRootPolicyControlledMetadata()

    // Dynamically build an HQL query to traverse the ownership chain
    StringBuilder hql = new StringBuilder("SELECT t${ownershipChain.size() - 1}.${rootMetadata.resourceIdField}")
    hql.append(" FROM ${leafMetadata.resourceClassName} t0") // Start from the leaf entity

    // Build the JOINs up the chain
    for (int i = 0; i < ownershipChain.size() - 1; i++) {
      PolicyControlledMetadata currentMetadata = ownershipChain.get(i)
      // Ensure currentMetadata.ownerField is not null/empty before appending join
      if (currentMetadata.ownerField) {
        hql.append(" JOIN t${i}.${currentMetadata.ownerField} t${i+1}")
      }
    }

    hql.append(" WHERE t0.id = :leafResourceId") // Filter by the requested leaf resource ID

    // Execute the HQL query and return the id at hand
    String resolvedRootId = AccessPolicyEntity.executeQuery(hql.toString(), ["leafResourceId": leafResourceId])[0]

    // Return the resolved ID, or fallback to the leaf ID if resolution fails (e.g., entity not found)
    return resolvedRootId ?: leafResourceId
  }

  /**
   * Configures and returns a {@link PolicyEngine} instance.
   * This method determines the FOLIO client configuration based on environment variables,
   * Grails application configuration, or falls back to internal Okapi client details.
   * A new PolicyEngine is spun up per request, which is a consideration for efficiency.
   *
   * @return A configured {@link PolicyEngine} instance.
   */
  protected PolicyEngine getPolicyEngine() {
    // This should work regardless of whether we're in a proper FOLIO space or not now.
    // I'm not convinced this is the best way to do it but hey ho
    UserDetails patron = getPatron()
    String defaultPatronId = 'defaultPatronId'
    if (patron.hasProperty("id")) {
      defaultPatronId = patron.id
    }

    // Build the folio information via ENV_VARS, grailsApplication defaults OR fallback to "this folio".
    // Should allow devs to control where code is pointing dynamically without needing to comment/uncomment different folioConfigs here
    String baseOkapiUrl = grailsApplication.config.getProperty('accesscontrol.folio.baseokapiurl', String)
    boolean folioIsExternal = true
    if (baseOkapiUrl == null) {
      folioIsExternal = false
      baseOkapiUrl = "https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}"
    }

    FolioClientConfig folioClientConfig = FolioClientConfig.builder()
      .baseOkapiUri(baseOkapiUrl)
      .tenantName(grailsApplication.config.getProperty('accesscontrol.folio.tenantname', String, OkapiTenantResolver.schemaNameToTenantId(Tenants.currentId())))
      .patronId(grailsApplication.config.getProperty('accesscontrol.folio.patronid', String, OkapiTenantResolver.schemaNameToTenantId(defaultPatronId)))
      .userLogin(grailsApplication.config.getProperty('accesscontrol.folio.userlogin', String))
      .userPassword(grailsApplication.config.getProperty('accesscontrol.folio.userpassword', String))
      .build()

    // Keep logs to "trace" and ensure we only log INFO for prod
    log.trace("FolioClientConfig configured baseOkapiUri: ${folioClientConfig.baseOkapiUri}")
    log.trace("FolioClientConfig configured tenantName: ${folioClientConfig.tenantName}")
    log.trace("FolioClientConfig configured patronId: ${folioClientConfig.patronId}")
    log.trace("FolioClientConfig configured userLogin: ${folioClientConfig.userLogin}")
    log.trace("FolioClientConfig configured userPassword: ${folioClientConfig.userPassword}")

    // TODO This being spun up per request doesn't seem amazingly efficient -- but equally
    //  it's really just a POJO and each request could be from a different tenant so maybe it's fine
    PolicyEngine policyEngine = new PolicyEngine(
      PolicyEngineConfiguration
        .builder()
        .folioClientConfig(folioClientConfig)
        .externalFolioLogin(folioIsExternal)
        .acquisitionUnits(true) // This currently ASSUMES that we're ALWAYS using acquisitionUnits
        .build()
    )

    return policyEngine
  }

  /**
   * Generates a list of SQL fragments (policy subqueries) based on a given policy restriction,
   * query type, and the resource ID to which the policy applies.
   * This method communicates with the {@link PolicyEngine} to retrieve the policy definitions
   * and formats them into SQL suitable for database queries.
   *
   * @param restriction The type of policy restriction (e.g., READ, UPDATE, DELETE).
   * @param queryType The type of query (e.g., SINGLE for individual resource checks, LIST for collection checks).
   * @param resourceId The ID of the resource to apply the policy to. Can be {@code null} for LIST queries.
   * @return A list of SQL string fragments representing the access policies.
   */
  protected List<String> getPolicySql(PolicyRestriction restriction, AccessPolicyQueryType queryType, String resourceId) {
    /* ------------------------------- ACTUALLY DO THE WORK FOR EACH POLICY RESTRICTION ------------------------------- */

    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    List<PolicySubquery> policySubqueries = policyEngine.getPolicySubqueries(grailsHeaders, restriction, queryType)

    String resourceAlias = '{alias}'
    PolicyControlledMetadata rootPolicyControlledMetadata = policyControlledManager.getRootPolicyControlledMetadata()
    if (policyControlledManager.hasOwners()) {
      resourceAlias = rootPolicyControlledMetadata.getAliasName()
    } // If there are no "owners" then rootPolicyControlledMetadata should equal leafPolicyControlledMetadata ie, resourceClass

    // We build a parameter block to use on the policy subqueries. Some of these we can probably set up ahead of time...
    PolicySubqueryParameters params = PolicySubqueryParameters
      .builder()
      .accessPolicyTableName(AccessPolicyEntity.TABLE_NAME)
      .accessPolicyTypeColumnName(AccessPolicyEntity.TYPE_COLUMN)
      .accessPolicyIdColumnName(AccessPolicyEntity.POLICY_ID_COLUMN)
      .accessPolicyResourceIdColumnName(AccessPolicyEntity.RESOURCE_ID_COLUMN)
      .accessPolicyResourceClassColumnName(AccessPolicyEntity.RESOURCE_CLASS_COLUMN)
      .resourceAlias(resourceAlias) // FIXME this is a hibernate thing... not sure if we need to deal with this right now. Not sure how this will interract with "owner" type queries
      .resourceIdColumnName(rootPolicyControlledMetadata.resourceIdColumn)
      .resourceId(resourceId) // This might be null (For LIST type queries)
      .resourceClass(rootPolicyControlledMetadata.resourceClassName)
      .build()

    log.trace("PolicySubqueryParameters configured: ${params}")

    return policySubqueries.collect { psq -> psq.getSql(params)}
  }


  // TODO CLAIM and CREATE are a little different :/ Maybe the restriction type has to go all the way down to the PolicySubquery too
  /* --------------------- DYNAMICALLY ASSIGNED ACCESSCONTROL METHODS --------------------- */
  /**
   * Determines if a single resource can be accessed for a given {@link PolicyRestriction}.
   * This method resolves the root owner ID (if applicable), retrieves policy SQL fragments,
   * and executes a native SQL query combining these fragments to check access.
   *
   * @param pr The {@link PolicyRestriction} to check (READ, UPDATE, or DELETE).
   * @return {@code true} if access is allowed according to the policies, {@code false} otherwise.
   */
  protected boolean canAccess(PolicyRestriction pr) {
    AccessPolicyEntity.withNewSession { Session sess ->
      // Handle OWNER logic

      // If there are NO owners, we can use the queryResourceId from the request itself
      String queryResourceId = resolveRootOwnerId(params.id)

      if (
        !pr.equals(PolicyRestriction.READ) &&
          !pr.equals(PolicyRestriction.UPDATE) &&
          !pr.equals(PolicyRestriction.DELETE)
      ) {
        throw new PolicyEngineException("Restriction: ${pr.toString()} is not accessible here", PolicyEngineException.INVALID_RESTRICTION)
      }

      // We have a valid restriction, lets get the policySql
      List<String> policySqlFragments = policySqlFragments = getPolicySql(pr, AccessPolicyQueryType.SINGLE, queryResourceId)

      log.trace("AccessControl generated PolicySql: ${policySqlFragments.join(', ')}")

      // We're going to do this with hibernate criteria builder to match doTheLookup logic
      String bigSql = policySqlFragments.collect {"(${it})" }.join(" AND ") // JOIN all sql subqueries together here.
      boolean result = sess.createNativeQuery("SELECT ${bigSql} AS access_allowed".toString()).list()[0]

      return result
    }
  }

  /**
   * Checks if the currently authenticated user has read access to the resource identified by {@code params.id}.
   * The result is returned in the response map.
   */
  @Transactional
  def canRead() {
    log.trace("AccessPolicyAwareController::canRead")
    respond([canRead: canAccess(PolicyRestriction.READ)]) // FIXME should be a proper response here
  }

  /**
 * Checks if the currently authenticated user has update access to the resource identified by {@code params.id}.
 * The result is returned in the response map.
 */
  @Transactional
  def canUpdate() {
    log.trace("AccessPolicyAwareController::canUpdate")
    respond([canUpdate: canAccess(PolicyRestriction.UPDATE)]) // FIXME should be a proper response here
  }

  /**
   * Checks if the currently authenticated user has delete access to the resource identified by {@code params.id}.
   * The result is returned in the response map.
   */
  @Transactional
  def canDelete() {
    log.trace("AccessPolicyAwareController::canDelete")
    respond([canDelete: canAccess(PolicyRestriction.DELETE)]) // FIXME should be a proper response here
  }

  // FIXME this will need to go on the ACTUAL lookup etc etc...
  //  For now it can be a test method
  /**
   * Retrieves a list of resources that the current user has read access to,
   * applying access policy restrictions to the list query.
   * This method leverages the {@code doTheLookup} mechanism and injects policy-based
   * criteria using {@link MultipleAliasSQLCriterion}.
   *
   * @return A response object containing the list of accessible resources.
   */
  @Transactional
  def readRestrictedList() {
    AccessPolicyEntity.withNewSession {
      List<String> policySql = getPolicySql(PolicyRestriction.READ, AccessPolicyQueryType.LIST, null)
      log.trace("AccessControl generated PolicySql: ${policySql.join(', ')}")

      long beforeLookup = System.nanoTime()
      respond doTheLookup(resourceClass) {

        // To handle nested levels of ownership, we have pre-parsed the owner tree
        MultipleAliasSQLCriterion.SubCriteriaAliasContainer[] subCriteria = policyControlledManager.getNonLeafOwnershipChain().collect { pcm ->
          Criteria aliasCriteria = criteria.createCriteria(pcm.getAliasOwnerField(), pcm.getAliasName())
          return new MultipleAliasSQLCriterion.SubCriteriaAliasContainer(pcm.getAliasName(), aliasCriteria)
        }

        policySql.each {psql ->
          criteria.add(new MultipleAliasSQLCriterion(psql, subCriteria))
        }
        // Ensure we return criteria at the bottom?
        return criteria
      }

      long afterLookup = System.nanoTime()
      log.trace("AccessPolicyAwareController::testReadRestrictedList query time: {}", Duration.ofNanos(afterLookup - beforeLookup))
    }
  }
}
