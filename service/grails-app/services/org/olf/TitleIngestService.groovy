package org.olf

import java.util.concurrent.TimeUnit

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.kb.Embargo
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.Platform
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.RemoteKB
import org.olf.kb.TitleInstance
import org.slf4j.MDC

import grails.util.GrailsNameUtils
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
class TitleIngestService implements DataBinder {

  TitleInstanceResolverService titleInstanceResolverService
  TitleEnricherService titleEnricherService

  //FIXME is LOCAL_TITLE an acceptable default KB name for local title ingest?
  public Map upsertTitle(ContentItemSchema pc) {
    return upsertTitle(pc, 'LOCAL_TITLE')
  }

  public Map upsertTitle(ContentItemSchema pc, String remotekbname) {
    RemoteKB kb = RemoteKB.findByName(remotekbname)
    TitleInstance.withNewTransaction {
      if (!kb) {
       kb = new RemoteKB( name:remotekbname,
                          rectype: RemoteKB.RECTYPE_TITLE,
                          active: Boolean.TRUE,
                          readonly:readOnly,
                          trustedSourceTI:false).save(flush:true, failOnError:true)
      }

      upsertTitle(pc, kb)
    }
  }

  public Map upsertTitle(ContentItemSchema pc, RemoteKB kb, Boolean trusted = null) {
    def result = [
      startTime: System.currentTimeMillis(),
    ]
    // FIXME does ContentItemSchema need to be able to say trustedSourceTI or not? eg for manual import where you want it to be able to create but not update TIs
    //Boolean trustedSourceTI = trusted ?: package_data.header?.trustedSourceTI ?: kb.trustedSourceTI

    // If we're not explicitly handed trusted information, default to whatever the remote KB setting is
    Boolean trustedSourceTI = trusted ?: kb.trustedSourceTI
    if (trustedSourceTI == null) {
      // If it somehow remains unset, default to false, but with warning
      log.warn("Could not deduce trustedSourceTI setting for title, defaulting to false")
      trustedSourceTI = false
    }

    TitleInstance.withNewTransaction {
      result.updateTime = System.currentTimeMillis()

      // resolve may return null, used to throw exception which causes the whole package to be rejected. Needs
      // discussion to work out best way to handle.
      TitleInstance title = titleInstanceResolverService.resolve(pc, trustedSourceTI)


      if (title != null) {
        // Now we have a saved title in the system, we can check whether or not we want to go and grab extra data.

        String sourceIdentifier = pc?.sourceIdentifier
        // FIXME does this secondary enrichment belong in the title ingest service?
        titleEnricherService.secondaryEnrichment(kb, sourceIdentifier, title.id);

        // Append titleInstanceId to resultList, so we can use it elsewhere to look up titles ingested with this method
        result.titleInstanceId = title.id
        result.finishTime = System.currentTimeMillis()
      } else {
        String message = "Unable to resolve title from ${pc.title} with identifiers ${pc.instanceIdentifiers}"
        log.error(message)
      }
    }

    result
  }
}
