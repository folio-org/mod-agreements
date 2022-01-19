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

// TODO ERM-1799 this likely isn't needed
import groovy.json.JsonOutput


/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
class TitleIngestService implements DataBinder {

  TitleInstanceResolverService titleInstanceResolverService
  TitleEnricherService titleEnricherService


  public Map upsertTitle(ContentItemSchema pc) {
    return upsertTitle(pc, 'LOCAL_TITLE')
  }

  public Map upsertTitle(ContentItemSchema pc, String remotekbname) {
    RemoteKB kb = RemoteKB.findByName(remotekbname)
    TitleInstance.withNewTransaction {
      if (!kb) {
       kb = new RemoteKB( name:remotekbname,
                          rectype: RemoteKB.RECTYPE_TITLE,
                          active: Boolean.TRUE,
                          readonly:readOnly,
                          trustedSourceTI:false).save(flush:true, failOnError:true)
      }

      upsertTitle(pc, kb)
    }
  }

  // Bear in mind the kb's rectype here could be RECTYPE_PACKAGE, if called from packageIngestService
  public Map upsertTitle(ContentItemSchema pc, RemoteKB kb, Boolean trusted = null) {
    log.debug("TitleIngestService::UpsertTitle called")
    def result = [
      startTime: System.currentTimeMillis(),
    ]
    // TODO ERM-1801 Does ContentItemSchema need to be able to say trustedSourceTI or not? eg for manual import where you want it to be able to create but not update TIs
    //Boolean trustedSourceTI = trusted ?: package_data.header?.trustedSourceTI ?: kb.trustedSourceTI

    // If we're not explicitly handed trusted information, default to whatever the remote KB setting is
    Boolean trustedSourceTI = trusted ?: kb.trustedSourceTI
    if (trustedSourceTI == null) {
      // If it somehow remains unset, default to false, but with warning
      log.warn("Could not deduce trustedSourceTI setting for title, defaulting to false")
      trustedSourceTI = false
    }

    result.updateTime = System.currentTimeMillis()

    // resolve may return null, used to throw exception which causes the whole package to be rejected. Needs
    // discussion to work out best way to handle.

    // ERM-1847 Changed assert in TIRS to an explicit exception, which we can catch here. Should stop job from hanging on bad data
    TitleInstance title;
    try {
      title = titleInstanceResolverService.resolve(pc, trustedSourceTI)
    } catch (Exception e){
      log.error("Error resolving title (${pc.title}), skipping ${e.message}")
    }

    if (title != null) {
      /* ERM-1801
        * For now this secondary enrichment step is here rather than the PackageIngestService,
        * as it uses information about electronic vs print which the resolver service might have to separate out first.
        * So even when ingesting a title stream we want to resolve, sort into print vs electronic, then get the TI and enrich based on subType
        */
      String sourceIdentifier = pc?.sourceIdentifier
      titleEnricherService.secondaryEnrichment(kb, sourceIdentifier, title.id);

      // Append titleInstanceId to resultList, so we can use it elsewhere to look up titles ingested with this method
      result.titleInstanceId = title.id
      result.finishTime = System.currentTimeMillis()
    } else {
      String message = "Unable to resolve title from ${pc.title} with identifiers ${pc.instanceIdentifiers}"
      log.error(message)
    }

    result
  }

  //TODO ERM-1799 centralise the ContentItemSchema -> match key process to this service?
  // This returns a List<Map> which can then be used to set up matchKeys on an ErmResource
  public List<Map> collectMatchKeyInformation(ContentItemSchema pc) {
    // InstanceMedium Electronic vs Print lets us switch between instanceIdentifiers and siblingInstanceIdentifiers

    List<Map> matchKeys = []

    matchKeys.add([
      key: 'title_string',
      value: pc.title
    ])

    if (pc.instanceMedium?.toLowerCase() == 'electronic') {
      // The instance identifiers are the electronic versions
      matchKeys.addAll(parseMatchKeyIdentifiers(pc.instanceIdentifiers, pc.siblingInstanceIdentifiers))
    } else {
      // the sibling instance identifiers can be treated as the electronic versions
      matchKeys.addAll(parseMatchKeyIdentifiers(pc.siblingInstanceIdentifiers, pc.instanceIdentifiers))
    }

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

  public List<Map> parseMatchKeyIdentifiers(List<Identifier> electronicIdentifiers, List<Identifier> printIdentifiers) {
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

  void addKeyFromIdentifierMaps(List<Map> map, String key, List<Identifier> electronicIdentifiers, List<Identifier> printIdentifiers) {
    String returnValue = electronicIdentifiers.find {ident -> ident.namespace == key}?.value ?: // Check electronic list first
                         printIdentifiers.find {ident -> ident.namespace == key}?.value // fall back to print list
    if (returnValue) {
      map.add([key: key, value: returnValue])
    }
  }
}
