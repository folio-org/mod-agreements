package org.olf

import com.k_int.okapi.OkapiTenantAdminService
import grails.converters.JSON
import org.olf.dataimport.internal.KBManagementBean
import org.olf.general.jobs.GokbResourceEntitlementJob
import org.olf.general.jobs.PackageIngestJob
import org.olf.general.jobs.ResourceDeletionJob
import org.olf.general.jobs.TitleIngestJob
import org.olf.kb.metadata.ResourceIngressType

import static groovy.transform.TypeCheckingMode.SKIP

import com.k_int.web.toolkit.SimpleLookupService

import org.olf.dataimport.internal.PackageSchema.ContentItemSchema

import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Platform
import org.olf.kb.IdentifierOccurrence

import org.olf.dataimport.internal.TitleInstanceResolverService

import com.k_int.web.toolkit.settings.AppSetting

import org.hibernate.sql.JoinType
import grails.gorm.DetachedCriteria

import org.springframework.scheduling.annotation.Scheduled

import com.k_int.okapi.OkapiTenantResolver

import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic

import java.time.Instant
import java.time.temporal.ChronoUnit
import groovy.util.logging.Slf4j

/**
 * See http://guides.grails.org/grails-scheduled/guide/index.html for info on this way of
 * scheduling tasks
 */
@Slf4j
@CompileStatic
class KbManagementService {
  // This service used to hold MatchKey related methods, but is now empty.
  KBManagementBean kbManagementBean
  OkapiTenantAdminService okapiTenantAdminService

  @Scheduled(fixedDelay = 3600000L, initialDelay = 60000L)
  @CompileStatic(SKIP)
  triggerEntitlementJob() {
    ResourceIngressType ingressType = kbManagementBean.ingressType

    okapiTenantAdminService.allConfiguredTenantSchemaNames().each { tenant_schema_id ->
      log.debug "Create gokb resource job for tenant schema ${tenant_schema_id}"
      try {
        Tenants.withId(tenant_schema_id) {
          if (ingressType == ResourceIngressType.HARVEST) {

            // Look for packageIngest job in progress
            PackageIngestJob packageJob = PackageIngestJob.findByStatusInList([
              PackageIngestJob.lookupStatus('In progress')
            ])

            // Look for title job in progress
            TitleIngestJob titleJob = TitleIngestJob.executeQuery("""
          SELECT tj FROM TitleIngestJob AS tj
            WHERE (
              (tj.status.value = 'in_progress')
            )
        """.toString())[0]

            if (!packageJob && !titleJob) {
              // If neither harvest ingest job is in progress, run the entitlement job
              log.info("Starting external gokb entitlement sync job.")
              GokbResourceEntitlementJob job = new GokbResourceEntitlementJob(['name': 'GokbResourceEntitlementJob'])
              job.setStatusFromString('Queued')
              job.save(failOnError: true, flush: true)
            } else {
              log.info("Title {} or package {} ingest jobs found, skipping gokb entitlement sync job.", titleJob?.id?.toString(), packageJob?.id?.toString())
            }
          } else {
            log.info("Starting external gokb entitlement sync job.")
            GokbResourceEntitlementJob job = new GokbResourceEntitlementJob(['name': 'GokbResourceEntitlementJob'])
            job.setStatusFromString('Queued')
            job.save(failOnError: true, flush: true)
          }
        }
      }
      catch (Exception e) {
        log.error("Unexpected error in triggerEntitlementJob for tenant ${tenant_schema_id}", e);
      }
    }
  }
}
