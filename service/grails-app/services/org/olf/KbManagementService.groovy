package org.olf

import static groovy.transform.TypeCheckingMode.SKIP

import org.olf.dataimport.internal.PackageSchema.ContentItemSchema

import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Platform
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.MatchKey
import org.olf.MatchKeyService

import org.olf.dataimport.internal.TitleInstanceResolverService

import com.k_int.web.toolkit.settings.AppSetting

import org.hibernate.sql.JoinType
import grails.gorm.DetachedCriteria

import org.olf.general.jobs.ResourceRematchJob
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
  MatchKeyService matchKeyService
  TitleInstanceResolverService titleInstanceResolverService

  // Queue for changed IdentifierOccurrences
  private Set<TitleInstance> changedTiQueue = [];

  public void addTiToQueue(TitleInstance ti) {
    changedTiQueue.add(ti)
  }

  public void clearTiQueue() {
    changedTiQueue = []
  };

  @CompileStatic(SKIP)
  private void triggerRematch() {
    // Look for jobs already queued or in progress

    ResourceRematchJob rematchJob = ResourceRematchJob.findByStatusInList([
      ResourceRematchJob.lookupStatus('Queued'),
      ResourceRematchJob.lookupStatus('In progress')
    ])

    if (!rematchJob) {
      if (changedTiQueue.size() > 0) {
        String jobTitle = "Resource Rematch Job ${Instant.now()}"
        rematchJob = new ResourceRematchJob(name: jobTitle)
        rematchJob.setStatusFromString('Queued')
        rematchJob.save(failOnError: true, flush: true)
      } else {
        log.debug('No TitleInstances changed since last run, resource rematch job not scheduled')
      }
    } else {
      log.debug('Resource rematch already running or scheduled. Ignore.')
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
    // Firstly we need to save the current state of the queue and clear existing queue so more TIs can be added during run
    Set<TitleInstance> processTis = changedTiQueue.collect()
    clearTiQueue()

    // FIXME might not be needed
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


    /* Batch fetch all TIs changed since last run
     TODO Ian thinks this would be better to run based on events.
     Essentially every time an IdentifierOccurrence changes, store it in a queue
     Then if the queue is non-empty, run this job.
     */
/*     final int tiBatchSize = 100
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
    } */

    TitleInstance.withNewTransaction {
      processTis.each {ti ->
        log.info("TI ${ti} changed since last rematch run.")
        // For each TI look up all PCIs for that TI
        final int pciBatchSize = 100
        int pciBatchCount = 0
        List<String> pciIds = batchFetchPcisForTi(pciBatchSize, pciBatchCount, ti?.id)

        while (pciIds && pciIds.size()) {
          pciBatchCount++

          try {
            rematchResources(pciIds)
          } catch (Exception e) {
            log.error("Error running rematchResources for TI (${ti}): ${e}")
          }

          pciIds = batchFetchPcisForTi(pciBatchSize, pciBatchCount, ti?.id)
        }
      }
    }

    // At end of job, set cursor value created at beginning of job as new cursor
    AppSetting.withNewTransaction {
      resource_rematch_cursor.value = new_cursor_value
      resource_rematch_cursor.save(failOnError: true)
    }
  }

  @CompileStatic(SKIP)
  public void rematchResources(List<String> resourceIds) {
    resourceIds.each {id ->
      log.info("Attempting to rematch ErmResource ${id}")
      ErmResource res = ErmResource.get(id)
      TitleInstance ti; // To compare existing TI to one which we match later
      Collection<MatchKey> matchKeys = res.matchKeys;

      if (res instanceof PackageContentItem) {
        ti = res.pti.titleInstance
        Platform platform = res.pti.platform

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
            log.debug("LOGDEBUG USING PLATFORM: ${platform}")
            log.debug("LOGDEBUG CHECKING FOR EXISTING PLATFORM/TI PAIR")
            PlatformTitleInstance targetPti = PlatformTitleInstance.findByPlatformAndTitleInstance(platform, matchKeyTitleInstance)          
            if (targetPti) {
              log.debug("LOGDEBUG TARGETPTI EXISTS: ${targetPti}")
              res.pti = targetPti; // Move PCI to new target PTI
            } else {
              log.debug("LOGDEBUG NO TARGETPTI, CREATE ONE")
              res.pti = new PlatformTitleInstance(
                titleInstance: matchKeyTitleInstance,
                platform: platform,
                url: res.pti.url // Fill new PTI url with existing PTI url from resource
              )
            }
          }
        } else {
          log.error("An error occurred resolving TI from matchKey information: ${matchKeys}.")
        }

        res.save(failOnError: true)
      } else {
        throw new RuntimeException("Currently unable to rematch resource of type: ${res.getClass()}")
      }
    }
  }
}
