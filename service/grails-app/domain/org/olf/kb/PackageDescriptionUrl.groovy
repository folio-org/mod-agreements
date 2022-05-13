package org.olf.kb;

import grails.gorm.MultiTenant

public class PackageDescriptionUrl implements MultiTenant<PackageDescriptionUrl> {

	String id
  String url

	static belongsTo = [ owner: Pkg ]

	  static mapping = {
      // table 'package_description_url'
                        id column: 'pdu_id', generator: 'uuid2', length:36
                   version column: 'pdu_version'
                       url column: 'pdu_url'
                     owner column: 'pdu_owner_fk'  
	}

	static constraints = {
		 owner(nullable:false, blank:false);
	     url(nullable:false, blank:false);
	}

}
