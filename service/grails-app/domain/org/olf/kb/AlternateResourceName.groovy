package org.olf.kb;

import grails.gorm.MultiTenant

public class AlternateResourceName implements MultiTenant<AlternateResourceName> {
	
	String id
  String name
	
	static belongsTo = [ owner: ErmResource ]
  
	  static mapping = {
      // table 'alternate_resource_name'
                        id column: 'arn_id', generator: 'uuid2', length:36
                   version column: 'arn_version'
                      name column: 'arn_name'
                     owner column: 'arn_owner_fk'  
	}
  
	static constraints = {
		 owner(nullable:false, blank:false);
	   name(nullable:false, blank:false);
	}
  
}
