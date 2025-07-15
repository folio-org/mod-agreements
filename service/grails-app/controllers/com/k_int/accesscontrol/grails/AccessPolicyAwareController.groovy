package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.sql.AccessControlSql
import com.k_int.accesscontrol.core.AccessPolicyQueryType
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadata
import com.k_int.accesscontrol.core.PolicyEngineException
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.sql.AccessControlSqlType
import com.k_int.accesscontrol.core.sql.PolicySubquery
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters
import com.k_int.accesscontrol.grails.criteria.AccessControlHibernateTypeMapper
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.grails.criteria.MultipleAliasSQLCriterion
import com.k_int.okapi.OkapiClient
import grails.gorm.transactions.Transactional
import org.hibernate.Criteria
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.query.NativeQuery
import org.hibernate.type.Type
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.PostConstruct
import java.time.Duration

/**
 * Extends com.k_int.okapi.OkapiTenantAwareController to incorporate access policy enforcement for resources.
 * This controller provides methods to check read, update, and delete permissions based on defined
 * access policies, including handling complex ownership chains and dynamic policy evaluation.
 *
 * @param <T> The type of the resource entity managed by this controller.
 */
class AccessPolicyAwareController<T> extends PolicyEngineController<T> {
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
    String resolvedRootId = AccessPolicyEntity.executeQuery(hql.toString(), ["leafResourceId": leafResourceId])[0 as String]

    // Return the resolved ID, or fallback to the leaf ID if resolution fails (e.g., entity not found)
    return resolvedRootId ?: leafResourceId
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
  protected List<AccessControlSql> getPolicySql(PolicyRestriction restriction, AccessPolicyQueryType queryType, String resourceId) {
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


  // TODO CLAIM is a little different.. TBD
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
  @SuppressWarnings('GrMethodMayBeStatic') // Intellij won't shut up about making this static
  protected Set<PolicyRestriction> getCanAccessValidPolicyRestrictions() {
    return EnumSet.of(
      PolicyRestriction.CREATE,
      PolicyRestriction.DELETE,
      PolicyRestriction.UPDATE,
      PolicyRestriction.READ
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
    AccessPolicyEntity.withNewSession { Session sess ->
      // Handle OWNER logic

      // If there are NO owners, we can use the queryResourceId from the request itself
      String queryResourceId = resolveRootOwnerId(params.id)

      if (!getCanAccessValidPolicyRestrictions().contains(pr)) {
        throw new PolicyEngineException("Restriction: ${pr.toString()} is not accessible here", PolicyEngineException.INVALID_RESTRICTION)
      }

      // We have a valid restriction, lets get the policySql
      List<AccessControlSql> policySqlFragments = getPolicySql(pr, AccessPolicyQueryType.SINGLE, queryResourceId)

      log.trace("AccessControl generated PolicySql: ${policySqlFragments.join(', ')}")

      // We're going to do this with hibernate criteria builder to match doTheLookup logic
      String bigSql = policySqlFragments.collect {"(${it.getSqlString()})" }.join(" AND ") // JOIN all sql subqueries together here.
      NativeQuery accessAllowedQuery = sess.createNativeQuery("SELECT ${bigSql} AS access_allowed".toString())

      // Now bind all parameters for all sql fragments. We ASSUME they're all using ? for bind params.
      // Track where we're up to with hibernateParamIndex -- hibernate is 1-indexed
      int hibernateParamIndex = 1
      policySqlFragments.each { AccessControlSql entry ->
        entry.getParameters().eachWithIndex { Object param, int paramIndex  ->
          accessAllowedQuery.setParameter(hibernateParamIndex, param, (Type) typeMapper.getHibernateType((AccessControlSqlType) entry.getTypes()[paramIndex]))
          hibernateParamIndex++ // Iterate the outer index
        }
      }

      boolean result = accessAllowedQuery.list()[0]

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

  @Transactional
  def canCreate() {
    log.trace("AccessPolicyAwareController::canCreate")
    respond([canCreate: canAccess(PolicyRestriction.CREATE)]) // FIXME should be a proper response here
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
      List<AccessControlSql> policySql = getPolicySql(PolicyRestriction.READ, AccessPolicyQueryType.LIST, null)
      log.trace("AccessControl generated PolicySql: ${policySql.join(', ')}")

      long beforeLookup = System.nanoTime()
      respond doTheLookup(resourceClass) {

        // To handle nested levels of ownership, we have pre-parsed the owner tree
        MultipleAliasSQLCriterion.SubCriteriaAliasContainer[] subCriteria = policyControlledManager.getNonLeafOwnershipChain().collect { pcm ->
          Criteria aliasCriteria = criteria.createCriteria(pcm.getAliasOwnerField(), pcm.getAliasName())
          return new MultipleAliasSQLCriterion.SubCriteriaAliasContainer(pcm.getAliasName(), aliasCriteria)
        }

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
}
