package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyQueryType
import com.k_int.accesscontrol.core.PolicyControlledMetadata
import com.k_int.accesscontrol.core.PolicyEngineException
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.PolicySubquery
import com.k_int.accesscontrol.core.PolicySubqueryParameters
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.main.PolicyEngineConfiguration
import com.k_int.folio.FolioClientConfig
import com.k_int.okapi.OkapiClient
import com.k_int.okapi.OkapiTenantAwareController
import com.k_int.okapi.OkapiTenantResolver
import grails.gorm.multitenancy.Tenants
import org.hibernate.Session
import org.springframework.security.core.userdetails.UserDetails

import javax.servlet.http.HttpServletRequest
import javax.transaction.Transactional

// Extend OkapiTenantAwareController with PolicyEngine stuff
class AccessPolicyAwareController<T> extends OkapiTenantAwareController<T> {
  OkapiClient okapiClient
  final Class<T> resourceClass

  private static void ensurePolicyControlled(Class<?> clazz) {
    if (!clazz.isAnnotationPresent(GrailsPolicyControlled)) {
      throw new IllegalArgumentException(
        "AccessPolicyAwareController can only be used for @GrailsPolicyControlled classes. " +
          "${clazz.name} is not annotated."
      )
    }
  }

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
    resourceClass = resource
    ensurePolicyControlled(resource)
  }

  AccessPolicyAwareController(Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
    resourceClass = resource
    ensurePolicyControlled(resource)
  }

  protected static PolicyControlledMetadata resolvePolicyControlledMetadata(Class<?> clazz) {
    def annotation = clazz.getAnnotation(GrailsPolicyControlled)

    // This _shouldn't_ be possible thanks to the ensurePolicyControlled method above in the constructor
    if (!annotation) throw new IllegalArgumentException("Missing @GrailsPolicyControlled on ${clazz.name}")

    return PolicyControlledMetadata.builder()
      .resourceClass(annotation.resourceClass())
      .resourceIdColumn(annotation.resourceIdColumn())
      .build()
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


    log.info("LOGDEBUG BASE OKAPI URI: ${folioClientConfig.baseOkapiUri}")
    log.info("LOGDEBUG TENANT ID: ${folioClientConfig.tenantName}")
    log.info("LOGDEBUG PATRON ID: ${folioClientConfig.patronId}")
    log.info("LOGDEBUG USER LOGIN: ${folioClientConfig.userLogin}")
    log.info("LOGDEBUG USER PASSWORD: ${folioClientConfig.userPassword}")

    // FIXME This being spun up per request doesn't seem amazingly efficient
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

  private List<String> getPolicySql(AccessPolicyQueryType type, PolicyRestriction restriction, String resourceId) {
    /* ------------------------------- ACTUALLY DO THE WORK FOR EACH POLICY RESTRICTION ------------------------------- */

    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    List<PolicySubquery> policySubqueries = policyEngine.getPolicySubqueries(grailsHeaders, restriction)
    PolicyControlledMetadata policyControlledMetadataForClass = resolvePolicyControlledMetadata(resourceClass)

    // We build a parameter block to use on the policy subqueries. Some of these we can probably set up ahead of time...
    PolicySubqueryParameters params = PolicySubqueryParameters
      .builder()
      .type(type)
      .accessPolicyTableName(AccessPolicyEntity.TABLE_NAME)
      .accessPolicyTypeColumnName(AccessPolicyEntity.TYPE_COLUMN)
      .accessPolicyIdColumnName(AccessPolicyEntity.POLICY_ID_COLUMN)
      .accessPolicyResourceIdColumnName(AccessPolicyEntity.RESOURCE_ID_COLUMN)
      .accessPolicyResourceClassColumnName(AccessPolicyEntity.RESOURCE_CLASS_COLUMN)
      .resourceAlias("{alias}") // FIXME this is a hibernate thing... not sure if we need to deal with this right now.
      .resourceIdColumnName(policyControlledMetadataForClass.resourceIdColumn)
      .resourceId(resourceId) // This might be null (For LIST type queries)
      .resourceClass(policyControlledMetadataForClass.resourceClass)
      .build()

    log.info("LOGDEBUG PARAMS: ${params}")

    return policySubqueries.collect { psq -> psq.getSql(params)}
  }


  // IMPORTANT this assumes that "id" is the parameter available for the UUID in the SINGLE cases
  List<String> getReadRestrictedList() {
    return getPolicySql(AccessPolicyQueryType.LIST, PolicyRestriction.READ, null)
  }

  List<String> getReadRestrictedSingle() {
    return getPolicySql(AccessPolicyQueryType.SINGLE, PolicyRestriction.READ, params.id)
  }

  List<String> getUpdateRestrictedList() {
    return getPolicySql(AccessPolicyQueryType.LIST, PolicyRestriction.UPDATE, null)
  }

  List<String> getUpdateRestrictedSingle() {
    return getPolicySql(AccessPolicyQueryType.SINGLE, PolicyRestriction.UPDATE, params.id)
  }

  List<String> getDeleteRestrictedList() {
    return getPolicySql(AccessPolicyQueryType.LIST, PolicyRestriction.DELETE, null)
  }

  List<String> getDeleteRestrictedSingle() {
    return getPolicySql(AccessPolicyQueryType.SINGLE, PolicyRestriction.DELETE, params.id)
  }

  // TODO CLAIM and CREATE are a little different :/ Maybe the restriction type has to go all the way down to the PolicySubquery too

  /* --------------------- DYNAMICALLY ASSIGNED ACCESSCONTROL METHODS --------------------- */
  private boolean canAccess(PolicyRestriction pr) {
    List<String> policySql = []
    switch (pr) {
      case PolicyRestriction.READ:
        policySql = getReadRestrictedSingle()
        break
      case PolicyRestriction.UPDATE:
        policySql = getUpdateRestrictedSingle()
        break
      case PolicyRestriction.DELETE:
        policySql = getDeleteRestrictedSingle()
        break
      default:
        throw new PolicyEngineException("Restriction: ${pr.toString()} is not accessible here", PolicyEngineException.INVALID_RESTRICTION)
        break
    }
      log.debug("LOGDEBUG WHAT IS POLICYSQL: ${policySql.join(', ')}")
      boolean result = false

      AccessPolicyEntity.withNewSession { Session sess ->
        String bigSql = policySql.collect {"(${it})" }.join(" AND ") // JOIN all sql subqueries together here.

        result = sess.createNativeQuery("SELECT ${bigSql} AS access_allowed".toString()).list()[0]
        log.debug("LOGDEBUG WHAT IS RESULT: ${result}")
      }

      return result
  }

  @Transactional
  def canRead() {
    log.debug("AccessPolicyAwareController::canRead")
    respond([canRead: canAccess(PolicyRestriction.READ)]) // FIXME should be a proper response here
  }

  @Transactional
  def canUpdate() {
    log.debug("AccessPolicyAwareController::canUpdate")
    respond([canUpdate: canAccess(PolicyRestriction.UPDATE)]) // FIXME should be a proper response here
  }

  @Transactional
  def canDelete() {
    log.debug("AccessPolicyAwareController::canDelete")
    respond([canDelete: canAccess(PolicyRestriction.DELETE)]) // FIXME should be a proper response here
  }
}
