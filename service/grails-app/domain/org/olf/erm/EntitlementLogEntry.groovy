package org.olf.erm

import java.time.LocalDate
import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import groovy.util.logging.Slf4j
import org.olf.kb.ErmResource

@Slf4j
@GrailsCompileStatic
class EntitlementLogEntry implements MultiTenant<EntitlementLogEntry>  {
  
  String id
  String seqid
  LocalDate startDate
  LocalDate endDate
  ErmResource res
  Entitlement directEntitlement
  Entitlement packageEntitlement
  String eventType
  

  static mapping = {
                      id column:'ele_id', generator: 'uuid2', length:36
                   seqid column:'ele_seq_id'
               startDate column:'ele_start_date'
                 endDate column:'ele_end_date'
                     res column:'ele_res', cascade: 'none'
       directEntitlement column:'ele_direct_entitlement', cascade: 'none'
      packageEntitlement column:'ele_pkg_entitlement', cascade: 'none'
               eventType column:'ele_event_type'
  }
   
  static constraints = {
                    seqid(nullable:false)
                startDate(nullable:false)
                  endDate(nullable:true)
                      res(nullable:false)
        directEntitlement(nullable:true)
       packageEntitlement(nullable:true)
                eventType(nullable:true)
  }
  
}
