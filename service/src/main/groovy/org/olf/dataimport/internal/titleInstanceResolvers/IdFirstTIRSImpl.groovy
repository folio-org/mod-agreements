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
class IdFirstTIRSImpl extends BaseTIRS implements DataBinder {
  public String resolve(ContentItemSchema citation, boolean trustedSourceTI) {
    // log.debug("TitleInstanceResolverService::resolve(${citation})");
    String result = null;

    List<TitleInstance> candidate_list = classOneMatch(citation.instanceIdentifiers);
    int num_matches = candidate_list.size()
    int num_class_one_identifiers = countClassOneIDs(citation.instanceIdentifiers);
    if ( num_matches > 1 ) {
      log.debug("Class one match found multiple titles:: ${candidate_list}");
    }

    // We weren't able to match directly on an identifier for this instance - see if we have an identifier
    // for a sibling instance we can use to narrow down the list.
    if ( num_matches == 0 ) {
      candidate_list = siblingMatch(citation)
      num_matches = candidate_list.size()
      log.debug("siblingMatch for ${citation.title} found ${num_matches} titles");
      if ( num_matches > 1 ) {
        log.debug("Sibling match found multiple titles:: ${candidate_list}");
      }
    }

    // If we didn't have a class one identifier AND we weren't able to match anything via
    // a sibling match, try to do a fuzzy match as a last resort
    // DO NOT ATTEMPT if there is no title on the citation
    if ( ( num_matches == 0 ) && ( num_class_one_identifiers == 0 ) && citation.title ) {
      log.debug("No matches on identifier - try a fuzzy text match on title(${citation.title})");
      // No matches - try a simple title match
      candidate_list = titleMatch(citation.title,MATCH_THRESHOLD);
      num_matches = candidate_list.size()
    }

    if ( candidate_list != null ) {
      switch ( num_matches ) {
        case(0):
          log.debug("No title match, create new title ${citation}")
          result = createNewTitleInstanceWithSiblings(citation).id
          break;
        case(1):
          log.debug("Exact match. Enrich title.")
          result = candidate_list.get(0).id
          checkForEnrichment(candidate_list.get(0).id, citation, trustedSourceTI)
          break;
        default:
          throw new TIRSException(
            "title matched ${num_matches} records with a threshold >= ${MATCH_THRESHOLD} . Unable to continue. Matching IDs: ${candidate_list.collect { it.id }}. class one identifier count: ${num_class_one_identifiers}",
            TIRSException.MULTIPLE_TITLE_MATCHES,
          );
          break;
      }
    }

    return result;
  }

  /* This method WILL NOT set previously ERRORed sibling identifiers back to APPROVED
   * This won't matter for IdFirstTIRS as this method is only called for new TIs
   * But if called externally, such as by WorkSourceIdentifierTIRS, if that behaviour is
   * expected, it will need to be performed externally too
   */
  protected void upsertSiblings(ContentItemSchema citation, String workId) {
    List<TitleInstance> candidate_list = []

    // Lets try and match based on sibling identifiers. 
    // Our first "alternate" matching strategy. Often, KBART files contain the ISSN of the print edition of an electronic work.
    // The line is not suggesting that buying an electronic package includes copies of the physical item, its more a way of saying
    // "The electronic item described by this line relates to the print item identified by X".
    // In the bibframe nomenclature, the print and electronic items are two separate instances. Therefore, creating an electronic
    // identifier with the ID of the print item does not seem sensible. HOWEVER, we would still like to be able to be able to match
    // a title if we know that it is a sibling of a print identifier.
    List<PackageContentImpl> siblingCitations = getSiblingCitations(citation);
    if ( siblingCitations.size() != 0 ) {
      // One sibling for each citation
      siblingCitations.each { sibling_citation ->
        // Find ALL siblings on this work who match this identifier (should only be one id because of above code)
        candidate_list = directMatch(sibling_citation.instanceIdentifiers, workId, 'print', false)

        switch ( candidate_list.size() ) {
          case 0:
            log.debug("Create sibling print instance for citation ${sibling_citation}")
            createNewTitleInstance(sibling_citation, workId)
            break
          case 1:
            // Already exists somehow -- should not be possible on IdFirstTIRS as we create sibling along with work
            log.warn("Sibling already exists for identifiers: ${sibling_citation.instanceIdentifiers} on Work ${workId}.")
            break;
          default:
            // Problem -- DEFINITELY should not see this one
            log.warn("Detected multiple records for sibling instance match")
            break;
        }
      }
    }
  }

  protected TitleInstance createNewTitleInstance(final ContentItemSchema citation, String workId = null) {
    TitleInstance result = null;

    result = createNewTitleInstanceWithoutIdentifiers(citation, workId)
    citation.instanceIdentifiers.each { id ->
      Identifier id_lookup = lookupOrCreateIdentifier(id.value, id.namespace)
      def io_record = new IdentifierOccurrence(
        status: IdentifierOccurrence.lookupOrCreateStatus('approved'),
        identifier: id_lookup
      ).save(failOnError: true)

      result.addToIdentifiers(io_record)
    }

    if (result != null) {
      // Refresh the newly minted title so we have access to all the related objects (eg Identifiers)
      result.save(failOnError: true, flush: true)
    }
    result
  }

  // Setting to public so we can reuse this in WorkSourceIdentifierTIRS
  protected TitleInstance createNewTitleInstanceWithSiblings(ContentItemSchema citation, String workId = null) {
    TitleInstance result;
    result = createNewTitleInstance(citation, workId)
    if (result != null) {
      // We assume that the incoming citation already has split ids and siblingIds
      upsertSiblings(citation, result.work.id)
    }

    return result
  }

  /* -------- MATCHING METHODS --------*/

  protected static final float MATCH_THRESHOLD = 0.775f
  protected static final String TEXT_MATCH_TITLE_HQL = '''
   SELECT ti from TitleInstance as ti
    WHERE 
      trgm_match(ti.name, :qrytitle) = true
      AND similarity(ti.name, :qrytitle) > :threshold
      AND ti.subType.value like :subtype
    ORDER BY similarity(ti.name, :qrytitle) desc
  '''

  protected String getSiblingMatchHQL(Collection<IdentifierSchema> identifiers) {
    // Do not pass "false" as second param here, we only match on identifiers which are "approved"
    String identifierHQL = buildIdentifierHQL(identifiers)

    // Ensure we are only checking title instances where work is set (?)
    String outputHQL = """
      from TitleInstance as ti
      WHERE 
        ti.work IS NOT NULL AND
        exists ( SELECT sibling FROM TitleInstance as sibling 
                   JOIN sibling.identifiers as io
                 WHERE 
                   sibling.work IS NOT NULL AND
                   sibling.work = ti.work AND
                    ${identifierHQL}
                ) AND
        ti.subType.value = 'electronic'
    """
    //log.debug("LOGDEBUG SIBMATCH OUTPUT HQL: ${outputHQL}")
    return outputHQL
  }

  protected String getDirectMatchHQL(Collection<IdentifierSchema> identifiers, String workId = null, boolean approvedIdsOnly = true) {
    String identifierHQL = buildIdentifierHQL(identifiers, approvedIdsOnly)

    String outputHQL = """
      from TitleInstance as ti
        WHERE 
          exists ( SELECT io FROM IdentifierOccurrence as io 
                  WHERE
                    io.resource.id = ti.id AND
                    ${identifierHQL}
                  ) AND
          ti.subType.value = :subtype
    """

    if (workId !== null) {
      outputHQL += """ AND
        ti.work.id = '${workId}'
      """
    }
    //log.debug("LOGDEBUG DIRECTMATCH OUTPUT HQL: ${outputHQL}")
    return outputHQL
  }

  /*
   * Being passed a map of namespace, value pair maps, attempt to locate any title instances with class 1 identifiers (ISSN, ISBN, DOI)
   */
  protected List<TitleInstance> classOneMatch(final Iterable<IdentifierSchema> identifiers) {
    // We want to build a list of all the title instance records in the system that match the identifiers. Hopefully this will return 0 or 1 records.
    // If it returns more than 1 then we are in a sticky situation, and cleverness is needed.
    final List<TitleInstance> result = new ArrayList<TitleInstance>()

    int num_class_one_identifiers = 0;

    identifiers.each { IdentifierSchema id ->
      if ( class_one_namespaces?.contains(id.namespace.toLowerCase()) ) {

        num_class_one_identifiers++;

        // Look up each identifier
        // log.debug("${id} - try class one match");


        /*
         * At this stage we could be trying to match incoming
        {
          namespace: 'eISSN',
          value: 1234-5678
        }
         * with something that in our system looks like:
        {
          namespace: 'issn',
          value: 1234-5678
        }
         * We have to know that eissn == issn etc... Use namespaceMapping function
         */

        /* NOTE: We have the possibility for Kiwi that an existing TI is in place with namespace eissn,
         * since cleanup is a complicated matter.
         * For the time being, allow matching BOTH of `eissn` -> `issn` (As per the story),
         * but also `eissn` -> `eissn` AND `issn` -> `eissn` in our db.
         * To allow for `eissn` -> `eissn` the (ns:id.namespace) case is sufficient.
         * To allow for `issn` -> `eissn` we also need
         */
        final List<Identifier> id_matches = Identifier.executeQuery("""
          SELECT id FROM Identifier AS id
          WHERE
            id.value = :value AND
            (
              id.ns.value = :nsm OR
              id.ns.value = :ns OR
              id.ns.value = :ens OR
              id.ns.value = :pns
            )""".toString(),
          [
            value:id.value,
            ns:id.namespace.toLowerCase(),
            nsm:namespaceMapping(id.namespace),
            ens:mapNamespaceToElectronic(id.namespace),
            pns:mapNamespaceToPrint(id.namespace)
          ],
          [max:2]
        )

        if (id_matches.size() > 1) {
          throw new TIRSException(
            "Multiple (${id_matches.size()}) matches found for identifier ${id.namespace}::${id.value}",
            TIRSException.MULTIPLE_IDENTIFIER_MATCHES,
          );
        }

        // For each matched (It should only ever be 1)
        id_matches.each { matched_id ->
          // For each occurrence where the STATUS is APPROVED
          matched_id.occurrences.each { io ->
            if ( io.status?.value == APPROVED ) {
              if ( result.contains(io.resource) ) {
                // We have already seen this title, so don't add it again
              }
              else {
                // log.debug("Adding title ${io.resource.id} ${io.resource.title} to matches for ${matched_id}");
                result << io.resource
              }
            }
            // ERM-1986 Don't throw on non approved occurrence existing, just skip
            //else {
            //  throw new RuntimeException("Match on non-approved");
            //}
          }
        }
      }
      else {
        // log.debug("Identifier ${id} not from a class one namespace");
      }
    }

    // log.debug("At end of classOneMatch, resut contains ${result.size()} titles");
    return result;
  }

  /**
   * Attempt a fuzzy match on the title
   */
  protected List<TitleInstance> titleMatch(String title, float threshold) {
    return titleMatch(title, threshold, 'electronic');
  }

  protected List<TitleInstance> titleMatch(final String title, final float threshold, final String subtype) {
    String matchTitle = StringUtils.truncate(title);

    List<TitleInstance> result = new ArrayList<TitleInstance>()
    TitleInstance.withSession { session ->
      try {
        result = TitleInstance.executeQuery(TEXT_MATCH_TITLE_HQL,[qrytitle: (matchTitle),threshold: (threshold), subtype:subtype], [max:20])
      }
      catch ( Exception e ) {
        log.debug("Problem attempting to run HQL Query ${TEXT_MATCH_TITLE_HQL} on string ${matchTitle} with threshold ${threshold}",e)
      }
    }
 
    return result
  }

  // Direct match will find ALL title instances which match ANY of the instanceIdentifiers passed. Is extremely naive.
  // Allow for non-approved identifiers if we wish
  protected List<TitleInstance> directMatch(final Iterable<IdentifierSchema> identifiers, String workId = null, String subtype = 'electronic', boolean approvedIdsOnly = true) {
    if (identifiers.size() <= 0) {
      return []
    }
    List<String> titleList = TitleInstance.executeQuery(getDirectMatchHQL(identifiers, workId, approvedIdsOnly),[subtype: subtype]).collect { it.id };
    return listDeduplictor(titleList)
  }

  /**
   * Return a list of the siblings for this instance. Sometimes vendors identify a title by citing the issn of the print edition.
   * we model the print and electronic as 2 different title instances, linked by a common work. This method looks up/creates any sibling instances
   * by matching the print instance, then looking for a sibling with type "electronic"
   */
  protected List<TitleInstance> siblingMatch(ContentItemSchema citation) {
    Collection<IdentifierSchema> classOneIds = citation.siblingInstanceIdentifiers.findAll { class_one_namespaces?.contains(it.namespace.toLowerCase()) };
    // Break out if no sibling instance ids
    if (classOneIds.size() <= 0) {
      return []
    }

    List<String> titleList = TitleInstance.executeQuery(getSiblingMatchHQL(classOneIds)).collect { it.id };
    return listDeduplictor(titleList)
  }
}
