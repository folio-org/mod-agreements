package org.olf.erm

import grails.gorm.MultiTenant


/**
 * A coverage statement - 
 */
public class CoverageOverride implements MultiTenant<CoverageOverride> {

  String id

  // Mutually exclusive --- ONE of pci, pti or ti
  AgreementLineItem ali

  // MUST Be in format yyyy-mm-dd
  String startDate
  String endDate
  String startVolume
  String startIssue
  String endVolume
  String endIssue

  static mapping = {
                   id column:'co_id', generator: 'uuid', length:36
              version column:'co_version'
                  ali column:'co_pci_fk'
            startDate column:'co_start_date'
              endDate column:'co_end_date'
          startVolume column:'co_start_volume'
           startIssue column:'co_start_issue'
            endVolume column:'co_end_volume'
             endIssue column:'co_end_issue'
  }

  static constraints = {
    ali(nullable:true, blank:false);
    startDate(nullable:true, blank:false);
    endDate(nullable:true, blank:false);
    startVolume(nullable:true, blank:false);
    startIssue(nullable:true, blank:false);
    endVolume(nullable:true, blank:false);
    endIssue(nullable:true, blank:false);
  }


}
