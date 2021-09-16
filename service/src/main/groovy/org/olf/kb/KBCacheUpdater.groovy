package org.olf.kb;

import java.util.Map;

/**
 * KBCacheUpdater. Adapter pattern - allowing this module to aggregate KB data from multiple external sources.
 *
 * The domain model of this app can be thought of as a local cache of multiple external knowledgebases (KBs)
 * the term KB is problematic, as it can refer in turn to
 *     The "KB" of everything thats been published, by whom and when, including title histories and other info (Publications KB?)
 *     The "KB" of how publications are packaged and bundled for sale by content providers (Packaging KB?)
 *     The "KB" of what I think I have bought (What titles, coverage, terms), from where and for how long (PCA)
 *
 * This interface defines interface which a class must implement in order to collect information from a remote KB
 * and update the local KB Cache.
 *
 * The initial focus will be on the "Packaging KB" - which implies a degree of "Publications KB" functionality.
 *
 * It is anticipated that implementations of this adapter will exist at least for
 *
 *    * A Mock source for tests
 *    * the GOKb system
 *    * EBSCO KB
 *    * Perhaps KB+ as it's a useful test source for k-int
 *
 * Implementors of this interface hide the complexity of source knowledgebase systems by getting all package changes
 * since the last check, converting those packages into the canonical package format as defined at
 * https://docs.google.com/document/d/14KIi4Guhu8r1NM7lr8NH6SI7giyAdmFjjy4Q6x-MMvQ/edit
 * and then calling KBCache.onPackageChange or KBCache.onPackageRemoved.
 *
 * The only responsibility of implementors is to understand the remote system and create a stream of package data
 * in the canonical format.
 */
public interface KBCacheUpdater {

  /**
   * freshen the cache - Make calls to the KBCache based on DELTA information about what has changed
   * in the remote KB.
   *
   * @param source_id the ID of a org.olf.kb.RemoteKB that is to be used for an update. Different implementations
   *                  will serialise different kinds of cursor back to the implementation to track their own state.
   * @param cursor A Map containing implementation specific data that allows the updater to "Know where it is"
   * @param cache The targer for the update
   * @Return updated Cursor object representing the state of the cursor for the next pass
   *
   */
  public void freshenPackageData(String source_name, 
                                   String base_uri, 
                                   String cursor, 
                                   KBCache cache,
                                   boolean trustedSourceTI);


  public void freshenTitleData(String source_name,
                               String base_uri,
                               String cursor, 
                               KBCache cache,
                               boolean trustedSourceTI);

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache);

  public String makePackageReference(Map params);

  /**
   * using a native package identifier, request a specific package from the remote source and add it to the KB Cache.
   * If the package already exists, implementors MAY update the existing package with the new information.
   */
  public Map importPackage(Map params, KBCache cache);
                                
 
  /**
   * Ask the remote service to activate a selected content item - usually using some combination of provider, platform, title ID
   * @return true if the content was successfully activated false otherwise
   */
  public boolean activate(Map params, KBCache cache);

  /**
   * Ask the remote service to get more information about a specific titleInstance
   */
  public Map getTitleInstance(String source_name,
                              String base_url,
                              String identifier,
                              String type,
                              String publicationType,
                              String subType);
  
  public boolean requiresSecondaryEnrichmentCall();
}
