package org.olf.kb;

import grails.gorm.MultiTenant

public class ContentType implements MultiTenant<ContentType> {

	String id
  String contentType

	static belongsTo = [ owner: Pkg ]

	  static mapping = {
      // table 'content_type'
                        id column: 'ct_id', generator: 'uuid2', length:36
                   version column: 'ct_version'
               contentType column: 'ct_content_type'
                     owner column: 'ct_owner_fk'  
	}

	static constraints = {
		 owner(nullable:false, blank:false);
	   contentType(nullable:false, blank:false);
	}

}
