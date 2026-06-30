package org.olf.kb.adapters

import org.olf.dataimport.internal.InternalPackageImplWithPackageContents
import org.olf.dataimport.internal.PackageSchema
import org.olf.kb.GoKbClient
import org.olf.kb.GoKbClientException
import org.olf.kb.KBCache;
import org.olf.kb.KBCacheUpdater;
import org.springframework.validation.BindingResult

import grails.web.databinding.DataBinder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
public class KIJPFAdapter implements KBCacheUpdater, DataBinder {

  GoKbClient goKbClient;

  KIJPFAdapter() {
   goKbClient = new GoKbClient()
  }

  public void freshenPackageData(final String source_name,
                                 final String base_url,
                                 final String current_cursor,
                                 final KBCache cache,
                                 final boolean trustedSourceTI = false) {

    log.debug ("KIJPFAdapter::freshen - fetching from URI: ${base_url}")

    def query_params = [
        'max': '10',
        'format': 'json',
        'order':'lastUpdated'
    ]

    String cursor = ''
    if ( cursor != null ) {
      cursor = current_cursor
      query_params.lastUpdatedAfter = cursor
    }

    boolean cont = true
    int spin_protection = 0

    while ( cont ) {
      log.debug "Process page of data start date = ${query_params.startDate}, spin_protection=${spin_protection} - params ${query_params}"

      spin_protection++
      boolean valid = true
      Map<String, ?> jsonMap
      try {
        jsonMap = (Map)  goKbClient.getPackageData(base_url, query_params as Map<String, Object>)
      } catch (GoKbClientException exception) {
        log.error "Request failed with message: ${exception.message} and status code: ${exception.responseStatusCode}"
        valid = false
      }
 
      if (valid) {
        final Map page_result = processPage(cursor, jsonMap, source_name, cache)
        log.debug "processPage returned, processed ${page_result.count} packages"
        
        final String new_cursor = page_result.new_cursor as String
        cache.updateCursor(source_name, new_cursor)

        if ( ( page_result.count == 0 ) || ( spin_protection > 25 ) ) {
          cont = false;
        }
        else {
          log.debug("Fetch next page of data - ${page_result.new_cursor}");
          query_params.lastUpdatedAfter=page_result.new_cursor;
        }
      }      
    }
  }

  public void freshenTitleData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache,
                                 boolean trustedSourceTI = false) {
    throw new RuntimeException("Not supported by this KB provider");
  }


  private Map processPage(String cursor, Map<String, ?> package_list, String source_name, KBCache cache) {
    def result = [:]
    result.new_cursor = cursor;
    result.count = 0;
    package_list.packages.each { Map<String, ?> pkg ->
      (result.count as int)++;
      
      log.debug "${result.count}"
      log.debug "${pkg.name}"
      log.debug "${pkg.packageContentAsJson}"

      processPackage(pkg.packageContentAsJson as String, source_name, cache);

      final String lupdt = pkg.lastUpdated as String
      
      if ( lupdt > cursor ) {
        log.debug ("New cursor value - ${pkg.lastUpdated} > ${result.new_cursor} ");
        result.new_cursor = pkg.lastUpdated;
      }
    }
    return result;
  }

  private void processPackage(String url, String source_name, KBCache cache) {
    log.debug ("processPackage(${url},${source_name}) -- fetching");
    try {
      boolean valid = true
      Map<String, ?> jsonMap
      try {
        jsonMap = (Map)  goKbClient.getPackageData(url)
      } catch (GoKbClientException exception) {
        log.error "Request failed with message: ${exception.message} and status code: ${exception.responseStatusCode}"
        valid = false
      }


      if (valid) {
        PackageSchema json_package_description = kbplusToERM(jsonMap)
        cache.onPackageChange(source_name, json_package_description)
      }
    }
    catch ( Exception e ) {
      log.error "Unexpected error processing package ${url}"
      throw e
    }
  }

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache) {
    throw new RuntimeException("Not yet implemented");
  }

  public Map importPackage(Map params,
                            KBCache cache) {
    throw new RuntimeException("Not yet implemented");
    return null;
  }

  public boolean activate(Map params, KBCache cache) {
    throw new RuntimeException("Not supported by this KB provider");
    return false;
  }

  public String makePackageReference(Map params) {
    throw new RuntimeException("Not yet implemented");
    return null;
  }

  public boolean requiresSecondaryEnrichmentCall() {
    false
  }

  public Map getTitleInstance(String source_name,
                              String base_url,
                              String identifier,
                              String type,
                              String publicationType,
                              String subType) {
    throw new RuntimeException("Not supported by this KB provider");
  }


  private PackageSchema kbplusToERM(Map<String,?> m) {
    InternalPackageImplWithPackageContents pkg = new InternalPackageImplWithPackageContents()
    BindingResult binding = bindData (pkg, m)
    if (binding?.hasErrors()) {
      binding.allErrors.each { log.debug "\t${it}" }
    }
    pkg
  }
}
