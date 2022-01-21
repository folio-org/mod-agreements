package org.olf

import java.util.concurrent.TimeUnit

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.erm.Identifier

import org.olf.kb.RemoteKB
import org.olf.kb.TitleInstance
import org.slf4j.MDC

import grails.util.GrailsNameUtils
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

import org.olf.dataimport.internal.TitleInstanceResolverService
import org.olf.kb.MatchKey

@Slf4j
class MatchKeyService {
  // This returns a List<Map> which can then be used to set up matchKeys on an ErmResource
  public List<Map> collectMatchKeyInformation(ContentItemSchema pc) {
    // InstanceMedium Electronic vs Print lets us switch between instanceIdentifiers and siblingInstanceIdentifiers

    List<Map> matchKeys = []

    matchKeys.add([
      key: 'title_string',
      value: pc.title
    ])

   /* ERM-1799 not sure about this. PackageContentImpl contains a dateMonographPublishedPrint,
    * but org.olf.dataimport.erm.TitleInstance does not.
    * This means that Ingest can use dateMonographPublishedPrint, and Import using ERMSchema cannot.
    * (Import using InternalSchema will work fine, as will KBART import)
    * We can attempt to switch on the particular 
    */
    if (pc.class.name == 'org.olf.dataimport.erm.ContentItem') { // internal import does not contain dateMonographPublishedPrint
      // Attempt to use the instanceMedium combined with the dateMonographPublished to split them
      if (pc.instanceMedium?.toLowerCase() == 'electronic' && pc.dateMonographPublished) {
        matchKeys.add([
          key: 'date_electronic_published',
          value: pc.dateMonographPublished
        ])
      } else if (pc.instanceMedium?.toLowerCase() != 'electronic' && pc.dateMonographPublished) {
        matchKeys.add([
          key: 'date_print_published',
          value: pc.dateMonographPublished
        ])
      }
    } else {
      // In this case we should have potentially dateMonographPublished AND dateMonographPublishedPrint
      if (pc.dateMonographPublished) {
        matchKeys.add([
          key: 'date_electronic_published',
          value: pc.dateMonographPublished
        ])
      }

      if (pc.dateMonographPublishedPrint) {
        matchKeys.add([
          key: 'date_print_published',
          value: pc.dateMonographPublishedPrint
        ])
      }
    }

    // Deal with identifiers and sibling identifiers
    if (pc.instanceMedium?.toLowerCase() == 'electronic') {
      // The instance identifiers are the electronic versions
      matchKeys.addAll(parseMatchKeyIdentifiers(pc.instanceIdentifiers, pc.siblingInstanceIdentifiers))
    } else {
      // the sibling instance identifiers can be treated as the electronic versions
      matchKeys.addAll(parseMatchKeyIdentifiers(pc.siblingInstanceIdentifiers, pc.instanceIdentifiers))
    }

    if (pc.firstAuthor) {
      matchKeys.add([
        key: 'author',
        value: pc.firstAuthor
      ])
    }

    if (pc.firstEditor) {
      matchKeys.add([
        key: 'editor',
        value: pc.firstEditor
      ])
    }

    if (pc.monographVolume) {
      matchKeys.add([
        key: 'monograph_volume',
        value: pc.monographVolume
      ])
    }

    if (pc.monographEdition) {
      matchKeys.add([
        key: 'edition',
        value: pc.monographEdition
      ])
    }

    matchKeys
  }

  public List<Map> parseMatchKeyIdentifiers(Collection<Identifier> electronicIdentifiers, Collection<Identifier> printIdentifiers) {
    List<Map> matchKeys = []
    
    // Find first identifier which could be the electronic_issn
    String electronic_issn = electronicIdentifiers.find {ident -> ident.namespace ==~ /.*issn/}?.value // Should match eissn or issn
    if (electronic_issn) {
      matchKeys.add([key: 'electronic_issn', value: electronic_issn])
    }

    // Find first identifier which could be the electronic_isbn
    String electronic_isbn = electronicIdentifiers.find {ident -> ident.namespace ==~ /.*isbn/}?.value // Should match eisbn or isbn
    if (electronic_isbn) {
      matchKeys.add([key: 'electronic_isbn', value: electronic_isbn])
    }

    // Find first identifier which could be the print_issn
    String print_issn = printIdentifiers.find {ident -> ident.namespace ==~ /.*issn/}?.value // Should match pissn or issn
    if (print_issn) {
      matchKeys.add([key: 'print_issn', value: print_issn])
    }

    // Find first identifier which could be the print_isbn
    String print_isbn = printIdentifiers.find {ident -> ident.namespace ==~ /.*isbn/}?.value // Should match eisbn or isbn
    if (print_isbn) {
      matchKeys.add([key: 'print_isbn', value: print_isbn])
    }

    // Other identifiers could feasibly be in either
    addKeyFromIdentifierMaps(matchKeys, 'zdbid', electronicIdentifiers, printIdentifiers)
    addKeyFromIdentifierMaps(matchKeys, 'ezbid', electronicIdentifiers, printIdentifiers)
    addKeyFromIdentifierMaps(matchKeys, 'doi', electronicIdentifiers, printIdentifiers)

    matchKeys
  }

  void addKeyFromIdentifierMaps(List<Map> map, String key, Collection<Identifier> electronicIdentifiers, Collection<Identifier> printIdentifiers) {
    String returnValue = electronicIdentifiers.find {ident -> ident.namespace == key}?.value ?: // Check electronic list first
                         printIdentifiers.find {ident -> ident.namespace == key}?.value // fall back to print list
    if (returnValue) {
      map.add([key: key, value: returnValue])
    }
  }
}
