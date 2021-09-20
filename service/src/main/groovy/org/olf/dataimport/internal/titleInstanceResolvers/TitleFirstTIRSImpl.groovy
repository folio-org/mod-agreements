package org.olf.dataimport.internal.titleInstanceResolvers

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


import groovy.json.*

import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
class TitleFirstTIRSImpl extends BaseTIRS implements TitleInstanceResolverService {
  private static final String TEXT_MATCH_TITLE_HQL = '''
      SELECT ti from TitleInstance as ti
        WHERE 
          ti.name = :queryTitle
          AND ti.subType.value like :subtype
      '''

  private static final String ID_OCCURENCE_MATCH_HQL = '''
    SELECT title.name from IdentifierOccurrence as io
    LEFT JOIN io.title as title
      WHERE
        io.status.value = :approved
        AND io.identifier.id = :id_id
    '''

  /* This method lowercases, strips all leading and trailing whitespace,
   * and replaces all internal duplicated whitespaces with a single space
   */
  private String titleNormaliser(String s) {
    return s.toLowerCase().trim().replaceAll("\\s+", " ")
  }

  private List<TitleInstance> titleMatch(final String title, final String subtype) {
    List<TitleInstance> result = new ArrayList<TitleInstance>()
    TitleInstance.withSession { session ->
      try {
        result = TitleInstance.executeQuery(TEXT_MATCH_TITLE_HQL,[queryTitle: titleNormaliser(title), subtype:subtype], [max:20])
      }
      catch ( Exception e ) {
        log.debug("Problem attempting to run HQL Query ${TEXT_MATCH_TITLE_HQL} on string ${title} with subtype ${subtype}",e)
      }
    }
 
    return result
  }

  private List<TitleInstance> titleMatch(String title) {
    return titleMatch(title, 'electronic');
  }

  public TitleInstance resolve (ContentItemSchema citation, boolean trustedSourceTI) {
    TitleInstance result = null;

    List<TitleInstance> candidate_list = titleMatch(citation.title)
    if ( candidate_list != null ) {
      switch ( candidate_list.size() ) {
        case(0):
          log.debug("No title match, create new title")
          result = createNewTitleInstance(citation)
          // TODO implement this as per issue
          /* if (result != null) {
            createOrLinkSiblings(citation, result.work)
          } */
          break;
        case(1):
          log.debug("Exact match. Enrich title.")
          result = candidate_list.get(0)
          // TODO Link any new identifiers
          checkForEnrichment(result, citation, trustedSourceTI)
          linkIdentifiers(result, citation)
          break;
        default:
          log.warn("title matched ${num_matches} records . Unable to continue. Matching IDs: ${candidate_list.collect { it.id }}.");
          throw new RuntimeException("LOGDEBUG MULTIPLE MATCHES. (This isn't implemented yet");
          break;
      }
    }
  }

  private TitleInstance createNewTitleInstance(final ContentItemSchema citation, Work work = null) {
    TitleInstance result = null;

    TitleInstance.withNewTransaction {
      result = createNewTitleInstanceWithoutIdentifiers(citation, work)
    }

    // This will assign the instanceIds to the TI and 'createOrLinkSiblings' will create a sibling for each sibling id
    linkIdentifiers(result, citation)
    
    if (result != null) {
      // Refresh the newly minted title so we have access to all the related objects (eg Identifiers)
      result.refresh()
    }
    result
  }

  // When method passed with sibling = true, link Sibling identifiers, else link identifiers
  private void linkIdentifiers(TitleInstance title, ContentItemSchema citation, boolean sibling = false) {
    if (sibling) {
      citation.siblingInstanceIdentifiers.each {id -> linkIdentifier(id, title, citation)}
    } else {
      citation.instanceIdentifiers.each {id -> linkIdentifier(id, title, citation)}
    }
  }

  private void linkIdentifier(IdentifierSchema id, TitleInstance title, ContentItemSchema citation) {
    // Lookup or create identifier. If not already on an approved IdentifierOccurence we'll need to create it anyway
    def id_lookup = lookupOrCreateIdentifier(id.value, id.namespace);

    ArrayList<IdentifierOccurrence> io_lookup = IdentifierOccurrence.executeQuery(
      ID_OCCURENCE_MATCH_HQL,
      [
        id_id: id_lookup?.id,
        approved:APPROVED
      ], [max:20]
    );


    if (io_lookup.size() < 1) {
      // We have no approved IOs linked to TIs with that identifier information. Create one

      def io_record = new IdentifierOccurrence(
        title: title,
        identifier: id_lookup)
      
      io_record.setStatusFromString(APPROVED)
      io_record.save(flush:true, failOnError:true)
    } else {
      // Log warning allows for multiple TIs to have the same identifier through different occurences, I don't believe this should happen in production though
      log.warn("Identifier ${id} not assigned to ${title.name} as it is already assigned to title${io_lookup.size() > 1 ? "s" : ""}: ${io_lookup}")
      // TODO Ethan -- do we want to create an IdentifierOccurrence with status "Error" here rather than ignoring?
    }
  }
}
