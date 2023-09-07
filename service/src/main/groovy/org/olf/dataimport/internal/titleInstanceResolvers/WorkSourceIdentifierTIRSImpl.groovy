package org.olf.dataimport.internal.titleInstanceResolvers

import org.olf.general.StringUtils

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.IdentifierSchema
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.TitleInstance
import org.olf.kb.Work

import grails.gorm.transactions.Transactional
import grails.web.databinding.DataBinder

import org.olf.dataimport.internal.TitleInstanceResolverService
import groovy.util.logging.Slf4j

import groovy.json.*
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil


/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
class WorkSourceIdentifierTIRSImpl extends BaseTIRS implements DataBinder, TitleInstanceResolverService {
  // Inject IdFirstTIRS to fall back to it
  TitleInstanceResolverService idFirstTIRS = new IdFirstTIRSImpl();

  public TitleInstance resolve(ContentItemSchema citation, boolean trustedSourceTI) {
    // log.debug("TitleInstanceResolverService::resolve(${citation})");
    TitleInstance result = null;

    // Error out if sourceIdentifier or sourceIdentifierNamespace do not exist
    ensureSourceIdentifierFields(citation);

    List<Work> candidate_works = Work.executeQuery("""
      from Work as w WHERE EXISTS (
        SELECT io FROM IdentifierOccurrence as io WHERE
          io.resource.id = w.id AND
          io.identifier.ns.value = :sourceIdentifierNamespace AND
          io.identifier.value = :sourceIdentifier AND
          io.status.value = '${APPROVED}'
      )
    """.toString(),
    [
      sourceIdentifierNamespace: citation.sourceIdentifierNamespace,
      sourceIdentifier: citation.sourceIdentifier
    ])

    switch (candidate_works.size()) {
      case 0:
        // Zero direct matches for work, fall back to IdFirstTIRS
        result = fallbackToIdFirstTIRSResolve(citation, trustedSourceTI);
        break;
      case 1:
        break;
      default:
      /*
       * We should NEVER match multiple works with the given
       * sourceIdentifierNamespace and sourceIdentifier
       */ 
      throw new TIRSException(
        "Matched ${candidate_works.size()} with source identifier ${citation.sourceIdentifierNamespace}:${citation.sourceIdentifier}",
        TIRSException.MULTIPLE_WORK_MATCHES
      )
        break;
    }

    return result;
  }

  private TitleInstance fallbackToIdFirstTIRSResolve(ContentItemSchema citation, boolean trustedSourceTI) {
    TitleInstance ti = null;
    /*
     * Could not find a work, fall back to resolve in idFirstTIRS
     * and attempt to set work sourceId on some existing TI.
     *
     * Resolve will either successfully create/match a single TI,
     * in which case we can move forward, or will match multiple and error out
     * We can catch the multiple case, because here we wish to create in that
     * circumstance
     */
    try {
      ti = idFirstTIRS.resolve(citation, trustedSourceTI);
    } catch (TIRSException tirsException) {
      // We treat a multiple title match here as NBD and move onto creation
      // Any other TIRSExceptions are legitimate concerns and we should rethrow
      if (
        tirsException.code != TIRSException.MULTIPLE_TITLE_MATCHES
      ) {
        throw new TIRSException(tirsException.message, tirsException.code);
      }
    } //Dont catch any other exception, those are legitimate reasons to stop

    /* At this point we should either have a title instance from
     * IdFirstTIRS or still null due to exceptions in TIRS
     */
    if (!ti) {
      // If we have no TI at this point, create one complete with work etc
      ti = idFirstTIRS.createNewTitleInstanceWithSiblings(citation)
    } else {
      /* We _do_ have a TI. Check that the attached work does not have an ID
       * If the attached work _does_ have an id, then we need a whole new work anyway
       */
      Work work = ti.work;
      switch (work.sourceIdentifier) {
        /* ASSUMPTION has been made here that a Work sourceIdentifier cannot have status
         * error, since we never set it to error through any of our logic. If that changes
         * Then this logic may need tweaks
         */
        case { it == null }:
          /* This is a preexisting TI/Work
           * We got to this TI via IdFirstTIRS resolve, so we know that there
           * is a single electronic TI (If there had been multiple on this
           * path we'd have created a new work after catching the TIRSException
           * above). Hence we know that after setting the Work sourceIdentifier
           * field, the TI we have in hand is the correct one and we can move forwards
           */
          Identifier identifier = lookupOrCreateIdentifier(citation.sourceIdentifier, citation.sourceIdentifierNamespace);
          IdentifierOccurrence sourceIdentifier = new IdentifierOccurrence([
            identifier: identifier,
            status: IdentifierOccurrence.lookupOrCreateStatus('approved')
          ])
          work.setSourceIdentifier(sourceIdentifier);
          work.save(flush: true, failOnError: true);

          /*
           * Now we need to do some identifier and sibling wrangling
           * to ensure data is consistent with what's coming in from citation
           */
          updateIdentifiersAndSiblings(citation, work)
          break;
        case {
          it != null &&
          (
            work.sourceIdentifier.identifier.value != citation.sourceIdentifier ||
            work.sourceIdentifier.identifier.ns.value != citation.sourceIdentifierNamespace
          )
        }:
          /*
           * At this step we have a work, but it does not match the sourceIdentifier
           * So we need to create a new Work/TI/Siblings set and return that at the end
           */
          ti = idFirstTIRS.createNewTitleInstanceWithSiblings(citation)
          break;
        default:
          /*
           * Only case left is sourceIdentifier is not null,
           * and sourceIdentifier matches that of the citation
           *
           * The TI in hand MUST be newly created
           * (Because otherwise we wouldn't be in this path at all,
           * this path begins at not finding a matching work)
           * Since TI is brand new, we can move forward with no wrangling
           */
          break;
      }
    }

    return ti;
  }

  // Method to wrangle ids and siblings after the fact
  private void updateIdentifiersAndSiblings(ContentItemSchema citation, Work work) {
    log.debug("Not currently implemented")
  }
}
