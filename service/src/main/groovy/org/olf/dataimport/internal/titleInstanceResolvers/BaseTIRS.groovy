package org.olf.dataimport.internal.titleInstanceResolvers

import org.olf.IdentifierService
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.TitleInstance
import org.olf.kb.Work

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 * This is a base TIRS class to give any implementing classes some shared tools to use 
 */
@Slf4j
@Transactional
class BaseTIRS {
    @Autowired
    IdentifierService identifierService
    protected static final def APPROVED = 'approved'
    protected static final def ERROR = 'error'

  // ERM-1649. This function acts as a way to manually map incoming namespaces onto known namespaces where we believe the extra information is unhelpful.
  // This is also the place to do any normalisation (lowercasing etc).
  protected String namespaceMapping(String namespace) {
    identifierService.namespaceMapping(namespace)
  }

    protected static def class_one_namespaces = [
    'zdb',
    'isbn',
    'issn',  // This really isn't true - we get electronic items identified by the issn of their print sibling.. Needs thought
    'eissn', // We want to accept eissn still as a class-one-namespace, even though internally we flatten to 'issn'. ERM-1649
    'doi'
  ];

  /*
   * Given an identifier in a citation { value:'1234-5678', namespace:'isbn' } lookup or create an identifier in the DB to represent that info
   */
  protected Identifier lookupOrCreateIdentifier(final String value, final String namespace) {
    Identifier result = null;

    // Ensure we are looking up properly mapped namespace (pisbn -> isbn, etc)
    def identifier_lookup = Identifier.executeQuery('select id from Identifier as id where id.value = :value and id.ns.value = :ns',[value:value, ns:namespaceMapping(namespace)]);

    switch(identifier_lookup.size() ) {
      case 0:
        IdentifierNamespace ns = lookupOrCreateIdentifierNamespace(namespace);
        result = new Identifier(ns:ns, value:value).save(flush:true, failOnError:true);
        break;
      case 1:
        result = identifier_lookup.get(0);
        break;
      default:
        throw new RuntimeException("Matched multiple identifiers for ${id}");
        break;
    }
    return result;
  }

  /*
   * This is where we can call the namespaceMapping function to ensure consistency in our DB
   */
  protected IdentifierNamespace lookupOrCreateIdentifierNamespace(final String ns) {
    IdentifierNamespace.findOrCreateByValue(namespaceMapping(ns)).save(flush:true, failOnError:true)
  }


  /*
   * Check to see if the citation has properties that we really want to pull through to
   * the DB. In particular, for the case where we have created a stub title record without
   * an identifier, we will need to add identifiers to that record when we see a record that
   * suggests identifiers for that title match.
   */ 
  protected void checkForEnrichment(TitleInstance title, ContentItemSchema citation, boolean trustedSourceTI) {
    log.debug("Checking for enrichment of Title Instance: ${title} :: trusted: ${trustedSourceTI}")
    def changes = 0;
    
    if (trustedSourceTI == true) {
      log.debug("Trusted source for TI enrichment--enriching")

      if (title.name != citation.title) {
        title.name = citation.title
        changes++
      }

      /*
       * For some reason whenever a title is updated with just refdata fields it fails to properly mark as dirty.
       * The below solution of '.markDirty()' is not ideal, but it does solve the problem for now.
       * TODO: Ian to Review with Ethan - this makes no sense to me at the moment
       *
       * If the "Authoritative" publication type is not equal to whatever mad value a remote site has sent then
       * replace the authortiative value with the one sent?
       */
      if (title.publicationType?.value != citation.instancePublicationMedia) {
       
        title.publicationTypeFromString = citation.instancePublicationMedia
        title.markDirty()
        changes++
      }

      if (validateCitationType(citation?.instanceMedia)) {
        if ((title.type == null) || (title.type.value != citation.instanceMedia)) {
          title.typeFromString = citation.instanceMedia
          title.markDirty()
          changes++
        }
      } else {
        log.error("Type (${citation.instanceMedia}) does not match 'serial' or 'monograph' for title \"${citation.title}\", skipping field enrichment.")
      }

      if (title.dateMonographPublished != citation.dateMonographPublished) {
        title.dateMonographPublished = citation.dateMonographPublished
        changes++
      }

      if (title.firstAuthor != citation.firstAuthor) {
        title.firstAuthor = citation.firstAuthor
        changes++
      }
      
      if (title.firstEditor != citation.firstEditor) {
        title.firstEditor = citation.firstEditor
        changes++
      }

      if (title.monographEdition != citation.monographEdition) {
        title.monographEdition = citation.monographEdition
        changes++
      }

      if (title.monographVolume != citation.monographVolume) {
        title.monographVolume = citation.monographVolume
        changes++
      }

      // Ensure we only save title on enrich if changes have been made
      if (changes > 0 && !title.save(flush: true)) {
        title.errors.fieldErrors.each {
          log.error("Error saving title. Field ${it.field} rejected value: \"${it.rejectedValue}\".")
        }
      }
    } else {
      log.debug("Not a trusted source for TI enrichment--skipping")
    }
    return null;
  }

  private boolean validateCitationType(String tp) {
    return tp != null && ( tp.toLowerCase() == 'monograph' || tp.toLowerCase() == 'serial' )
  }

  // Different TIRS implementations will have different workflows with identifiers, but the vast majority of the creation will be the same
  // We assume that the incoming citation already has split ids and siblingIds
  protected TitleInstance createNewTitleInstanceWithoutIdentifiers(final ContentItemSchema citation, Work work = null) {
    TitleInstance result = null

    // Ian: adding this - Attempt to make sense of the instanceMedia value we have been passed
    //
    // I'm entirely befuddled by whats going on in this service with the handling of instanceMedia, resource_type and instancePublicationMedia -
    // it's a confused mess. This method is about fuzzily absorbing a citation doing the best we can. To reject an entry out of hand because a value
    // does not match an arbitrarily internally decided upon string leaves callers with no way of resolving what went wrong or what to do about it.
    // I'm adding this to make the integraiton tests pass again, and try to regain some sanity.
    // It would be more sensible to stick with the single instanceMedia field and if the value is not one we expect, stash the value in
    // a memo field here and convert as best we can.

    // Journal or Book etc
    def resource_type = citation.instanceMedia?.trim()

    // This means that publication type can no longer be set directly by passing in instanceMedia - that 
    // cannot be the right thing to do.
    def resource_pub_type = citation.instancePublicationMedia?.trim()


    switch( citation.instanceMedia?.toLowerCase() ) {
      case null: // No value, nothing we can do
        break;
      case 'serial': // One of our approved values
        break;
      case 'monograph': // One of our approved values
        break;
      case 'newspaper':
      case 'journal':
        // If not already set, stash the instanceMedia we are looking at in instancePublicationMedia
        // citation.instanceMedia = 'serial';
        resource_type = 'serial'
        resource_pub_type = citation.instancePublicationMedia ?: citation.instanceMedia ?: 'serial'
        break;
      case 'BKM':
      case 'book':
        // If not already set, stash the instanceMedia we are looking at in instancePublicationMedia
        // citation.instanceMedia = 'monograph';
        resource_type = 'monograph'
        resource_pub_type = citation.instancePublicationMedia ?: citation.instanceMedia ?: 'monograph'
        break;
      default:
        log.warn("Unhandled media type ${citation.instanceMedia}");
        break;
    }

    // With the introduction of fuzzy title matching, we are relaxing this constraint and
    // will expect to enrich titles without identifiers when we next see a record. BUT
    // this needs elaboration and experimentation.

    // Validate
    Map title_is_valid = [
      titleExists: ( citation.title != null ) && ( citation.title.length() > 0 ),
      typeMatchesInternal: validateCitationType(resource_type)
    ]

    if ( title_is_valid.count { k,v -> v == false} == 0 ) {

      if ( work == null ) {
        work = new Work(title:citation.title).save(flush:true, failOnError:true)
      }

      // Print or Electronic
      def medium = citation.instanceMedium?.trim()

      // FIXME This seems to be unused
      def resource_coverage = citation?.coverage
      result = new TitleInstance(
        name: citation.title,

        dateMonographPublished: citation.dateMonographPublished,
        firstAuthor: citation.firstAuthor,
        firstEditor: citation.firstEditor,
        monographEdition: citation.monographEdition,
        monographVolume: citation.monographVolume,

        work: work
      )

      // We can trust these by the check above for file imports and through logic in the adapters to set pubType and type correctly
      result.typeFromString = resource_type

      if ( ( resource_pub_type != null ) && ( resource_pub_type.length() > 0 ) ) {
        result.publicationTypeFromString = resource_pub_type
      }
      
      if ((medium?.length() ?: 0) > 0) {
        result.subTypeFromString = medium
      }
      
      result.save(flush:true, failOnError:true)

     // Leave identifier work out, as this may differ between implementations of "resolve" method
    }
    else {

      // Run through the failed validation one by one and throw relavent errors
      if (!title_is_valid.titleExists) {
        log.error("Create title failed validation check - insufficient data to create a title record");
      }

      if (!title_is_valid.typeMatchesInternal) {
        log.error("Create title \"${citation.title}\" failed validation check - type (${citation.instanceMedia.toLowerCase()}) does not match 'serial' or 'monograph'");
      }
      
      // We will return null, which means no title
      // throw new RuntimeException("Insufficient detail to create title instance record");
    }

    result
  }

}
