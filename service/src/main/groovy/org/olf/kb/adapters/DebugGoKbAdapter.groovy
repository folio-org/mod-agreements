package org.olf.kb.adapters

import static groovy.transform.TypeCheckingMode.SKIP

import java.text.*

import org.olf.TitleEnricherService
import org.olf.dataimport.internal.InternalPackageImplWithPackageContents
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.kb.KBCache
import org.olf.kb.KBCacheUpdater
import org.springframework.validation.BindingResult

import grails.web.databinding.DataBinder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.xml.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import groovyx.net.http.*

import org.slf4j.MDC

/**
 * A debug adapter to treat some XML file as a GoKB package harvest
 */

@Slf4j
@CompileStatic
public class DebugGoKbAdapter extends DebugGoKbAdapter {
  private static final String XML_FILE_LOCATION = "src/integration-test/resources/DebugGoKbAdapter"

  public void freshenPackageData(final String source_name,
                                 final String base_url,
                                 final String current_cursor,
                                 final KBCache cache,
                                 final boolean trustedSourceTI = false) {

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

    def pageXml = new XmlSlurper().parse(new File("${XML_FILE_LOCATION}/exampleXMLPage.xml"))
    log.debug("LOGDEBUG PAGEXML: ${pageXml}")

    /* long package_sync_start_time = System.currentTimeMillis();

    while ( found_records ) {
      // Clear MDC package source and reference, they'll be set lower in the code
      MDC.remove('packageSource')
      MDC.remove('packageReference')

      log.info("OAI/HTTP GET url=${packagesUrl} params=${query_params} elapsed=${System.currentTimeMillis()-package_sync_start_time}")

      // Built in parser for XML returns GPathResult
      Object sync_result = getSync(packagesUrl, query_params) {
        response.failure { FromServer fromServer ->
          log.error "HTTP/OAI Request failed with status ${fromServer.statusCode}"
          found_records = false
        }
      }

      if ( (found_records) && ( sync_result instanceof GPathResult ) ) {

        xml = (GPathResult) sync_result;
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
      else {
        log.warn("HTTP Get did not return a GPathResult... skipping");
        found_records = false
      }

      log.debug("DebugGoKbAdapter::freshenPackageData - exiting URI: ${base_url} with cursor \"${cursor}\" resumption \"${query_params?.resumptionToken?:'NULL'}\" found=${found_records}")
    } */
  }

  public void freshenTitleData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache,
                                 boolean trustedSourceTI = false) {
    throw new RuntimeException("Title data not suported by DebugGoKbAdapter")
  }

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache) {
    throw new RuntimeException("Holdings data not suported by DebugGoKbAdapter")
  }
}
