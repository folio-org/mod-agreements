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
import com.k_int.accesscontrol.grails.extensions.MultipleAliasSQLCriterion
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

// Extend OkapiTenantAwareController with PolicyEngine stuff
class AccessPolicyAwareController<T> extends OkapiTenantAwareController<T> {
  OkapiClient okapiClient
  final Class<T> resourceClass

  final PolicyControlledManager policyControlledManager

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

  AccessPolicyAwareController(Class<T> resource) {
    super(resource)
    this.resourceClass = resource
    this.policyControlledManager = new PolicyControlledManager(resource)
  }

  AccessPolicyAwareController(Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
    this.resourceClass = resource
    this.policyControlledManager = new PolicyControlledManager(resource)
  }

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

  protected PolicyEngine getPolicyEngine() {
    // FIXME okapiBaseUri/tenantName/patronId should be central in any controller doing AccessControl
    // And obviously shouldn't be hardcoded

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
  // This is SINGLE only, canRead, canUpdate, canDelete.
  protected boolean canAccess(PolicyRestriction pr) {
    AccessPolicyEntity.withNewSession {
      // Handle OWNER logic

      // If there are NO owners, we can use the queryResourceId from the request itself
      String queryResourceId = resolveRootOwnerId(params.id)

      List<String> policySqlFragments = []
      if (
        !pr.equals(PolicyRestriction.READ) &&
          !pr.equals(PolicyRestriction.UPDATE) &&
          !pr.equals(PolicyRestriction.DELETE)
      ) {
        throw new PolicyEngineException("Restriction: ${pr.toString()} is not accessible here", PolicyEngineException.INVALID_RESTRICTION)
      }

      // We have a valid restriction, lets get the policySql
      policySqlFragments = getPolicySql(pr, AccessPolicyQueryType.SINGLE, queryResourceId)

      log.trace("AccessControl generated PolicySql: ${policySqlFragments.join(', ')}")
      boolean result = false

      // We're going to do this with hibernate criteria builder to match doTheLookup logic
      AccessPolicyEntity.withNewSession { Session sess ->
        String bigSql = policySqlFragments.collect {"(${it})" }.join(" AND ") // JOIN all sql subqueries together here.
        result = sess.createNativeQuery("SELECT ${bigSql} AS access_allowed".toString()).list()[0]
      }

      return result
    }
  }

  @Transactional
  def canRead() {
    log.trace("AccessPolicyAwareController::canRead")
    respond([canRead: canAccess(PolicyRestriction.READ)]) // FIXME should be a proper response here
  }

  @Transactional
  def canUpdate() {
    log.trace("AccessPolicyAwareController::canUpdate")
    respond([canUpdate: canAccess(PolicyRestriction.UPDATE)]) // FIXME should be a proper response here
  }

  @Transactional
  def canDelete() {
    log.trace("AccessPolicyAwareController::canDelete")
    respond([canDelete: canAccess(PolicyRestriction.DELETE)]) // FIXME should be a proper response here
  }

  // FIXME this will need to go on the ACTUAL lookup etc etc...
  //  For now it can be a test method
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
