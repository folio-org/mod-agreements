package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.acqunits.AcquisitionsClient
import com.k_int.accesscontrol.core.PolicyControlledMetadata
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.PolicySubquery
import com.k_int.accesscontrol.core.PolicySubqueryParameters
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.main.PolicyEngineConfiguration
import com.k_int.folio.FolioClientConfig
import com.k_int.folio.FolioClientException
import com.k_int.okapi.OkapiClient
import com.k_int.okapi.OkapiTenantAwareController
import com.k_int.okapi.OkapiTenantResolver
import grails.gorm.multitenancy.Tenants
import org.springframework.security.core.userdetails.UserDetails

import java.time.Duration


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
    FolioClientConfig folioClientConfig = FolioClientConfig.builder()
      .baseOkapiUri(grailsApplication.config.getProperty('accesscontrol.folio.baseokapiurl', String, "https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}"))
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
        .acquisitionUnits(true) // This currently ASSUMES that we're ALWAYS using acquisitionUnits
        .build()
    )

    return policyEngine
  }

  // TODO perhaps this ought to be service methods instead -- and allow for a GENERIC Class<T has annotation>
  List<String> getPolicySql() {
    try {
      /* ------------------------------- LOGIN LOGIC ------------------------------- */
      AcquisitionsClient acqClient = policyEngine.getAcqClient()
      // FIXME in the final work we will just pass down request context headers instead, not do a separate login
      long beforeLogin = System.nanoTime()

      String[] folioAccessHeaders = acqClient.getFolioAccessTokenCookie([] as String[])

      log.info("LOGDEBUG LOGIN COOKIE: ${folioAccessHeaders}")
      /* ------------------------------- END LOGIN LOGIC ------------------------------- */

      /* ------------------------------- ACTUALLY DO THE WORK FOR EACH POLICY RESTRICTION ------------------------------- */
      long beforePolicy = System.nanoTime()

      List<PolicySubquery> policySubqueries = policyEngine.getPolicySubqueries(folioAccessHeaders, PolicyRestriction.READ)

      PolicyControlledMetadata policyControlledMetadataForClass = resolvePolicyControlledMetadata(resourceClass)

      // We build a parameter block to use on the policy subqueries. Some of these we can probably set up ahead of time...
      PolicySubqueryParameters params = PolicySubqueryParameters
        .builder()
        .accessPolicyTableName(AccessPolicyEntity.TABLE_NAME)
        .accessPolicyTypeColumnName(AccessPolicyEntity.TYPE_COLUMN)
        .accessPolicyIdColumnName(AccessPolicyEntity.POLICY_ID_COLUMN)
        .accessPolicyResourceIdColumnName(AccessPolicyEntity.RESOURCE_ID_COLUMN)
        .accessPolicyResourceClassColumnName(AccessPolicyEntity.RESOURCE_CLASS_COLUMN)
        .resourceAlias("{alias}") // FIXME this is a hibernate thing... not sure if we need to deal with this right now.
        .resourceIdColumnName(policyControlledMetadataForClass.resourceIdColumn)
        .resourceClass(policyControlledMetadataForClass.resourceClass)
        .build()

      log.info("LOGDEBUG PARAMS: ${params}")

      long beforeReturn = System.nanoTime()

      Duration loginToPolicy = Duration.ofNanos(beforePolicy - beforeLogin)
      Duration policyToLookup = Duration.ofNanos(beforeReturn - beforePolicy)

      log.debug("LOGDEBUG login time: ${loginToPolicy}")
      log.debug("LOGDEBUG policy lookup time: ${policyToLookup}")
      return policySubqueries.collect { psq -> psq.getSql(params)}
    } catch (FolioClientException e) {
      if (e.cause) {
        log.error("Something went wrong in folio call: ${e}: CAUSE:", e.cause)
      } else {
        log.error("Something went wrong in folio call", e)
      }

      // Something has gone wrong, return an empty list
      return []
    }
  }
}
