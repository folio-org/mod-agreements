package org.olf

import static groovy.transform.TypeCheckingMode.SKIP

import org.olf.dataimport.internal.PackageSchema.ContentItemSchema

import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.TitleInstance
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.MatchKey
import org.olf.MatchKeyService

import org.olf.dataimport.internal.TitleInstanceResolverService

import com.k_int.web.toolkit.settings.AppSetting

import org.hibernate.sql.JoinType
import grails.gorm.DetachedCriteria

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
  MatchKeyService matchKeyService
  TitleInstanceResolverService titleInstanceResolverService

  // This code is essentially the same logic which creates scheduled Title and Package ingest jobs over in the KbHarvestService
  //@Scheduled(fixedDelay = 3600000L, initialDelay = 60000L) // Run task every hour, wait 1 minute.
  @Scheduled(fixedDelay = 300000L, initialDelay = 60000L) // Run task every 5 minute, wait 1 minute.
  void triggerRematch() {
    log.debug "Running scheduled rematch job for all tenants :{}", Instant.now()
    return

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

  @CompileStatic(SKIP)
  List<String> batchFetchTIs(int tiBatchSize, int tiBatchCount, Date last_refreshed) {
    List<String> tis = TitleInstance.createCriteria().list([max: tiBatchSize, offset: tiBatchSize * tiBatchCount]) {
      order 'id'
      createAlias('identifiers', 'titleIdentifiers', JoinType.LEFT_OUTER_JOIN) 

      or {
        // TI was updated directly since last run
        gt('lastUpdated', last_refreshed)
        gt('titleIdentifiers.lastUpdated', last_refreshed)
      }

      projections {
        distinct 'id'
      }
    }

    tis
  }

  @CompileStatic(SKIP)
  List<String> batchFetchPcisForTi(int pciBatchSize, int pciBatchCount, String tiId) {
    List<String> pciIds = PackageContentItem.createCriteria().list([max: pciBatchSize, offset: pciBatchSize * pciBatchCount]) {
      order 'id'
      pti {
        eq ('titleInstance.id', tiId)
      }

      projections {
        distinct 'id'
      }
    }
  }


  // "Rematch" process for ErmResources using matchKeys (Only available for PCI at the moment)
  @CompileStatic(SKIP)
  public void runRematchProcess() {
    // Work out when job last ran
    String new_cursor_value = System.currentTimeMillis()
    Date last_refreshed
    AppSetting resource_rematch_cursor;

    // One transaction for fetching the initial values/creating AppSettings
    AppSetting.withNewTransaction {
      // Need to flush this initially so it exists for first instance
      // Set initial cursor to 0 so everything currently in system gets updated
      resource_rematch_cursor = AppSetting.findByKey('resource_rematch_cursor') ?: new AppSetting(
        section:'registry',
        settingType:'Date',
        key: 'resource_rematch_cursor',
        value: 0
      ).save(flush: true, failOnError: true)

      // Parse setting Strings to Date/Long
      last_refreshed = new Date(Long.parseLong(resource_rematch_cursor.value))
    }


    // Batch fetch all TIs changed since last run
    final int tiBatchSize = 100
    int tiBatchCount = 0
    TitleInstance.withNewTransaction {
      List<String> tisUpdated = batchFetchTIs(tiBatchSize, tiBatchCount, last_refreshed)
      while (tisUpdated && tisUpdated.size() > 0) {

        tiBatchCount++
        tisUpdated.each {tiId ->
          log.info("TI ${tiId} changed since last rematch run.")
          // For each TI look up all PCIs for that TI
          final int pciBatchSize = 100
          int pciBatchCount = 0
          List<String> pciIds = batchFetchPcisForTi(pciBatchSize, pciBatchCount, tiId)

          while (pciIds && pciIds.size()) {
            pciBatchCount++

            try {
              rematchResources(pciIds)
            } catch (Exception e) {
              log.error("Error running rematchResources for TI (${tiId}): ${e}")
            }

            pciIds = batchFetchPcisForTi(pciBatchSize, pciBatchCount, tiId)
          }
        }

        // Next page
        tisUpdated = batchFetchTIs(tiBatchSize, tiBatchCount, last_refreshed)
      }
    }
    
    // At end of job, set cursor value created at beginning of job as new cursor
    AppSetting.withNewTransaction {
      resource_rematch_cursor.value = new_cursor_value
      resource_rematch_cursor.save(failOnError: true)
    }
  }

  public void rematchResources(List<String> resourceIds) {
    resourceIds.each {id ->
      log.info("Attempting to rematch ErmResource ${id}")
      ErmResource res = ErmResource.get(id)
      TitleInstance ti; // To compare existing TI to one which we match later
      Collection<MatchKey> matchKeys = res.matchKeys;

      if (res instanceof PackageContentItem) {
        ti = res.pti.titleInstance
      } else {
        throw new RuntimeException("Currently unable to rematch resource of type: ${res.getClass()}")
      }

      // This is within a try/catch above
      TitleInstance matchKeyTitleInstance = titleInstanceResolverService.resolve(
        matchKeyService.matchKeysToSchema(matchKeys),
        false
      )
      log.debug("LOGDEBUG RESOLVED TI: ${matchKeyTitleInstance}")
      log.debug("LOGDEBUG ORIGINAL TI: ${ti}")
      if (matchKeyTitleInstance) {
        if (matchKeyTitleInstance.id == ti.id) {
          log.info ("ErmResource (${id}) already matched to correct TI according to match keys.")
        } else {
          // FIXME 1800 fill this in
          // At this point we have a PCI resource which needs to be linked to a different TI
        }
      } else {
        log.error("An error occurred resolving TI from matchKey information: ${matchKeys}.")
      }
    }
  }
}
