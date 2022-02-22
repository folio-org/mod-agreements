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

  @CompileStatic(SKIP)
  // COUNT query to check for TIs which have changed, or have changed IdentifierOccurrences OR MatchKeys
  static final DetachedCriteria<String> CHANGED_TITLES( final Date since ) { 
    new DetachedCriteria(TitleInstance, 'changed_tis').build {
      or {
        // TI was updated directly
        isNotNull('lastUpdated')
        gt ('lastUpdated', since)

        // IdentifierOccurrence on TI was updated
        'in' 'id', new DetachedCriteria(TitleInstance, 'tis_with_changed_match_keys').build {
          identifiers {
            isNotNull('lastUpdated')
            gt ('lastUpdated', since)
          }
          projections {
            distinct 'tis_with_changed_match_keys.id'
          }
        }

        // MatchKey on PCI was updated
        'in' 'id', new DetachedCriteria(TitleInstance, 'tis_with_changed_pcis').build {
          platformInstances {
            packageOccurences {
              matchKeys {
                isNotNull('lastUpdated')
                gt ('lastUpdated', since)
              }
            }
          }

          projections {
            distinct 'tis_with_changed_pcis.id'
          }
        }
      }

      projections {
        distinct 'changed_tis.id'
      }
    }
  }

  @CompileStatic(SKIP)
  private void triggerRematch() {
    // Look for jobs already queued or in progress
    ResourceRematchJob rematchJob = ResourceRematchJob.findByStatusInList([
      ResourceRematchJob.lookupStatus('Queued'),
      ResourceRematchJob.lookupStatus('In progress')
    ])

    if (!rematchJob) {
      // Lookup previous ResourceRematchJob
      ResourceRematchJob lastRematchJob = ResourceRematchJob.find("from ResourceRematchJob order by dateCreated desc")
      Date since = lastRematchJob ? Date.from(lastRematchJob?.started) : new Date(0);


      log.debug("LOGDEBUG ATTEMPTING QUERY")
      DetachedCriteria<String> changedTitles = CHANGED_TITLES(since);
      log.debug("LOGDEBUG CHANGED TITLES SINCE (${since})? ${changedTitles.list()}")
      log.debug("LOGDEBUG # CHANGED TITLES SINCE (list.size) (${since})? ${changedTitles.list().size()}")

      def countTheThing = changedTitles.list {
        projections {
          rowCount()
        }
      }
      log.debug("LOGDEBUG # OF CHANGED TITLES SINCE (${since})? ${countTheThing}")

     /*  if (changedTitles.count() > 0) {
        String jobTitle = "Resource Rematch Job ${Instant.now()}"
        rematchJob = new ResourceRematchJob(name: jobTitle, since: since)
        rematchJob.setStatusFromString('Queued')
        rematchJob.save(failOnError: true, flush: true)
      } else {
        log.debug('No TitleInstances changed since last run, resource rematch job not scheduled')
      } */
    } else {
      log.debug('Resource rematch already running or scheduled. Ignore.')
    }
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

  // We may need a maunal trigger for "Check rematch for all TIs in system"

  // "Rematch" process for ErmResources using matchKeys (Only available for PCI at the moment)
  @CompileStatic(SKIP)
  public void runRematchProcess(Date since) {
    TitleInstance.withNewTransaction {
      final Iterator<String> tis = simpleLookupService.lookupAsBatchedStream(TitleInstance, null, 100) {
        'in' 'id', CHANGED_TITLES(since)
      }

      if (tis.hasNext()) {
        while (tis.hasNext()) {
          final String tiId = tis.next()
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
      }
    }
  }

  @CompileStatic(SKIP)
  public void rematchResources(List<String> resourceIds) {
    resourceIds.each {id ->
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

        if (matchKeyTitleInstance) {
          if (matchKeyTitleInstance.id == ti.id) {
            log.info ("ErmResource (${res}) already matched to correct TI according to match keys.")
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
