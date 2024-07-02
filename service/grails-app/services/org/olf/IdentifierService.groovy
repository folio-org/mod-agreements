package org.olf

import org.olf.kb.IdentifierException

import org.olf.kb.IdentifierNamespace
import org.olf.kb.Identifier
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.Pkg
import org.olf.kb.TitleInstance

import groovy.util.logging.Slf4j

@Slf4j
// Cannot @CompileStatic while using DomainClass.lookupOrCreate${upperName} static method for RefdataValues
public class IdentifierService {

  private static final String IDENTIFIER_OCCURRENCE_MATCH_HQL = '''
    SELECT io from IdentifierOccurrence as io
    WHERE 
      io.resource.id = :initialTitleInstanceId AND
      io.identifier.ns.value = :identifierNamespace AND
      io.identifier.value = :identifierValue AND
      io.status.value = :status
  '''

  /*
    This method accepts an ArrayList of Maps of the form:
    [
      [
        identifierNamespace: "ISSN",
        identifierValue: "12345",
        targetTitleInstanceId: "abcde-12345-fghij",
        initialTitleInstanceId: "jihgf-54321-edcba"
      ],
      ...
    ]

    It will attempt to "reassign" each IdentifierOccurence in turn to the new TitleInstance
    Reassignation will actually consist of the IdentifierOccurence in question
    being marked as "ERROR", and a new Occurrence being created on the targetTI
  */
  def reassignFromFile (final ArrayList<Map<String, String>> reassignmentQueue) {
    reassignmentQueue.each{reassignmentMap ->
      IdentifierOccurrence.withNewTransaction{
        TitleInstance initialTI = TitleInstance.get(reassignmentMap.initialTitleInstanceId)
        TitleInstance targetTI = TitleInstance.get(reassignmentMap.targetTitleInstanceId)
        
        // Check that we could find the specified titleinstances
        if (targetTI != null & initialTI != null) {
          // Now look up an IdentifierOccurrence for the correct set of information
          List<IdentifierOccurrence> identifierOccurrences = IdentifierOccurrence.executeQuery(
            IDENTIFIER_OCCURRENCE_MATCH_HQL,
            [
              initialTitleInstanceId: reassignmentMap.initialTitleInstanceId,
              identifierNamespace: reassignmentMap.identifierNamespace,
              identifierValue: reassignmentMap.identifierValue.toLowerCase(),
              status: 'approved'
            ]
          )
          // Should only be one of these -- check and error out otherwise
          switch (identifierOccurrences.size()) {
            case 0:
              log.error("IdentifierOccurrence could not be found for (${reassignmentMap.identifierNamespace}:${reassignmentMap.identifierValue}) on initial TitleInstance.")
              break;
            case 1:
              IdentifierOccurrence identifierOccurrence = identifierOccurrences[0];
              // We have identified the single IO we wish to "move" to another TI

              // First we mark the current identifier occurrence as "error"
              identifierOccurrence.status = IdentifierOccurrence.lookupOrCreateStatus('error');
              identifierOccurrence.save(failOnError: true)

              // Next we create a new IdentifierOccurrence on the targetTI
              IdentifierOccurrence newIdentifierOccurrence = new IdentifierOccurrence(
                identifier: identifierOccurrence.identifier,
                resource: targetTI,
                status: IdentifierOccurrence.lookupOrCreateStatus('approved')
              ).save(failOnError: true)

              log.info("(${reassignmentMap.identifierNamespace}:${reassignmentMap.identifierValue}) IdentifierOccurrence for TI (${initialTI}) marked as ERROR, new IdentifierOccurrence created on TI (${targetTI})")

              break;
            default:
              log.error("Multiple valid IdentifierOccurrences matched for (${reassignmentMap.identifierNamespace}:${reassignmentMap.identifierValue}) on initial TitleInstance (${initialTI}).")
          }
        } else {
          if (initialTI == null) {
            log.error("TitleInstance could not be found for initialTitleInstanceId (${reassignmentMap.initialTitleInstanceId}).")
          }
          if (targetTI == null) {
            log.error("TitleInstance could not be found for targetTitleInstanceId (${reassignmentMap.targetTitleInstanceId}).")
          }
        }
      }
    }
  }

  // ERM-1649. This function acts as a way to manually map incoming namespaces onto known namespaces where we believe the extra information is unhelpful.
  // This is also the place to do any normalisation (lowercasing etc).
  public String namespaceMapping(String namespace) {

    String lowerCaseNamespace = namespace.toLowerCase()
    String result = lowerCaseNamespace
    switch (lowerCaseNamespace) {
      case 'eissn':
      case 'pissn':
      case 'eisbn':
      case 'pisbn':
        // This will remove the first character from the namespace
        result = lowerCaseNamespace.substring(1)
        break;
      default:
        break;
    }

    result
  }

  public void updatePackageIdentifiers(Pkg pkg, List<org.olf.dataimport.erm.Identifier> identifiers) {
    // Assume any package identifier information is the truth, and upsert/delete as necessary
    IdentifierOccurrence.withTransaction {
      // Firstly add any new identifiers from the identifiers list,
      // and keep a track of the relevant ids in the database of all the identifiers passed in by the process
      def identifiers_to_keep = [];

      identifiers.each {ident ->

        if ( ( ident.namespace != null ) && ( ident.value != null ) ) {
          IdentifierOccurrence existingIo = IdentifierOccurrence.executeQuery("""
            SELECT io FROM IdentifierOccurrence as io
            WHERE io.resource.id = :pkgId AND
              io.identifier.ns.value = :ns AND
              io.identifier.value = :value
          """.toString(), [pkgId: pkg.id, ns: ident.namespace, value: ident.value])[0]
  
          if (!existingIo || existingIo.id == null) {
            IdentifierNamespace ns = IdentifierNamespace.findByValue(ident.namespace) ?: new IdentifierNamespace([value: ident.namespace]).save(flush: true, failOnError: true)
            org.olf.kb.Identifier identifier = org.olf.kb.Identifier.findByNsAndValue(ns, ident.value) ?: new org.olf.kb.Identifier([
              ns: ns,
              value: ident.value
            ]).save(flush: true, failOnError: true)
  
            IdentifierOccurrence newIo = new IdentifierOccurrence([
              identifier: identifier,
              status: IdentifierOccurrence.lookupOrCreateStatus('approved')
            ])

            pkg.addToIdentifiers(newIo)
            // Need to save the package in order to get the id of the just created IdentifierOccurrence
            pkg.save(flush:true, failOnError: true)

            identifiers_to_keep << newIo.id
          } else if (existingIo) {
            identifiers_to_keep << existingIo.id
            if (existingIo.status.value == 'error') {
              // This Identifier Occurrence exists as ERROR, reset to APPROVED
              existingIo.status = IdentifierOccurrence.lookupOrCreateStatus('approved')
            }
          }
        } else {
          log.warn("Identifier with null namespace or value - skipping - package ID is ${pkg.id}");
        }
      }

      // Next we "delete" (set as error) any identifiers on the package not present in the identifiers list.
      List<IdentifierOccurrence> identsToRemove = IdentifierOccurrence.executeQuery("""
        SELECT io FROM IdentifierOccurrence AS io
        WHERE resource.id = :pkgId AND
          io.id NOT IN :keepList AND
          io.status.value = :approved
      """.toString(), [
        pkgId: pkg.id,
        keepList: identifiers_to_keep,
        approved: 'approved'
      ]);

      identsToRemove.each { ident -> 
        ident.status = IdentifierOccurrence.lookupOrCreateStatus('error')
      }

      // Finally save the package
      pkg.save(failOnError: true)
    }
  }

  public ArrayList<String> lookupIdentifier(final String value, final String namespace) {
    return Identifier.executeQuery("""
      SELECT iden.id from Identifier as iden
        where iden.value = :value and iden.ns.value = :ns
      """.toString(),
      [value:value, ns:namespaceMapping(namespace)]
    );
  }

  /*
   * TODO Should probably integration test these methods
   *
   *
   * ASSUMPTION -- Assumes context from calling code
   * This is where we can call the namespaceMapping function to ensure consistency in our DB
   */
  public IdentifierNamespace lookupOrCreateIdentifierNamespace(final String ns) {
    IdentifierNamespace.findOrCreateByValue(namespaceMapping(ns)).save(failOnError:true)
  }

  /*
   * ASSUMPTION -- Assumes context from calling code
   * Given an identifier { value:'1234-5678', namespace:'isbn' }
   * lookup or create an identifier in the DB to represent that info.
   */
  protected String lookupOrCreateIdentifier(final String value, final String namespace, boolean flush = true) {
    String result = null;

    // Ensure we are looking up properly mapped namespace (pisbn -> isbn, etc)
    def identifier_lookup = lookupIdentifier(value, namespace);

    switch(identifier_lookup.size() ) {
      case 0:
        IdentifierNamespace ns = lookupOrCreateIdentifierNamespace(namespace);
        result = new Identifier(ns:ns, value:value).save(failOnError:true, flush: flush).id;
        break;
      case 1:
        result = identifier_lookup[0];
        break;
      default:
        throw new IdentifierException(
          "Matched multiple identifiers for ${id}",
          IdentifierException.MULTIPLE_IDENTIFIER_MATCHES
        );
        break;
    }
    return result;
  }

}
