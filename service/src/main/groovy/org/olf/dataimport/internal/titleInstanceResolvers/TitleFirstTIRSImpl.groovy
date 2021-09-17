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
          checkForEnrichment(result, citation, trustedSourceTI)
          break;
        default:
          log.warn("title matched ${num_matches} records . Unable to continue. Matching IDs: ${candidate_list.collect { it.id }}.");
          throw new RuntimeException("LOGDEBUG MULTIPLE MATCHES. (This isn't implemented yet");
          break;
      }
    }
    
    
  }
}
