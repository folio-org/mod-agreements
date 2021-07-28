package org.olf

import org.olf.dataimport.internal.PackageSchema
import org.olf.erm.Entitlement
import org.olf.kb.ContentActivationRecord
import org.olf.kb.KBCache
import org.olf.kb.KBCacheUpdater
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.RemoteKB
import org.springframework.transaction.TransactionDefinition
import java.time.LocalDate
import org.olf.erm.EntitlementLogEntry;



/**
 * This service turns the declarative set of entitlements which reprsent the list of available titles
 * at a point-in-time (today) into a log that can be read in sequence with a cursor to get changes since
 * a last sequence-id. This service detects and records the delta of the list of active titles from one
 * moment (day) to the next enabling consuming services to say "Tell me what titles have become available
 * or been removed since I last asked on X"
 */
public class EntitlementLogService {

  // This HQL is based firmly on the query in ExportService. Changes there need to be reviewed and may
  // need to be applied here. This should be refactored into a single static repo of queries.
  private static final String NEW_ENTITLEMENTS_QUERY = '''
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
                  AND
                    (pkg_ent.owner IS NOT NULL)
              )
        WHERE (
          (direct_ent IS NOT NULL)
            OR
          (pkg_ent IS NOT NULL)
        ) AND (
          NOT EXISTS ( SELECT ele 
                         FROM EntitlementLogEntry as ele 
                        WHERE ele.res = res 
                          AND ( ( ( ele.directEntitlement is null ) AND ( direct_ent is null ) ) OR ele.directEntitlement=direct_ent )
                          AND ( ( ( ele.packageEntitlement is null ) AND ( pkg_ent is null ) ) OR ele.packageEntitlement=pkg_ent )
                          AND ele.endDate is null ) 
        )
       
   '''

  /**
   * Find all the current live resources that do not have a corresponding entitlement.
   * Do this by joining to the package or direct entitlement and looking for the resource
   */
  private static final String TERMINATED_ENTITLEMENTS_QUERY = '''
    SELECT ele 
      FROM EntitlementLogEntry as ele 
     WHERE ele.endDate is null
       AND NOT EXISTS ( SELECT ent
                          FROM Entitlement as ent
                            JOIN ent.resource as package_resource
                              JOIN package_resource.contentItems as package_content_item
                         WHERE ele.packageEntitlement = ent
                           AND ent.resource.class = Pkg
                           AND package_content_item = ele.res
                           AND ( ent.activeTo IS NULL OR ent.activeTo >= :today ) 
                           AND ( ent.activeFrom IS NULL OR ent.activeFrom  <= :today ) )
       AND NOT EXISTS ( SELECT ent
                          FROM Entitlement as ent
                         WHERE ele.directEntitlement = ent 
                           AND ( ent.activeTo IS NULL OR ent.activeTo >= :today ) 
                           AND ( ent.activeFrom IS NULL OR ent.activeFrom  <= :today ) )
  '''

  def triggerUpdate() {

    long start_time = System.currentTimeMillis();
    long seqno = 0;

    EntitlementLogEntry.withNewTransaction {
      log.debug("EntitlementLogService::triggerUpdate()");
      final LocalDate today = LocalDate.now()
      def new_entitlements = EntitlementLogEntry.executeQuery(NEW_ENTITLEMENTS_QUERY, ['today': today], [readOnly: true])
      new_entitlements.each {
        String seq = String.format('%015d-%06d',start_time,seqno++)
        log.debug("  -> add entitlement for ${start_time} ${seq} ${it[0].id} pkg:${it[1]?.id} direct:${it[2]?.id}");
        
        EntitlementLogEntry ele = new EntitlementLogEntry(
                                        seqid: seq,
                                        startDate:today,
                                        endDate:null,
                                        res:it[0],
                                        packageEntitlement:it[1],
                                        directEntitlement:it[2],
                                        eventType:'ADD'
                                      ).save(flush:true, failOnError:true);
      }

      def terminated_entitlements = EntitlementLogEntry.executeQuery(TERMINATED_ENTITLEMENTS_QUERY, ['today': today], [readOnly: true])
      terminated_entitlements.each {
        String seq = String.format('%015d-%06d',start_time,seqno++)

        log.debug("  -> close out entitlement for ${start_time} ${seq} ${it.id}");
        // EntitlementLogEntry.executeUpdate('UPDATE EntitlementLogEntry set endDate = :ed where id=:id',[ed:today, id:it.id]);
        EntitlementLogEntry.executeUpdate('UPDATE EntitlementLogEntry set endDate = :ed, packageEntitlement=null, directEntitlement=null where id=:id',[ed:today, id:it.id]);

        log.debug("  -> Create a new log entry that documents the closing out of the entitlement");
        EntitlementLogEntry ele = new EntitlementLogEntry(
                                        seqid: seq,
                                        startDate:it.startDate,
                                        endDate:today,
                                        res:it.res,
                                        // packageEntitlement:it.packageEntitlement,
                                        // directEntitlement:it.directEntitlement,
                                        eventType:'REMOVE'
                                      ).save(flush:true, failOnError:true);

      }
    }

    log.debug("At end - ${seqno} entitlements activated");
   
    return "OK"
  }

}

