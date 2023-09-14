package org.olf

import static groovy.transform.TypeCheckingMode.SKIP

import com.k_int.web.toolkit.SimpleLookupService

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
  SimpleLookupService simpleLookupService

  @CompileStatic(SKIP)
  // COUNT query to check for TIs which have changed, or have changed IdentifierOccurrences OR MatchKeys
  static final DetachedCriteria<TitleInstance> CHANGED_TITLES( final Instant since ) {

    // Ensure we run the actual query on a DATE not an Instant, since the lastUpdated fields are Dates
    Date sinceDate = Date.from(since)
    new DetachedCriteria(TitleInstance, 'changed_tis').build {
      
      or {
        // IdentifierOccurrence on TI was updated
        'in' 'id', new DetachedCriteria(TitleInstance, 'tis_with_changed_match_keys').build {
          identifiers {
            isNotNull('lastUpdated')
            gt ('lastUpdated', sinceDate)
          }
          projections {
            property 'tis_with_changed_match_keys.id'
          }
        }

        // MatchKey on PCI was updated
        'in' 'id', new DetachedCriteria(TitleInstance, 'tis_with_changed_pcis').build {
          platformInstances {
            packageOccurences {
              matchKeys {
                isNotNull('lastUpdated')
                gt ('lastUpdated', sinceDate)
              }
            }
          }

          projections {
            property 'tis_with_changed_pcis.id'
          }
        }
      }
    }
  }

  @CompileStatic(SKIP)
  private void triggerRematch() {
    /*
     * IMPORTANT this automatic rematch process is extremely non-performant,
     * and also it will run on ALL tis after first creation, which is not ideal.
     * For lotus release this will simply be disabled, returning immediately.
     * SEE ALSO ResourceRematchJob getWork closure
     */
    /* 
    // Look for jobs already queued or in progress
    ResourceRematchJob rematchJob = ResourceRematchJob.findByStatusInList([
      ResourceRematchJob.lookupStatus('Queued'),
      ResourceRematchJob.lookupStatus('In progress')
    ])

    if (!rematchJob) {
      // Last job run
      final Instant sinceInst = ResourceRematchJob.createCriteria().get {
        // Only successful finished jobs
        order 'ended', 'desc'

        // This means that if some resources fail to be rematched in one job, they can be retried in the next
        result {
          eq ('value', 'success')
        }

        projections {
          property 'ended'
        }
        maxResults 1
      }

      final Instant since = sinceInst ?: Instant.EPOCH

      final boolean changedTis = TitleInstance.createCriteria().list {
        'in' 'id', CHANGED_TITLES(since).distinct('id')
        maxResults 1
      }?.size() > 0;

     if (changedTis) {
        String jobTitle = "Resource Rematch Job ${Instant.now()}"
        rematchJob = new ResourceRematchJob(name: jobTitle, since: since)
        rematchJob.setStatusFromString('Queued')
        rematchJob.save(failOnError: true, flush: true)
      } else {
        log.debug('No TitleInstances changed since last run, resource rematch job not scheduled')
      }
    } else {
      log.debug('Resource rematch already running or scheduled. Ignore.')
    } */
  }

  // "Rematch" process for ErmResources using matchKeys (Only available for PCI at the moment)
  @CompileStatic(SKIP)
  public void runRematchProcess(Instant since) {

      // Seems to need the lists?
      final Iterator<String> tis = simpleLookupService.lookupAsBatchedStream(TitleInstance, null, 100, null, null, null) {
        // Just get the IDs
        'in' 'id', CHANGED_TITLES(since).distinct('id')

        projections {
          property 'id'
        }
      }
      while (tis.hasNext()) {
        final String tiId = tis.next()
        // For each TI look up all PCIs for that TI

        // Seems to need the lists?
        final Iterator<String> pciIds = simpleLookupService.lookupAsBatchedStream(PackageContentItem, null, 100, null, null, null) {
          // Just get the IDs
          pti {
            eq ('titleInstance.id', tiId)
          }

          projections {
            distinct 'id'
          }
        }

        PackageContentItem.withNewTransaction {
          while (pciIds.hasNext()) {
            final String pciId = pciIds.next()
            try {
              rematchResource(pciId)
            } catch (Exception e) {
              log.error("Error running rematchResources for TI (${tiId})", e)
            }
          }
        }
      }
  }

  @CompileStatic(SKIP)
  public void rematchResource(String resourceId) {
    ErmResource res = ErmResource.get(resourceId)
    TitleInstance ti; // To compare existing TI to one which we match later
    Collection<MatchKey> matchKeys = res.matchKeys;

    // If no match keys, this process will break
    if (matchKeys.size() != 0) {
      if (res instanceof PackageContentItem) {
        ti = res.pti.titleInstance
        Platform platform = res.pti.platform

        TitleInstance matchKeyTitleInstance
        // Direct try/catch for broken TIs
        try {
          matchKeyTitleInstance = TitleInstance.read(
            titleInstanceResolverService.resolve(
              matchKeyService.matchKeysToSchema(matchKeys),
              false
            )
          )
        } catch (Exception e) {
          log.error("An error occurred resolving TI from matchKey information: ${matchKeys}.", e)
        }
        
        if (matchKeyTitleInstance) {
          if (matchKeyTitleInstance.id == ti.id) {
            log.info ("${res} already matched to correct TI according to match keys.")
          } else {
            // At this point we have a PCI resource which needs to be linked to a different TI
            PlatformTitleInstance targetPti = PlatformTitleInstance.findByPlatformAndTitleInstance(platform, matchKeyTitleInstance)          
            if (targetPti) {
              log.info("Moving ErmResource (${res}) to existing PTI (${targetPti})")
              res.pti = targetPti; // Move PCI to new target PTI
            } else {
              log.info("No PTI exists for platform (${platform}) and TitleInstance (${matchKeyTitleInstance}). ErmResource (${res}) will be moved to a new PTI.")
              res.pti = new PlatformTitleInstance(
                titleInstance: matchKeyTitleInstance,
                platform: platform,
                url: res.pti.url // Fill new PTI url with existing PTI url from resource
              )
            }

            // Only save resource when a change has occurred--otherwise next rematch run will grab this resource again
            res.save(failOnError: true)
          }
        } else {
          log.error("An error occurred resolving TI from matchKey information: ${matchKeys}.")
        }
      } else {
        throw new RuntimeException("Currently unable to rematch resource of type: ${res.getClass()}")
      }
    } else {
      log.error("No match keys found for resource ${res}.")
    }
  }

  @CompileStatic(SKIP)
  public void rematchResourcesForTIs(List<String> tiIds) {
    tiIds.each {tiId ->
      // Seems to need the lists?
      final Iterator<String> pciIds = simpleLookupService.lookupAsBatchedStream(PackageContentItem, null, 100, null, null, null) {
        // Just get the IDs
        pti {
          eq ('titleInstance.id', tiId)
        }

        projections {
          distinct 'id'
        }
      }

      PackageContentItem.withNewTransaction {
        while (pciIds.hasNext()) {
          final String pciId = pciIds.next()
          try {
            rematchResource(pciId)
          } catch (Exception e) {
            log.error("Error running rematchResources for TI (${tiId})", e)
          }
        }
      }
    }
  }
}
