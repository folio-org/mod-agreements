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

import groovy.util.logging.Slf4j

import groovy.json.*
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
class WorkSourceIdentifierTIRSImpl extends IdFirstTIRSImpl implements DataBinder {
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
        // Zero direct matches for work, fall back to baseResolve
        result = fallbackToIdFirstResolve(citation, trustedSourceTI);
        break;
      case 1:
        Work work = GrailsHibernateUtil.unwrapIfProxy(candidate_works.get(0));
        result = getTitleInstanceFromWork(citation, work)
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

  private TitleInstance fallbackToIdFirstResolve(ContentItemSchema citation, boolean trustedSourceTI) {
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
      ti = super.resolve(citation, trustedSourceTI);
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
      ti = createNewTitleInstanceWithSiblings(citation)
    } else {
      /* We _do_ have a TI. Check that the attached work does not have an ID
       * If the attached work _does_ have an id, then we need a whole new work anyway
       */
      Work work = ti.work;
      if (!work) {
        throw new TIRSException(
          "No work found on TI: ${ti}",
          TIRSException.NO_WORK_MATCH
        )
      }

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

          // Refresh TI in hand
          ti.refresh();
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
          ti = createNewTitleInstanceWithSiblings(citation)
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

  private List<TitleInstance> getTISFromWork(String workId, String subtype = 'electronic') {
    return TitleInstance.executeQuery("""
      from TitleInstance as ti WHERE
        ti.work.id = :workId AND
        ti.subType.value = '${subtype}'
    """.toString(), [workId: workId]);
  }

  private TitleInstance getTitleInstanceFromWork(ContentItemSchema citation, Work work) {
    TitleInstance ti;
    List<TitleInstance> candidate_tis = getTISFromWork(work.id);
    switch (candidate_tis.size()) {
      case 1:
        ti = GrailsHibernateUtil.unwrapIfProxy(candidate_tis.get(0));
        updateIdentifiersAndSiblings(citation, work);
        // Ensure we refresh TI after updateIdentifiersAndSiblings
        ti.refresh();
        break;
      case 0:
        /* There is no electronic TI for this work, create it and siblings
         * ASSUMPTION making an assumption here that there are no print siblings
         * already on this work, and just creating everything from scratch. I'm not sure
         * this branch will ever get hit
         */
         ti = createNewTitleInstanceWithSiblings(citation, work)
        break;
      default:
        // If there are somehow multiple electronic title instances on the work at this stage, error out
        throw new TIRSException(
          "Multiple (${candidate_tis.size()}) electronic title instances found on Work: ${work}, skipping",
          TIRSException.MULTIPLE_TITLE_MATCHES
        )
        break;
    }

    return ti;
  }

  // Method to wrangle ids and siblings after the fact
  private void updateIdentifiersAndSiblings(ContentItemSchema citation, Work work) {
    // First up, wrangle IDs on single electronic title instance.
    // Shouldn't be in a situation where there are multiple, but can't hurt to check again
    List<TitleInstance> candidate_tis = getTISFromWork(work.id);
    TitleInstance electronicTI;
    switch (candidate_tis.size()) {
      case 1:
        electronicTI = GrailsHibernateUtil.unwrapIfProxy(candidate_tis.get(0));
        break;
      case 0:
        throw new TIRSException(
          "No electronic title instances found on Work: ${work}, cannot update identifiers and siblings",
          TIRSException.NO_TITLE_MATCH
        )
        break;
      default:
        // If there are somehow multiple electronic title instances on the work at this stage, error out
        throw new TIRSException(
          "Multiple (${candidate_tis.size()}) electronic title instances found on Work: ${work}, cannot update identifiers and siblings",
          TIRSException.MULTIPLE_TITLE_MATCHES
        )
        break;
    }

    // So, we now have a single electronic TI, make sure all identifiers match those from citation
    updateTIIdentifiers(electronicTI, citation.instanceIdentifiers);
    
    // TODO Next for each sibling, ensure citations are up to scratch
    List<PackageContentImpl> siblingCitations = getSiblingCitations(citation);
    /*
     * We need to first check that every siblingCitation matches to a TI
     * on the correct work (NOTE: not necessarily with approved identifiers)
     *
     * Then ensure every print sibling on the work can be matched by one of the sibling_citations
     *
     * FINALLY ensure that each sibling/sibling_citation pair goes through updateTIIdentifiers
     */

     // FIXME CAN WE ASSUME THAT MULTIPLE APPROVED IDs ON PRINT SIBLING IS AN ERROR?



  }

  private void updateTIIdentifiers(TitleInstance ti, Collection<IdentifierSchema> identifiers) {
    // First ensure all identifiers from citation are on TI
    identifiers.each {IdentifierSchema citation_id ->
      IdentifierOccurrence io = ti.identifiers.find { IdentifierOccurrence ti_id ->
        ti_id.identifier.ns.value == namespaceMapping(citation_id.namespace) &&
        ti_id.identifier.value == citation_id.value
      }

      if (!io) {
        // Identifier from citation not on TI, add it
        Identifier id = lookupOrCreateIdentifier(citation_id.value, citation_id.namespace)
        IdentifierOccurrence newIO = new IdentifierOccurrence([
          identifier: id,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ])

        ti.addToIdentifiers(sourceIdentifier);
        ti.save(flush: true, failOnError: true);
      } else if (io.status.value != APPROVED) {
        io.setStatusFromString(APPROVED)
        io.save(flush: true, failOnError: true);
      }
    }

    // Next ensure ONLY identifiers from citation are on TI
    ti.identifiers.each { IdentifierOccurrence io ->
      IdentifierSchema ids = identifiers.find { citation_id ->
        io.identifier.ns.value == namespaceMapping(citation_id.namespace) &&
        io.identifier.value == citation_id.value
      }

      if (!ids) {
        // Set status to ERROR
        io.setStatusFromString(ERROR)
        io.save(flush: true, failOnError: true);
      }
    }
  }
}
