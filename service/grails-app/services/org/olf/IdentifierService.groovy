package org.olf

import org.olf.kb.IdentifierOccurrence
import org.olf.kb.TitleInstance

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import grails.gorm.transactions.Transactional

@Slf4j
// Cannot @CompileStatic while using DomainClass.lookupOrCreate${upperName} static method for RefdataValues
public class IdentifierService {

  private static final String IDENTIFIER_OCCURRENCE_MATCH_HQL = '''
    SELECT io from IdentifierOccurrence as io
    WHERE 
      io.title.id = :initialTitleInstanceId AND
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
                title: targetTI,
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
}

