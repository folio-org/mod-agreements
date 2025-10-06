package org.olf

import com.k_int.okapi.OkapiTenantAdminService
import com.k_int.web.toolkit.refdata.RefdataValue
import org.olf.dataimport.internal.KBManagementBean
import org.olf.erm.Entitlement
import org.olf.general.jobs.ExternalEntitlementSyncJob
import org.olf.general.jobs.PackageIngestJob
import org.olf.general.jobs.PersistentJob
import org.olf.general.jobs.TitleIngestJob
import org.olf.kb.metadata.ResourceIngressType
import static groovy.transform.TypeCheckingMode.SKIP
import org.springframework.scheduling.annotation.Scheduled
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
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
  EntitlementService entitlementService

  @Scheduled(fixedDelay = 3600000L, initialDelay = 60000L)
  @CompileStatic(SKIP)
  triggerEntitlementJob() {
    ResourceIngressType ingressType = kbManagementBean.ingressType

    okapiTenantAdminService.allConfiguredTenantSchemaNames().each { tenant_schema_id ->
      log.debug "Create gokb resource job for tenant schema ${tenant_schema_id}"
      try {
        Tenants.withId(tenant_schema_id) {
          List<Entitlement> entitlements = entitlementService.findEntitlementsByAuthority(Entitlement.GOKB_RESOURCE_AUTHORITY)
          if (entitlements == null || entitlements?.size() == 0) {
            // If we can't find any entitlements for external resources, we can skip job creation.
            return;
          }

          if (ingressType == ResourceIngressType.HARVEST) {
            // Fetch in-progress persistent jobs
            RefdataValue inProgressJobs = PersistentJob.lookupStatus('in_progress')

            PackageIngestJob packageJob = PackageIngestJob.findByStatusInList([
              inProgressJobs
            ])

            TitleIngestJob titleJob = TitleIngestJob.findByStatusInList([
              inProgressJobs
            ])

            if (!packageJob && !titleJob) {
              // If neither harvest ingest job is in progress, run the entitlement job
              log.info("Starting external entitlement sync job.")
              ExternalEntitlementSyncJob job = new ExternalEntitlementSyncJob(['name': 'ExternalEntitlementSyncJob'])
              job.setStatusFromString('Queued')
              job.save(failOnError: true, flush: true)
            } else {
              log.info("Title {} or package {} ingest jobs found, skipping job creation.", titleJob?.id?.toString(), packageJob?.id?.toString())
            }
          } else {
            // If Ingress Type != HARVEST i.e. we're using PushKB
            log.info("Starting external entitlement sync job.")
            ExternalEntitlementSyncJob job = new ExternalEntitlementSyncJob(['name': 'ExternalEntitlementSyncJob'])
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
