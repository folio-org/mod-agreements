package org.olf.kb

import grails.gorm.MultiTenant

/*
 * This table acts as a store for a key/value pair of additional information we can store on a level of a resource
 * (PTI/PCI) which we can later use to aid us in the matching process
 */
public class MatchKey implements MultiTenant<MatchKey> {

  String id
  String key
  String value
  ErmResource resource

  static mapping = {
           id column: 'mk_id', generator: 'uuid2', length:36
      version column: 'mk_version'
          key column: 'mk_key'
        value column: 'mk_value'
     resource column: 'mk_resource_fk'
  }

  static constraints = {
      key(nullable:false, blank:false)
    value(nullable:true, blank:false)
  }
}
