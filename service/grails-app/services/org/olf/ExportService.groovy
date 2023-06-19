package org.olf 
import java.time.LocalDate

import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource

import grails.gorm.transactions.Transactional
import grails.gorm.multitenancy.Tenants


/**
 * Service for exporting resources.
 */
@Transactional(readOnly=true)
public class ExportService {
  CoverageService coverageService
  
  SubscriptionAgreement agreement (final String agreementId, boolean currentOnly = false) {
    SubscriptionAgreement agreement = SubscriptionAgreement.read (agreementId)
    if (agreement) {
      // Add the extra resources here.
      if (currentOnly) {
        agreement.metaClass.getResourceList = { current(agreement.id) }
        // Only current resources.
      } else {
        // all.
        agreement.metaClass.getResourceList = { all(agreement.id) }
      }
    }
    
    agreement
  }

  /* Note - HQL query construction looks to have changed since v5.2.
   * Before, we were using an OR in the WHERE block to "null out" certain rows on JOINs,
   * but this is no longer happening. The solution here was to add the WHERE clauses to the JOIN,
   * and replace the WHERE with a not-null constraint.  
  */
   
  List<List> all(final String agreementId = null) {
    Tenants.withCurrent{
      def results = null
      if (agreementId) {
        results = ErmResource.executeQuery("""
          SELECT res, pkg_ent, direct_ent
          FROM ErmResource as res
            LEFT JOIN res.entitlements as direct_ent
              ON (
                (
                  direct_ent.owner.id = :id
                    AND
                  res.class != Pkg
                )
              )
            LEFT JOIN res.pkg as pkg

              ON (res.class = PackageContentItem) 
              LEFT JOIN pkg.entitlements as pkg_ent
                ON (
                  pkg_ent.owner.id = :id
                )
          WHERE
            (direct_ent IS NOT NULL)
              OR
            (pkg_ent IS NOT NULL)
            
        """, [id: agreementId], [readOnly: true])
      } else {
        results = ErmResource.executeQuery("""
          SELECT res, pkg_ent, direct_ent
          FROM ErmResource as res
            LEFT JOIN res.entitlements as direct_ent
              ON (
                (
                  direct_ent.owner IS NOT NULL
                  AND
                  res.class != Pkg
                )
              )
            LEFT JOIN res.pkg as pkg

              ON res.class = PackageContentItem
              LEFT JOIN pkg.entitlements as pkg_ent
                ON (
                  pkg_ent.owner IS NOT NULL
                )
          WHERE
            (direct_ent IS NOT NULL)
              OR
            (pkg_ent IS NOT NULL)
        """, [readOnly: true])
      }
    
      // At this point we should have a List of results. But instead of each result being an ErmResource we
      // should have a collection
      // of [0]->ErmResource, [1]->Entitlement, [2]->Entitlement.
        
      // The first entitlement will be present if this is a PCI resource and it associated through a 
      // package and the second will be present if this resource was directly associated to an entitlement. 
      // This means that can/will get multiple entries for the same resource if there are multiple 
      // packages, or if the resources is associated directly and also through a package to an 
      // agreement. This behaviour is actually desirable for the export.
        
      // This method writes to the web request if there is one (which of course there should be as we are in a controller method)
      coverageService.lookupCoverageOverrides(results.collect{it[0]}) 
        
      return results  
    }
  }
  
  List<List> current (final String agreementId = null) {
    Tenants.withCurrent{
      final LocalDate today = LocalDate.now()
      
      def results = null
      if (agreementId) {
        results = ErmResource.executeQuery("""
          SELECT res, pkg_ent, direct_ent
          FROM ErmResource as res
            LEFT JOIN res.entitlements as direct_ent
              ON (
                (direct_ent.activeTo IS NULL OR direct_ent.activeTo >= :today)
                  AND
                  (direct_ent.activeFrom IS NULL OR direct_ent.activeFrom  <= :today)
                  AND (
                    direct_ent.owner.id = :agreementId
                    AND
                    res.class != Pkg
                  )

              )
            LEFT JOIN res.pkg as pkg

              ON (res.class = PackageContentItem
                AND (
                    (res.accessEnd IS NULL OR res.accessEnd >= :today)
                  AND
                    (res.accessStart IS NULL OR res.accessStart <= :today)
                )
              ) 
              LEFT JOIN pkg.entitlements as pkg_ent
                ON (
                (pkg_ent.activeTo IS NULL OR pkg_ent.activeTo >= :today)
                  AND
                  (pkg_ent.activeFrom IS NULL OR pkg_ent.activeFrom  <= :today)
                  AND pkg_ent.owner.id = :agreementId
              )

            WHERE
              (direct_ent IS NOT NULL)
              OR
              (pkg_ent IS NOT NULL)
        """, ['agreementId': agreementId, 'today': today], [readOnly: true])
      } else {
        // This query is duplicated in EntitlementLogService
        // Changes here need to be reviewed and may need to be applied there. 
        // This should be refactored into a single static repo of queries.
        results = ErmResource.executeQuery("""
          SELECT res, pkg_ent, direct_ent
          FROM ErmResource as res
            LEFT JOIN res.entitlements as direct_ent
              ON (
                (direct_ent.activeTo IS NULL OR direct_ent.activeTo >= :today)
                  AND
                  (direct_ent.activeFrom IS NULL OR direct_ent.activeFrom  <= :today)
                  AND
                  (
                    direct_ent.owner IS NOT NULL
                    AND
                    res.class != Pkg
                  )
              )
            LEFT JOIN res.pkg as pkg

              ON (res.class = PackageContentItem
                AND (
                    (res.accessEnd IS NULL OR res.accessEnd >= :today)
                  AND
                    (res.accessStart IS NULL OR res.accessStart <= :today)
                )
              )
              LEFT JOIN pkg.entitlements as pkg_ent
                ON (
                  (pkg_ent.activeTo IS NULL OR pkg_ent.activeTo >= :today)
                    AND
                    (pkg_ent.activeFrom IS NULL OR pkg_ent.activeFrom  <= :today)
                    AND pkg_ent.owner IS NOT NULL
                )
          WHERE
            (direct_ent IS NOT NULL)
              OR
            (pkg_ent IS NOT NULL)
        """, ['today': today], [readOnly: true])
      }
    
      // At this point we should have a List of results. But instead of each result being an ErmResource we
      // should have a collection
      // of [0]->ErmResource, [1]->Entitlement, [2]->Entitlement.
        
      // The first entitlement will be present if this is a PCI resource and it associated through a
      // package and the second will be present if this resource was directly associated to an entitlement.
      // This means that can/will get multiple entries for the same resource if there are multiple
      // packages, or if the resources is associated directly and also through a packge to an
      // agreement. This behaviour is actually desirable for the export.
        
      // This method writes to the web request if there is one (which of course there should be as we are in a controller method)
      coverageService.lookupCoverageOverrides(results.collect{it[0]})
        
      return results
    }
  }

// TODO if this is uncommented and used, check the WHERE changes made above are actually working here
//  List<ErmResource> future (final String agreementId = null) {
//    final LocalDate today = LocalDate.now()
//    
//    def results = null
//    if (agreementId) {
//      results = ErmResource.executeQuery("""
//        SELECT res, pkg_ent, direct_ent
//        FROM ErmResource as res
//          LEFT JOIN res.entitlements as direct_ent
//            ON (
//              (direct_ent.activeTo IS NULL OR direct_ent.activeTo >= :today)
//                AND
//                 (direct_ent.activeFrom IS NULL OR direct_ent.activeFrom  <= :today)
//                AND
//                  (
//                    direct_ent.owner.id = :id
//                      AND
//                    res.class != Pkg
//                  )
//            )
//          LEFT JOIN res.pkg as pkg
//
//            ON (res.class = PackageContentItem
//              AND (
//                  (res.accessEnd IS NULL OR res.accessEnd >= :today)
//                AND
//                  (res.accessStart IS NULL OR res.accessStart <= :today)
//              )
//            ) 
//            LEFT JOIN pkg.entitlements as pkg_ent
//              ON (
//              (pkg_ent.activeTo IS NULL OR pkg_ent.activeTo >= :today)
//                AND
//                 (pkg_ent.activeFrom IS NULL OR pkg_ent.activeFrom  <= :today)
//                AND
//                  (pkg_ent.owner.id = :id)
//            )
//        WHERE
//          (direct_ent IS NOT NULL)
//            OR
//          (pkg_ent IS NOT NULL)
//      """, [id: agreementId, 'today': today], [readOnly: true])
//    } else {
//      results = ErmResource.executeQuery("""
//        SELECT res, pkg_ent, direct_ent
//        FROM ErmResource as res
//          LEFT JOIN res.entitlements as direct_ent
//            ON (
//              (direct_ent.activeTo IS NULL OR direct_ent.activeTo >= :today)
//                AND
//                 (direct_ent.activeFrom IS NULL OR direct_ent.activeFrom  <= :today)
//                AND
//                 (
//                   direct_ent.owner IS NOT NULL
//                     AND
//                   res.class != Pkg
//                 )
//            )
//          LEFT JOIN res.pkg as pkg
//
//            ON (res.class = PackageContentItem
//              AND (
//                  (res.accessEnd IS NULL OR res.accessEnd >= :today)
//                AND
//                  (res.accessStart IS NULL OR res.accessStart <= :today)
//              )
//            )
//            LEFT JOIN pkg.entitlements as pkg_ent
//              ON (
//                (pkg_ent.activeTo IS NULL OR pkg_ent.activeTo >= :today)
//                  AND
//                   (pkg_ent.activeFrom IS NULL OR pkg_ent.activeFrom  <= :today)
//                  AND (pkg_ent.owner IS NOT NULL)
//              )
//        WHERE
//          (direct_ent IS NOT NULL)
//            OR
//          (pkg_ent IS NOT NULL)
//      """, ['today': today], [readOnly: true])
//    }
//  
//    // At this point we should have a List of results. But instead of each result being an ErmResource we
//    // should have a collection
//    // of [0]->ErmResource, [1]->Entitlement, [2]->Entitlement.
//      
//    // The first entitlement will be present if this is a PCI resource and it associated through a
//    // package and the second will be present if this resource was directly associated to an entitlement.
//    // This means that can/will get multiple entries for the same resource if there are multiple
//    // packages, or if the resources is associated directly and also through a packge to an
//    // agreement. This behaviour is actually desirable for the export.
//      
//    // This method writes to the web request if there is one (which of course there should be as we are in a controller method)
//    coverageService.lookupCoverageOverrides(results.collect{it[0]})
//      
//    return results
//  }

}
