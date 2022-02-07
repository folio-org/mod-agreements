package org.olf

import static groovy.transform.TypeCheckingMode.SKIP

import org.olf.general.jobs.ResourceRematchJob
import org.springframework.scheduling.annotation.Scheduled

import com.k_int.okapi.OkapiTenantAdminService
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
  OkapiTenantAdminService okapiTenantAdminService


  // This code is essentially the same logic which creates scheduled Title and Package ingest jobs over in the KbHarvestService
  //@Scheduled(fixedDelay = 3600000L, initialDelay = 60000L) // Run task every hour, wait 1 minute.
  @Scheduled(fixedDelay = 60000L) // Run task every 1 minute, wait 1 minute.
  void triggerRematch() {
    log.debug "Running scheduled rematch job for all tenants :{}", Instant.now()

    // ToDo: Don't think this will work for newly added tenants - need to investigate.
    okapiTenantAdminService.getAllTenantSchemaIds().each { tenant_schema_id ->
      log.debug "Perform trigger rematch for tenant schema ${tenant_schema_id}"
      try {
        triggerRematchForTenant(tenant_schema_id as String, true)
      }
      catch ( Exception e ) {
        log.error("Unexpected error in triggerRematch for tenant ${tenant_schema_id}", e);
      }
    }
  }

  @CompileStatic(SKIP)
  private void triggerRematchForTenant(final String tenant_schema_id, boolean scheduled = false) {
    Tenants.withId(tenant_schema_id) {

      // Look for jobs already queued or in progress

      ResourceRematchJob rematchJob = ResourceRematchJob.findByStatusInList([
        ResourceRematchJob.lookupStatus('Queued'),
        ResourceRematchJob.lookupStatus('In progress')
      ])

      if (!rematchJob) {
        String jobTitle = scheduled ? "Scheduled Resource Rematch Job ${Instant.now()}" : "Manual Resource Rematch Job ${Instant.now()}"
        rematchJob = new ResourceRematchJob(name: jobTitle)
        rematchJob.setStatusFromString('Queued')
        rematchJob.save(failOnError: true, flush: true)
      } else {
        log.debug('Resource rematch already running or scheduled. Ignore.')
      }
    }
  }



}
