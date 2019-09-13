package org.olf.erm;

import com.k_int.okapi.remote_resources.RemoteOkapiLink
import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.MultiTenant

public class LicenseAmendmentStatus implements MultiTenant<LicenseAmendmentStatus> {
  
  String id
  
  @Defaults(['Current', 'Future', 'Historical', 'Does not apply'])
  RefdataValue status
  String note
  RemoteLicenseLink owner
  
  static belongsTo = [ owner: RemoteLicenseLink ]
  
  static mapping = {
             id column:'las_id', generator: 'uuid2', length:36
         status column:'las_status'
           note column:'las_note', type: 'text'
          owner column:'las_owner'
  }
  
  static constraints = {
       owner  (nullable:false)
       status (nullable:false)
         note (nullable:true, blank:false)
  }
}
