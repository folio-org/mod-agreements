package org.olf.kb.adapters;

import org.olf.kb.KBCacheUpdater;
import org.olf.kb.RemoteKB;
import org.olf.kb.KBCache;
import groovy.json.JsonSlurper;
import java.util.Map;

import static groovyx.net.http.ContentType.URLENC
import static groovyx.net.http.ContentType.XML
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import org.apache.http.*
import org.apache.http.protocol.*
import java.text.SimpleDateFormat
import java.nio.charset.Charset
import static groovy.json.JsonOutput.*
import java.text.*
import groovy.util.logging.Slf4j

/**
 * Get and Put KB records from the EBSCO API. Documentation can be found:
 *    https://developer.ebsco.com/gettingstarted
 *    https://developer.ebsco.com/docs
 * 
 */

@Slf4j
public class EbscoKBAdapter implements KBCacheUpdater {


  public void freshenPackageData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache) {
    throw new RuntimeException("Not supported by this KB provider");
  }

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache) {
    throw new RuntimeException("Not yet implemented");
  }

  public String makePackageReference(Map params) {
    return "EKB:${params.vendorid}:${params.packageid}".toString()
  }

  /**
   * using a native package identifier, request a specific package from the remote source and add it to the KB Cache.
   * If the package already exists, implementors MAY update the existing package with the new information.
   */
  public Map importPackage(Map params,
                            KBCache cache) {

    def erm_package = buildErmPackage(params.vendorid,
                                      params.packageid,
                                      params.principal,
                                      params.credentials,
                                      makePackageReference(params))

    return cache.onPackageChange(params.kb, erm_package);
  }

  /**
   * Use the EBSCO api and paginate the list of titles into our internal canonical package format, 
   * @param params - A map containing vendorid and packageid
   * @return the canonicalpackage definition.
   */
  private Map buildErmPackage(String vendorid, String packageid, String principal, String credentials, String package_reference) {

    log.debug("buildErmPackage(${vendorid},${packageid},${principal},${credentials})");

    def result = null;

    if ( ( vendorid == null  ) ||
         ( packageid == null ) ) 
      throw new RuntimeException("buildErmPackage requires vendorid and packageid parameters");

    result = [
      header:[
        availability:[
          type: 'general'
        ],
        packageProvider:[
          name:''
        ],
        packageSource:'EBSCO',
        packageName: '',
        packageSlug: ''
      ],
      packageContents: []
    ]

    // See https://developer.ebsco.com/docs#/
    def ebsco_api = new HTTPBuilder('https://sandbox.ebsco.io');


    // Get package header
    ebsco_api.request(Method.GET) { req ->
      headers.'x-api-key' = credentials
      uri.path="/rm/rmaccounts/${principal}/vendors/${vendorid}/packages/${packageid}"
      response.success = { resp, json ->
        log.debug("Package header: ${json}");
        result.header.reference = package_reference;
        result.header.packageSlug = package_reference;
        result.header.packageName = json.packageName
        result.header.packageProvider.name = json.vendorName
        result.header.packageProvider.reference = json.vendorId
        // {"isCustom":false,"titleCount":1262,"isSelected":false,"visibilityData":{"isHidden":false,"reason":""},"selectedCount":0,"isTokenNeeded":true,
        // "contentType":"AggregatedFullText","customCoverage":{"beginCoverage":"","endCoverage":""},"proxy":{"id":"<n>","inherited":true},
        // "allowEbscoToAddTitles":false,"packageToken":null,"packageType":"Complete"}
      }
    }


    int pageno = 1;

    def query_params = [
        'search': '',
        'count': '100',
        'searchField': 'titlename',
        'orderBy': 'titlename'
    ]

    def found_records = true;

    // Ebsco API uses pagination in blocks of count, offset by page. offset is PAGE offset, not record number, so count 100, offset 3 = records from 301-400
    // Last page will return 0
    while ( found_records ) {

      log.debug("Fetch page ${pageno} - records ${((pageno-1)*100)+1} to ${pageno*100} -- ${query_params}");
      query_params.offset = pageno;

      ebsco_api.request(Method.GET) { req ->
        // headers.Accept = 'application/json'
        headers.'x-api-key' = credentials
        uri.path="/rm/rmaccounts/${principal}/vendors/${vendorid}/packages/${packageid}/titles"
        uri.query=query_params

        response.success = { resp, json ->
          if ( json.titles.size() == 0 ) {
            found_records = false;
          }
          else {
            println("Process ${json.titles.size()} titles (total=${json.totalResults})");
            // println("First title: ${json.titles[0].titleId} ${json.titles[0].titleName}");
            // json.titles[0].identifiersList.each { id ->
            //   println("  -> id ${id.id} (${id.source} ${id.type} ${id.subtype})");
            // }

            json.titles.each { title ->

              String tipp_media = title.pubType?.toLowerCase()
              String tipp_medium = 'electronic';


              def instance_identifiers = [];
              title.identifiersList.each { id ->
                switch ( id.type ) {
                  case 0: //ISSN
                    switch( id.subtype ) {
                      case 0:
                        break;
                      case 1: // PRINT
                        instance_identifiers.add([namespace:'issn',value:id.id])
                        break;
                      case 2: // ONLINE
                        instance_identifiers.add([namespace:'eissn',value:id.id])
                        break;
                      case 7: // INVALID
                        break;
                    }
                    break;
                  case 1: //ISBN
                    instance_identifiers.add([namespace:'isbn',value:id.id])
                    break;
                  case 6: //ZDBID
                    instance_identifiers.add([namespace:'zdb',value:id.id])
                    break;
                  default:
                    break;
                }
              }

              def titleRecord = title.customerResourcesList.find { it.packageId.toString().equals(packageid.trim()) }

              if ( titleRecord ) {
                // log.debug("titleRecord located in customerResourceList -- ${titleRecord} ${titleRecord.url}");
              }
              else {
                log.debug("Unable to find ${packageid} amongst ${title.customerResourcesList.collect{ it.packageId}}");
              }

              String tipp_url = titleRecord?.url;
              String tipp_platform_url = null; // Platform URL is the URL of the platform provider - not the title. Will be derived from tipp url if not set
              def tipp_coverage = []

              titleRecord?.managedCoverageList?.each { ci ->
                tipp_coverage.add(
                  "startVolume": null,
                  "startIssue": null,
                  "startDate": ci.beginCoverage,
                  "endVolume": null,
                  "endIssue": null,
                  "endDate": ci.endCoverage
                )
              }

              if ( ( tipp_url != null ) && ( tipp_url.trim().length() > 0 ) ) {
                result.packageContents.add([
                  "title": title.titleName,
                  "instanceMedium": tipp_medium,
                  "instanceMedia": tipp_media,
                  "instanceIdentifiers": instance_identifiers,
                  // "siblingInstanceIdentifiers": tipp_sibling_identifiers,
                  "coverage": tipp_coverage,
                  // "embargo": null,
                  // "coverageDepth": tipp_coverage_depth,
                  // "coverageNote": tipp_coverage_note,
                  "platformUrl": tipp_platform_url,
                  // "platformName": tipp_platform_name,
                  "url": tipp_url
                ])
              }
              else {
                // entry failed basic QA check - don't add to the package
                log.warn("unable to locate URL for title ${title.titleName} - skipping");
              }
            }

            pageno++;
          }
        }

        response.failure = { resp ->
          log.error("Unexpected error: ${resp.status} : ${resp.statusLine.reasonPhrase}")
          found_records = false;
        }
      }
    }

    return result;
  }

  /**
   * @See https://github.com/folio-org/mod-kb-ebsco/blob/master/app/models/resource.rb
   */
  public boolean activate(Map params, KBCache cache) {
    // Nothing to see here yet
    //
    return true
  }

}

