package org.olf.erm

import javax.persistence.Transient

import java.time.LocalDate
import grails.compiler.GrailsCompileStatic
import groovy.util.logging.Slf4j
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem

import grails.gorm.multitenancy.Tenants
import grails.gorm.MultiTenant

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

  @Transient
  public String getActiveEntitlementCountForResource() {
    Tenants.withCurrent{
      final LocalDate today = LocalDate.now()

      PackageContentItem resAsPci = PackageContentItem.findById(res.id);

      List<Integer> direct_count = Entitlement.executeQuery(
        """
          SELECT COUNT(ent) FROM Entitlement ent
          WHERE ent.resource.id = :resId
            AND (ent.activeTo IS NULL OR ent.activeTo >= :today)
            AND (ent.activeFrom IS NULL OR ent.activeFrom <= :today)
            AND (ent.owner IS NOT NULL)
            AND ent.resource.class != Pkg
        """.toString(),
        [
          'resId': res.id,
          'today': today
        ], [readOnly: true]
      )

      List<Integer> package_count = [0]
      if (resAsPci != null) {
        package_count = Entitlement.executeQuery(
          """
            SELECT COUNT(ent) FROM Entitlement ent
            WHERE
              ent.resource.id = :resPkgId
              AND (
                (ent.resource.accessEnd IS NULL OR ent.resource.accessEnd >= :today)
                AND
                (ent.resource.accessStart IS NULL OR ent.resource.accessStart <= :today)
              )
              AND (ent.activeTo IS NULL OR ent.activeTo >= :today)
              AND (ent.activeFrom IS NULL OR ent.activeFrom <= :today)
              AND (ent.owner IS NOT NULL)
          """.toString(),
          [
            'resPkgId': resAsPci.pkg.id,
            'today': today
          ], [readOnly: true]
        )
      }

      return direct_count[0] + package_count[0];
    }
  }
  
}
