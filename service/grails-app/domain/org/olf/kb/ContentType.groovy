package org.olf.kb;

import grails.gorm.MultiTenant
import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

public class ContentType implements MultiTenant<ContentType> {

	String id
	@Defaults(['Serials', 'Monographs', 'Databases'])
  RefdataValue contentType

	static belongsTo = [ owner: Pkg ]

	  static mapping = {
      // table 'content_type'
                        id column: 'ct_id', generator: 'uuid2', length:36
                   version column: 'ct_version'
               contentType column: 'ct_content_type_fk'
                     owner column: 'ct_owner_fk'  
	}

	static constraints = {
		 owner(nullable:false, blank:false);
	   contentType(nullable:false, blank:false);
	}

}
