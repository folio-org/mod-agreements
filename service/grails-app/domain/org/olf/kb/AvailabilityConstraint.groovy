package org.olf.kb;

import grails.gorm.MultiTenant
import com.k_int.web.toolkit.refdata.RefdataValue

public class AvailabilityConstraint implements MultiTenant<AvailabilityConstraint> {
	
	String id
	RefdataValue body

	static belongsTo = [ owner: Pkg ]
  
	  static mapping = {
      // table 'alternate_resource_name'
                        id column: 'avc_id', generator: 'uuid2', length:36
                   version column: 'avc_version'
                      body column: 'avc_body_fk'
                     owner column: 'avc_owner_fk'  
	}
  
	static constraints = {
		 owner(nullable:false, blank:false);
	   body(nullable:false, blank:false);
	}
  
}
