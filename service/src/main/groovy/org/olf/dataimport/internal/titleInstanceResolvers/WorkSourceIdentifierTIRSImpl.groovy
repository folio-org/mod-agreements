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

    log.debug("LOGDEBUG CANDIDATE WORKS: ${candidate_works}")
    switch (candidate_works.size()) {
      case 0:
        TitleInstance ti = fallbackToIdFirstTIRSResolve(citation, trustedSourceTI);
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
      log.debug("TI IN PLACE, FURTHER WORK NEEDED")
    }

    return ti;
  }

  // Method to wrangle ids and siblings after the fact
  private void updateIdentifiersAndSiblings(ContentItemSchema citation, Work work) {
    log.debug("Not currently implemented")
  }
}
