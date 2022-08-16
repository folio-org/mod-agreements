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
 * An adapter to go between the GOKb TIPP stream service, for example the one at
 *   https://gokbt.gbv.de/gokb/oai/index/packages?verb=ListRecords&metadataPrefix=gokb
 * and our internal KBCache implementation.
 *
 * TIPP feed: https://gokbt.gbv.de/gokb/api/scroll?component_type=TitleInstancePackagePlatform&changedSince=2022-06-25+00:00:00
 */

@Slf4j
@CompileStatic
public class GOKbTippAdapter extends WebSourceAdapter implements KBCacheUpdater, DataBinder {

  private final SimpleDateFormat ISO_DATE = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
  
  public void freshenPackageData(final String source_name,
                                 final String base_url,
                                 final String current_cursor,
                                 final KBCache cache,
                                 final boolean trustedSourceTI = false) {

  }

  public void freshenTitleData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache,
                                 boolean trustedSourceTI = false) {
  }

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache) {
    throw new RuntimeException("Holdings data not suported by GOKb")
  }

  @CompileStatic(SKIP)
  public Map getTitleInstance(String source_name, String base_url, String goKbIdentifier, String type, String publicationType, String subType) {
    throw new RuntimeException('not implemented');
  }
  
  public boolean requiresSecondaryEnrichmentCall() {
    false
  }

  public String makePackageReference(Map params) {
    throw new RuntimeException("Not yet implemented")
  }

  // Move date parsing here - we might want to do something more sophistocated with different fallback formats
  // here in the future.
  Date parseDate(String s) {
    ISO_DATE.parse(s)
  }
}
