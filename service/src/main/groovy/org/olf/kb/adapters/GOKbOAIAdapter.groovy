package org.olf.kb.adapters

import static groovy.transform.TypeCheckingMode.SKIP

import java.text.*

import org.olf.TitleEnricherService
import org.olf.dataimport.internal.InternalPackageImpl
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.kb.KBCache
import org.olf.kb.KBCacheUpdater
import org.springframework.validation.BindingResult

import grails.web.databinding.DataBinder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.GPathResult
import groovyx.net.http.*

/**
 * An adapter to go between the GOKb OAI service, for example the one at
 *   https://gokbt.gbv.de/gokb/oai/index/packages?verb=ListRecords&metadataPrefix=gokb
 * and our internal KBCache implementation.
 */

@Slf4j
@CompileStatic
public class GOKbOAIAdapter extends WebSourceAdapter implements KBCacheUpdater, DataBinder {
  private final SimpleDateFormat ISO_DATE = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
  
  private static final String PATH_PACKAGES = '/packages'
  private static final String PATH_TITLES = '/titles'
  
  public void freshenPackageData(final String source_name,
                                 final String base_url,
                                 final String current_cursor,
                                 final KBCache cache,
                                 final boolean trustedSourceTI = false) {

    final String packagesUrl = "${stripTrailingSlash(base_url)}${PATH_PACKAGES}"
                                 
    log.debug("GOKbOAIAdapter::freshenPackageData - fetching from URI: ${packagesUrl}")

    def query_params = [
        'verb': 'ListRecords',
        'metadataPrefix': 'gokb'
    ]

   String cursor = null
    def found_records = true

    if ( current_cursor != null ) {
      cursor = current_cursor
      query_params.from=cursor
    }
    else {
      cursor = ''
    }
    GPathResult xml
    while ( found_records ) {

      log.info("OAI/HTTP GET url=${packagesUrl} params=${query_params}")

      // Built in parser for XML returns GPathResult
      xml = (GPathResult) getSync(packagesUrl, query_params) {

        response.failure { FromServer fromServer ->
          log.error "HTTP/OAI Request failed with status ${fromServer.statusCode}"
          found_records = false
        }
      }
      
      if (found_records) {
      
        log.debug("got page of data from OAI, cursor=${cursor}, ...")
        
        Map page_result = processPackagePage(cursor, xml, source_name, cache, trustedSourceTI)
  
        log.debug("processPackagePage returned, processed ${page_result.count} packages, cursor will be ${page_result.new_cursor}")
        
        // Extract some info from the page.
        final String new_cursor = page_result.new_cursor as String
        final int result_count = (page_result.count ?: 0) as int
        
        // Store the cursor so we know where we are up to.
        cache.updateCursor(source_name, new_cursor)
  
        if ( result_count > 0 ) {
          // If we processed records, and we have a resumption token, carry on.
          if ( page_result.resumptionToken ) {
            query_params.resumptionToken = page_result.resumptionToken
            /** / found_records = false /**/
          }
          else {
            // Reached the end of the data
            found_records = false
          }
        }
        else {
          found_records = false
        }
      }
  
      log.debug("GOKbOAIAdapter::freshenPackageData - exiting URI: ${base_url} with cursor \"${cursor}\" resumption \"${query_params?.resumptionToken}\"")
    }
  }
  // TODO Potentially can combine freshenTitleData and freshenPackageData with a new variable "dataType" or something like that.
  public void freshenTitleData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache,
                                 boolean trustedSourceTI = false) {
    
    final String titlesUrl = "${stripTrailingSlash(base_url)}${PATH_TITLES}"
                                 
    log.debug("GOKbOAIAdapter::freshenTitleData - fetching from URI: ${titlesUrl}")

    def query_params = [
        'verb': 'ListRecords',
        'metadataPrefix': 'gokb'
    ]

    String cursor = null
    def found_records = true

    if ( current_cursor != null ) {
      cursor = current_cursor
      query_params.from=cursor
    }
    else {
      cursor = ''
    }
    GPathResult xml
    while ( found_records ) {

      log.debug("** GET ${titlesUrl} ${query_params}")

      // Built in parser for XML returns GPathResult
      xml = (GPathResult) getSync(titlesUrl, query_params) {

        response.failure { FromServer fromServer ->
          log.error "Request failed with status ${fromServer.statusCode}"
          found_records = false
        }
      }
      
      if (found_records) {
      
        log.debug("got page of data from OAI, cursor=${cursor}, ...")
        
        Map page_result = processTitlePage(cursor, xml, source_name, cache, trustedSourceTI)
  
        log.debug("processTitlePage returned, processed ${page_result.count} titles, cursor will be ${page_result.new_cursor}")
        
        // Extract some info from the page.
        final String new_cursor = page_result.new_cursor as String
        final int result_count = (page_result.count ?: 0) as int
        
        // Store the cursor so we know where we are up to.
        cache.updateCursor(source_name, new_cursor)
  
        if ( result_count > 0 ) {
          // If we processed records, and we have a resumption token, carry on.
          if ( page_result.resumptionToken ) {
            query_params.resumptionToken = page_result.resumptionToken
            /** / found_records = false /**/
          }
          else {
            // Reached the end of the data
            found_records = false
          }
        }
        else {
          found_records = false
        }
      }
  
      log.debug("GOKbOAIAdapter::freshenTitleData - exiting URI: ${base_url} with cursor ${cursor}")
    }
  }

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache) {
    throw new RuntimeException("Holdings data not suported by GOKb")
  }

  @CompileStatic(SKIP)
  private Map processPackagePage(String cursor, GPathResult oai_page, String source_name, KBCache cache, boolean trustedSourceTI) {

    final SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    // Force the formatter to use UCT because we want "Z" as the timezone
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))

    def result = [:]


    // If there is no cursor, initialise it to an empty string.
    result.new_cursor = (cursor && cursor.trim()) != '' ? cursor : ''
    result.count = 0

    log.debug("GOKbOAIAdapter::processPackagePage(${cursor},...")

    // Remove the ThreadLocal<Set> containing ids of TIs enriched by this process.
    TitleEnricherService.enrichedIds.remove()

    oai_page.ListRecords.record.each { record ->
      result.count++
      def record_identifier = record?.header?.identifier?.text()
      def package_name = record?.metadata?.gokb?.package?.name?.text()
      def datestamp = record?.header?.datestamp?.text()
      def editStatus = record?.metadata?.gokb?.package?.editStatus?.text()
      def listStatus = record?.metadata?.gokb?.package?.listStatus?.text()
      def packageStatus = record?.metadata?.gokb?.package?.status?.text()
      def package_shortcode = record?.metadata?.gokb?.package?.shortcode?.text()

      log.debug("Processing OAI record :: ${result.count} ${record_identifier} ${package_name}")

      if ( packageStatus == 'deleted' ) {
        // ToDo: Decide what to do about deleted records
      }
      else {
        if (!package_name) {
          log.info("Ignoring Package '${record_identifier}' because package_name is missing")
        } else if (!package_shortcode) {
          log.info("Ignoring Package '${record_identifier}' because package_shortcode is missing")
        } else if (editStatus.toLowerCase() == 'rejected') {
          log.info("Ignoring Package '${package_name}' because editStatus=='${editStatus}'")
        } else if (listStatus.toLowerCase() != 'checked') {
          log.info("Ignoring Package '${package_name}' because listStatus=='${listStatus}' (required: 'checked')")
        } else {
          PackageSchema json_package_description = gokbToERM(record, trustedSourceTI)
          cache.onPackageChange(source_name, json_package_description)
        }
      }
      
      if ( datestamp > result.new_cursor ) {
        log.debug("Datestamp from record \"${datestamp}\" larger than current cursor (\"${result.new_cursor}\") - update it")
        // Because OAI uses >= we want to nudge up the cursor timestamp by 1s (2019-02-06T11:19:20Z)
        Date parsed_datestamp = parseDate(datestamp) // sdf.parse(datestamp)
        long incremented_datestamp = parsed_datestamp.getTime()+1000
        String new_string_datestamp = sdf.format(new Date(incremented_datestamp))

        log.debug("New cursor value - \"${datestamp}\" > \"${result.new_cursor}\" - updating as \"${new_string_datestamp}\"")
        result.new_cursor = new_string_datestamp
        log.debug("result.new_cursor=${result.new_cursor}")
      }
    }

    result.resumptionToken = oai_page.ListRecords?.resumptionToken?.text()
    return result
  }

  @CompileStatic(SKIP)
  private Map processTitlePage(String cursor, GPathResult oai_page, String source_name, KBCache cache, boolean trustedSourceTI) {

    final SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    // Force the formatter to use UCT because we want "Z" as the timezone
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))

    def result = [:]


    // If there is no cursor, initialise it to an empty string.
    result.new_cursor = (cursor && cursor.trim()) != '' ? cursor : ''
    result.count = 0

    log.debug("GOKbOAIAdapter::processPackagePage(${cursor},...")

    oai_page.ListRecords.record.each { record ->
      result.count++
      def datestamp = record?.header?.datestamp?.text()

      def record_identifier = record?.header?.identifier?.text()
      def title_name = record?.metadata?.gokb?.title?.name?.text()
      // TODO ERM-1801 might have to pass trustedSourceTI here eventually
      log.debug("Processing OAI record :: ${result.count} ${record_identifier} ${title_name}")
      
      ContentItemSchema json_title_description = gokbToERMTitle(record)
      cache.onTitleChange(source_name, json_title_description)
      
      if ( datestamp > result.new_cursor ) {
        log.debug("Datestamp from record \"${datestamp}\" larger than current cursor (\"${result.new_cursor}\") - update it")
        // Because OAI uses >= we want to nudge up the cursor timestamp by 1s (2019-02-06T11:19:20Z)
        Date parsed_datestamp = parseDate(datestamp) // sdf.parse(datestamp)
        long incremented_datestamp = parsed_datestamp.getTime()+1000
        String new_string_datestamp = sdf.format(new Date(incremented_datestamp))

        log.debug("New cursor value - \"${datestamp}\" > \"${result.new_cursor}\" - updating as \"${new_string_datestamp}\"")
        result.new_cursor = new_string_datestamp
        log.debug("result.new_cursor=${result.new_cursor}")
      }
    }

    result.resumptionToken = oai_page.ListRecords?.resumptionToken?.text()
    return result
  }

  /**
   * convert the gokb package metadataPrefix into our canonical ERM json structure as seen at
   *   https://github.com/folio-org/mod-erm/blob/master/service/src/integration-test/resources/packages/apa_1062.json
   * the GOKb records look like this
   *   https://gokbt.gbv.de/gokb/oai/index/packages?verb=ListRecords&metadataPrefix=gokb
   */
  @CompileStatic(SKIP)
  private InternalPackageImpl gokbToERM(GPathResult xml_gokb_record, boolean trustedSourceTI) {

    def package_record = xml_gokb_record?.metadata?.gokb?.package

    def result = null

    if ( ( package_record != null ) && ( package_record.name != null ) ) {

      def package_name = package_record.name?.text()
      def package_shortcode = package_record.shortcode?.text()
      def nominal_provider = package_record.nominalProvider?.name?.text()
      def package_status = package_record.status?.text()
      def source_data_created = package_record.dateCreated?.text()
      def source_data_updated = package_record.lastUpdated?.text()
      def availability_scope = package_record.global?.text()

      def identifiers = package_record.identifiers?.identifier?.findAll {
        it.@type?.text() == null || it.@type?.text()?.trim() == ''
      }?.collect {
        [
          namespace: it.@namespaceName?.text()?.toLowerCase()?.replaceAll(/\s+/, "_"),
          value: it.@value?.text()
        ]
      }

      result = [
        header:[
          status: package_status,
          availability:[
            type: 'general'
          ],
          packageProvider:[
            name:nominal_provider
          ],
          packageSource:'GOKb',
          packageName: package_name,
          trustedSourceTI: trustedSourceTI,
          packageSlug: package_shortcode,
          sourceDataCreated: source_data_created,
          sourceDataUpdated: source_data_updated,
          availabilityScope: availability_scope
        ],
        identifiers: identifiers,
        packageContents: []
      ]

      package_record.TIPPs?.TIPP.each { tipp_entry ->
        def tipp_status = tipp_entry?.status?.text()

        // log.info("Tipp.title is of size ${tipp_entry?.title?.name?.size()} and tipp_entry?.title?.name is ${tipp_entry?.title?.name}");

        // Skip delete tipps, and skip tipps where no title has been properly idenitified yet for the KBart line
        if ( ( tipp_status != 'Deleted' ) && ( tipp_entry?.title?.name?.size() > 0 ) ) {

          def tipp_id = tipp_entry?.@id?.toString()
          def tipp_medium = tipp_entry?.medium?.text()

          def tipp_coverage = [] // [ "startVolume": "8", "startIssue": "1", "startDate": "1982-01-01", "endVolume": null, "endIssue": null, "endDate": null ],

          // Coverage node exists
          if (tipp_entry.coverage.size()) {
            // Our domain model does not allow null startDate or endDate
            String start_date_string = tipp_entry.coverage?.@startDate?.toString()
            String end_date_string = tipp_entry.coverage?.@endDate?.toString()

            tipp_coverage.add(["startVolume": tipp_entry.coverage?.@startVolume?.toString(),
              "startIssue": tipp_entry.coverage?.@startIssue?.toString(),
              "startDate": start_date_string?.length() > 0 ? start_date_string : null,
              "endVolume":tipp_entry.coverage?.@endVolume?.toString(),
              "endIssue": tipp_entry.coverage?.@endIssue?.toString(),
              "endDate": end_date_string?.length() > 0 ? end_date_string : null]
            )
          }

          def tipp_coverage_depth = tipp_entry.coverage.@coverageDepth?.toString()
          def tipp_coverage_note = tipp_entry.coverage.@coverageNote?.toString()

          final String embargo = tipp_entry.coverage?.@embargo?.toString()

          def tipp_url = tipp_entry.url?.text()
          def tipp_platform_url = tipp_entry.platform?.primaryUrl?.text()
          def tipp_platform_name = tipp_entry.platform?.name?.text()

          String access_start = tipp_entry.access?.@start?.toString()
          String access_end = tipp_entry.access?.@end?.toString()

          //Retired TIPPs are no longer in the package and should have an access_end, if not then make a guess at it
          if(access_end.length()==0 && tipp_status == "Retired") {
            access_end = tipp_entry.lastUpdated?.text().toString()
            log.info( "accessEnd date guessed for retired title: ${tipp_entry?.title?.name?.text()} in package: ${package_name}. TIPP ID: ${tipp_id}" )
          }

          Map packageContent = parseTitleInformation(tipp_entry?.title, tipp_coverage)

          packageContent << [
            "instanceMedium": tipp_medium,
            "coverage": tipp_coverage,
            "embargo": embargo,
            "coverageDepth": tipp_coverage_depth,
            "coverageNote": tipp_coverage_note,
            "platformUrl": tipp_platform_url,
            "platformName": tipp_platform_name,
            "url": tipp_url,
            "accessStart": access_start,
            "accessEnd": access_end
          ]

          // log.debug("consider tipp ${tipp_title}")

          result.packageContents.add(packageContent)
        }
        else {
          log.warn("Skipping tipp without verified title");
        }
      }
    }
    else {
      throw new RuntimeException("Problem decoding package record: ${package_record}")
    }

    InternalPackageImpl pkg = new InternalPackageImpl()
    BindingResult binding = bindData (pkg, result)
    if (binding?.hasErrors()) {
      binding.allErrors.each { log.debug "\t${it}" }
    }
    pkg
  }

  public Map importPackage(Map params,
                            KBCache cache) {
    throw new RuntimeException("Not yet implemented")
    return null
  }

  public boolean activate(Map params, KBCache cache) {
    throw new RuntimeException("Not supported by this KB provider")
    return false
  }
  
  @CompileStatic(SKIP)
  public Map getTitleInstance(String source_name, String base_url, String goKbIdentifier, String type, String publicationType, String subType) {
    
    Map ti
    
    final String titlesUrl = "${stripTrailingSlash(base_url)}${PATH_TITLES}"
    
    if (type?.toLowerCase() == "monograph") {
      
      log.debug("Making secondary enrichment call for book/monograph title with GOKb identifier: ${goKbIdentifier}")
      
      final def query_params = [
        'verb': 'GetRecord',
        'identifier': goKbIdentifier,
        'metadataPrefix': 'gokb'
      ]
      
      log.debug("** GET ${titlesUrl} ${query_params}")

      log.debug("GOKbOAIAdapter::getTitleInstance - fetching from URI: ${titlesUrl}")
      boolean valid = true
      GPathResult xml = (GPathResult) getSync(titlesUrl, query_params) {
        
        response.failure { FromServer fromServer ->
          log.error "Request failed with status ${fromServer.statusCode}"
          valid = false
        }
      }
      
      if (valid) {
        
        ti = gokbToERMSecondary(xml.GetRecord.record, subType)
      }
    } else {
      log.debug("No secondary enrichment call needed for publicationType: ${publicationType}")
    }
    
    ti
  }
  
  // This method allows us to grab extra information from a title ingest stream
  // that isn't available on the package ingest stream
  @CompileStatic(SKIP)
  private Map gokbToERMSecondary(GPathResult xml_gokb_record, String subType) {
    /* We take in the subType here as we may need to do different things
     * with the data depending on whether it refers to an electronic/print TI
    */
    Map ermTitle = [:]
    def title_record = xml_gokb_record?.metadata?.gokb?.title

    ermTitle.monographEdition = title_record?.editionStatement.toString()
    ermTitle.monographVolume = title_record?.volumeNumber.toString()
    ermTitle.dateMonographPublished = subType.toLowerCase() == "electronic" ?
      title_record?.dateFirstOnline.toString() :
      title_record?.dateFirstInPrint.toString()

    if (ermTitle.dateMonographPublished) {
      // Incoming date information has time we need to strip out
      ermTitle.dateMonographPublished = ermTitle.dateMonographPublished.replace(" 00:00:00.0", "")
    }
    ermTitle.firstAuthor = title_record?.firstAuthor.toString()
    ermTitle.firstEditor = title_record?.firstEditor.toString()

    return ermTitle;
  }

  // A method to convert the incoming GoKB record to something our ERM software knows the shape of 
  @CompileStatic(SKIP)
  private PackageContentImpl gokbToERMTitle(GPathResult xml_gokb_record) {
    def title_record = xml_gokb_record?.metadata?.gokb?.title

    def result = null

    if (title_record != null && title_record.name != null) {
      result = parseTitleInformation(title_record)
      result.instanceMedium = 'Electronic'
    }

    PackageContentImpl title = new PackageContentImpl()
    BindingResult binding = bindData (title, result)
    if (binding?.hasErrors()) {
      binding.allErrors.each { log.debug "\t${it}" }
    }

    // log.info("gokbToERMTitle returning ${title}");
    
    title
  }


  /* A unified method to parse a GPathResult title and return
   * some of the necessary fields for the ingest. This method is used both
   * by gokbToERM AND gokbToERMTitle
   */
  @CompileStatic(SKIP)
  // Include tipp_coverage information for media logic
  private Map parseTitleInformation(GPathResult title, def coverage = null) {
    def titleText = title?.name?.text()
    def media = null

    List instance_identifiers = [] // [ "namespace": "issn", "value": "0278-7393" ]
    List sibling_identifiers = []

    // If we're processing an electronic record then issn is a sibling identifier
    // Ensure issn, pissn, pisbn end up in siblingInstanceIdentifiers
    title.identifiers.identifier.each { ti_id ->
      switch(ti_id.@namespace) {
        case 'issn':
        case 'pissn':
          sibling_identifiers.add(["namespace": "issn", "value": ti_id.@value?.toString() ])
          break;
        case 'pisbn':
          sibling_identifiers.add(["namespace": "isbn", "value": ti_id.@value?.toString() ])
          break;
        default:
          instance_identifiers.add(["namespace": ti_id.@namespace?.toString(), "value": ti_id.@value?.toString() ])
          break;
      }
    }

    // It appears that tipp_entry?.title?.type?.value() can be a list
    String title_pub_type = title?.type?.text()
    // Turns JournalInstance into journal, BookInstance into book, DatabaseInstance into database
    String pub_media = title_pub_type.replace('Instance', '')

    switch ( title_pub_type ) {
      case 'JournalInstance':
        media = 'serial'
        break
      case 'BookInstance':
        media = 'monograph'
        break
      default:
        if ( coverage ) {
          media = 'serial'
        } else {
          media = 'monograph'
        }
        break
    }

    def source_identifier = title?.@uuid?.toString()

    return ([
      "title": titleText,
      "instanceMedia": media,
      "instancePublicationMedia": pub_media,
      "sourceIdentifier": source_identifier,
      "instanceIdentifiers": instance_identifiers,
      "siblingInstanceIdentifiers": sibling_identifiers
    ])
  }

  public boolean requiresSecondaryEnrichmentCall() {
    true
  }

  public String makePackageReference(Map params) {
    throw new RuntimeException("Not yet implemented")
//    return null
  }

  // Move date parsing here - we might want to do something more sophistocated with different fallback formats
  // here in the future.
  Date parseDate(String s) {
    ISO_DATE.parse(s)
  }
}
