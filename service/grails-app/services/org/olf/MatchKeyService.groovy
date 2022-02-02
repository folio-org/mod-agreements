package org.olf

import java.util.concurrent.TimeUnit

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.erm.Identifier

import org.olf.IdentifierService

import org.olf.kb.RemoteKB
import org.olf.kb.ErmResource
import org.olf.kb.TitleInstance
import org.olf.kb.PackageContentItem
import org.olf.kb.MatchKey
import org.olf.kb.IdentifierOccurrence


import org.slf4j.MDC

import grails.util.GrailsNameUtils
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

import org.olf.dataimport.internal.TitleInstanceResolverService
import org.olf.kb.MatchKey

import grails.plugin.json.builder.JsonOutput

@Slf4j
class MatchKeyService {
  IdentifierService identifierService

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
      (pc.instanceIdentifiers ?: []).each {ident ->
        matchKeys.add([key: "electronic_${identifierService.namespaceMapping(ident.namespace)}", value: ident.value])
      }
      (pc.siblingInstanceIdentifiers ?: []).each {ident ->
        matchKeys.add([key: "print_${identifierService.namespaceMapping(ident.namespace)}", value: ident.value])
      }
    } else {
      // the sibling instance identifiers can be treated as the electronic versions
      (pc.siblingInstanceIdentifiers ?: []).each {ident ->
        matchKeys.add([key: "electronic_${identifierService.namespaceMapping(ident.namespace)}", value: ident.value])
      }
      (pc.instanceIdentifiers ?: []).each {ident ->
        matchKeys.add([key: "print_${identifierService.namespaceMapping(ident.namespace)}", value: ident.value])
      }
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

  /*
    This method takes a resource and a map of match keys.
    Any new keys will be added to the resource and any keys
    with mismatched values will be updated.
    NOTE this function does not care about "trusted" sources, that logic belongs elsewhere
   */
  void upsertMatchKeys(ErmResource resource, List<Map> matchKeyData, boolean saveOnExit = true) {
    /*
     * ERM-1799 What do we do if there's match keys on the resource
     * which are missing from incoming data? -- Do we ignore deletion or allow it? 
     */
    matchKeyData.each {mk -> 
      def resourceMatchKey = resource.matchKeys.find {rmk -> rmk.key == mk.key}

      // If there was no matching key then simply create a new one
      if (!resourceMatchKey) {
        resource.addToMatchKeys(new MatchKey(mk))
      } else if (mk.value != resourceMatchKey.value) {
        // Mismatched value, update
        resourceMatchKey.value = mk.value
      }

      if (saveOnExit) {
        resource.save(failOnError: true) // This save will cascade to all matchKeys
      }
    }
  }

  /*
   * This method checks for any PCIs without appended match_key information in the DB,
   * and attempts to generate them directly from the data.
   * Care should be taken when calling this method, as it will proliferate inaccuracies
   * to a deeper part of the matching process.
   * PCIs without match keys are batch fetched in case of large numbers
   */
  void generateMatchKeys() {
    final int pciBatchSize = 100
    int pciBatchCount = 0
    int pciCount = 0
    List<PackageContentItem> pcis = PackageContentItem.createCriteria().list([
        max: pciBatchSize,
        offset: pciBatchSize * pciBatchCount
      ]) {
      isEmpty('matchKeys') 
    }
    while (pcis && pcis.size() > 0) {
      pciBatchCount ++
      pcis.each { pci ->
        naiveAssignMatchKeys(pci)
        pciCount++
      }

      // Next page
      pcis = PackageContentItem.createCriteria().list([
        max: pciBatchSize,
        offset: pciBatchSize * pciBatchCount
      ]) {
        isEmpty('matchKeys') 
      }
    }

    log.info("Attempted to generate match keys for ${pciCount} PCIs in system")
  }

  void naiveAssignMatchKeys(PackageContentItem pci) {
    log.info("Attempting to naively assign match keys for PCI (${pci})")
    List<Map> matchKeys = []
    /* Attempt to assign match keys from ingested data.
      * The actual model allows fore more complicated setups than this,
      * but any errors can be fixed by reimporting specific packages
      */
    TitleInstance electronicTI
    TitleInstance printTI
    if (pci.pti.titleInstance.subType.value.toLowerCase() == 'electronic') {
      electronicTI = pci.pti.titleInstance
      printTI = pci.pti.titleInstance.getRelatedTitles()?.find {relti -> relti.subType.value.toLowerCase() == 'print'}
    } else {
      printTI = pci.pti.titleInstance
      electronicTI = pci.pti.titleInstance.getRelatedTitles()?.find {relti -> relti.subType.value.toLowerCase() == 'electronic'}
    }

    matchKeys.add(naiveGetPropertyMatchKey('title_string', 'name', electronicTI, printTI))
    matchKeys.add(naiveGetPropertyMatchKey('author', 'firstAuthor', electronicTI, printTI))
    matchKeys.add(naiveGetPropertyMatchKey('editor', 'firstEditor', electronicTI, printTI))
    matchKeys.add(naiveGetPropertyMatchKey('monograph_volume', 'monographVolume', electronicTI, printTI))
    matchKeys.add(naiveGetPropertyMatchKey('edition', 'monographEdition', electronicTI, printTI))

    // Add the identifiers
    matchKeys.addAll(naiveGetIdentifierMatchKeys(electronicTI?.identifiers, printTI?.identifiers))

    // Upsert generated match keys
    PackageContentItem.withNewTransaction{
      upsertMatchKeys(pci, matchKeys, true)
    }
  }

  List<Map> naiveGetIdentifierMatchKeys(Collection<IdentifierOccurrence> electronicIdentifiers = [], Collection<IdentifierOccurrence> printIdentifiers = []) {
    List<Map> matchKeys = []
    electronicIdentifiers.each {ident -> 
      if (ident.status.value.toLowerCase() == 'approved') {
        matchKeys.add([key: "electronic_${identifierService.namespaceMapping(ident.identifier?.ns?.value)}", value: ident.identifier?.value])
      }
    }

    printIdentifiers.each {ident -> 
      if (ident.status.value.toLowerCase() == 'approved') {
        matchKeys.add([key: "print_${identifierService.namespaceMapping(ident.identifier?.ns?.value)}", value: ident.identifier?.value])
      }
    }

    matchKeys
  }

  Map naiveGetPropertyMatchKey(List<Map> matchKeys, String key, String property, TitleInstance electronicTI, TitleInstance printTI) {
    String value = (electronicTI ?: [:])[property] ?: (printTI ?: [:])[property] // Attempt to fetch property from null safe electronic or print TIs

    if (value) {
      return [key: key, value: value];
    }
  }
}
