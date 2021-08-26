package org.olf.kb;

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema


/**
 * KBCache. the interface into the KB cache that adapters will call to notify changes
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
 * These methods are very likely candidates for participation in workflow - or rather the trigger for workflow events
 * We're trying to pre-guess how some of that might work out here.
 *
 * This document: https://docs.google.com/document/d/14KIi4Guhu8r1NM7lr8NH6SI7giyAdmFjjy4Q6x-MMvQ/edit
 * Drive -> ERM-Project Team -> WorkInProgress -> olf-erm analysis and design -> PackageDescription--JSON format
 * sets out some thoughts on the format of JSON documents describing some of this.
  
 */
public interface KBCache {

  /**
   *  An adapter detected a changed package. 

   *  @Param authority
   *  @Param authority_id_of_package
   *  @Param canonical_package_definition - A java object representing a parsed package in the format defined above.
   *
   *  Package header:
   *
   *  "Availability":{
   *  type:"General|Consortia"
   *  },
   *  "Notes":[
   *  "Just because we always end up needing them"
   *  ],
   *  "PackageProvider":"",
   *  "PackageSource":"",
   *  "PackageName":"",
   */
  public Map onPackageChange(String rkb_name, 
                             PackageSchema canonical_package_definition);

  public Map onTitleChange(String rkb_name,
                           ContentItemSchema canonical_title_definition);

  public void updateCursor(String rkb_name, String cursor);

  public void onPackageRemoved(String rkb_name,
                               String authority,
                               String authority_id_of_package);
}
