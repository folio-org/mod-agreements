package org.olf.kb.adapters;

import org.olf.kb.KBCacheUpdater;
import org.olf.kb.RemoteKB;
import org.olf.kb.KBCache;
import groovy.json.JsonSlurper;
import java.util.Map;


public class KBPlusAdapter implements KBCacheUpdater {


  public void freshenPackageData(String source_id,
                                   String uri,
                                   String cursor,
                                   KBCache cache,
                                   boolean trustedSourceTI = false) {

    // We want this update to happen independently of any other transaction, on it's own, and in the background.
    RemoteKB.withNewTransaction {
      RemoteKB remote_kb_info = RemoteKB.get(source_id)
      def kbplus_cursor_info = null;
      if ( remote_kb_info.cursor != null ) {
        kbplus_cursor_info = new JsonSlurper().parseText(remote_kb_info.cursor)
      }
      else {
        // No cursor - page through everything
      }

      // Package list service uses URLs of the form
      // https://www.kbplus.ac.uk/test2/publicExport/idx?format=json&lastUpdatedAfter=2017-10-14T11:07:00Z&order=lastUpdated&max=10

      remote_kb_info.save(flush:true, failOnError:true);
    }
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

}
