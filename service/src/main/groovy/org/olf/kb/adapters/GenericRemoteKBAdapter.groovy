package org.olf.kb.adapters

import org.olf.kb.KBCache
import org.olf.kb.KBCacheUpdater

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
public class GenericRemoteKBAdapter implements KBCacheUpdater {


  public void freshenPackageData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache,
                                 boolean trustedSourceTI = false) {
    throw new RuntimeException("Not supported by this KB provider")
  }

  public void freshenTitleData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache,
                                 boolean trustedSourceTI = false) {
    throw new RuntimeException("Not supported by this KB provider");
  }

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache) {
    throw new RuntimeException("Not yet implemented")
  }

  public String makePackageReference(Map params) {
    throw new RuntimeException("Not yet implemented")
  }

  /**
   *
   */
  public Map importPackage(Map params,
                            KBCache cache) {
    throw new RuntimeException("Not yet implemented")
  }

  public boolean activate(Map params, KBCache cache) {
    // Nothing to see here yet
    return true
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
    throw new RuntimeException("Not supported by this KB provider")
  }

}


