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

import com.k_int.web.toolkit.settings.AppSetting


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

  // Splitting these up so that we can get distinct rows for direct and package entitlements
  private static final String NEW_DIRECT_ENTITLEMENTS_QUERY = '''
      SELECT res, direct_ent
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
        WHERE (
          (direct_ent IS NOT NULL)
        ) AND (
          NOT EXISTS (
            SELECT ele FROM EntitlementLogEntry as ele 
              WHERE ele.res = res
                AND ( ( ( ele.directEntitlement is null ) AND ( direct_ent is null ) ) OR ele.directEntitlement=direct_ent )
                AND ele.endDate is null ) 
        )
       
   '''

   private static final String NEW_PKG_ENTITLEMENTS_QUERY = '''
      SELECT res, pkg_ent
        FROM ErmResource as res
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
        WHERE (
          (pkg_ent IS NOT NULL)
        ) AND (
          NOT EXISTS (
            SELECT ele FROM EntitlementLogEntry as ele 
              WHERE ele.res = res
                AND ( ( ( ele.packageEntitlement is null ) AND ( pkg_ent is null ) ) OR ele.packageEntitlement=pkg_ent )
                AND ele.endDate is null ) 
        )
       
   '''

  /**
   * Find all the current live resources that do not have a corresponding entitlement.
   * Do this by joining to the package or direct entitlement and looking for the resource
   */

  private static final String TERMINATED_ENTITLEMENTS_QUERY = '''
    SELECT ele.id, ele.startDate, ele.res, ele.eventType
      FROM EntitlementLogEntry as ele 
     WHERE ele.endDate is null
        AND NOT EXISTS (
          SELECT ent FROM Entitlement as ent
            JOIN ent.resource as package_resource
            JOIN package_resource.contentItems as package_content_item
            WHERE ele.packageEntitlement = ent
              AND ent.resource.class = Pkg
              AND package_content_item = ele.res
              AND ( ent.activeTo IS NULL OR ent.activeTo >= :today ) 
              AND ( ent.activeFrom IS NULL OR ent.activeFrom  <= :today ) )
        AND NOT EXISTS (
          SELECT ent FROM Entitlement as ent
            WHERE ele.directEntitlement = ent 
              AND ( ent.activeTo IS NULL OR ent.activeTo >= :today ) 
              AND ( ent.activeFrom IS NULL OR ent.activeFrom  <= :today ) )
  '''

  /*
   * Find all the entitlementLogEntries of type "ADD" where no "REMOVE" event exists,
   * and where the entitlement or its resource has been "significantly" changed since the last run
   * ("significantly" to be defined as we go on, to begin with suppressFromDiscovery or coverage)
   */
  private static final String UPDATED_ENTITLEMENTS_QUERY = '''
    SELECT ele
      FROM EntitlementLogEntry as ele
        LEFT JOIN ele.res as res
        LEFT JOIN res.entitlements as direct_ent
        LEFT JOIN res.pkg as pkg
        LEFT JOIN pkg.entitlements as pkg_ent
    WHERE (
      ele.eventType = 'ADD'
    ) AND NOT EXISTS (
      FROM EntitlementLogEntry AS ele1
      WHERE
        ele1.res = res AND
        ele1.eventType = 'REMOVE'
    ) AND (
      (
        direct_ent IS NOT NULL AND
        (
          (
            direct_ent.lastUpdated >= :cursor AND
            direct_ent.lastUpdated > direct_ent.dateCreated
          ) OR
          (
            direct_ent.contentUpdated >= :cursor AND
            direct_ent.contentUpdated > direct_ent.dateCreated
          )
        )
      ) OR (
        pkg_ent IS NOT NULL AND
        (
          pkg_ent.lastUpdated >= :cursor AND
          pkg_ent.lastUpdated > direct_ent.dateCreated
        ) OR
        (
          pkg_ent.contentUpdated >= :cursor AND
          pkg_ent.contentUpdated > direct_ent.dateCreated
        )
      )
    )
   '''

  def triggerUpdate() {
    log.debug("EntitlementLogService::triggerUpdate()");

    long start_time = System.currentTimeMillis();
    long seqno = 0;
    final LocalDate today = LocalDate.now()


    // Set up/read cursor values
    AppSetting entitlement_log_update_cursor
    Date last_run

    // One transaction for fetching the initial values/creating AppSettings
    AppSetting.withNewTransaction {
      // Need to flush this initially so it exists for first instance
      // Set initial cursor to 0 so everything currently in system gets taken into acct
      entitlement_log_update_cursor = AppSetting.findByKey('entitlement_log_update_cursor') ?: new AppSetting(
        section:'registry',
        settingType:'Date',
        key: 'entitlement_log_update_cursor',
        value: 0
      ).save(flush: true, failOnError: true)

      // Parse setting Strings to Date/Long
      last_run = new Date(Long.parseLong(entitlement_log_update_cursor.value))
    }

    EntitlementLogEntry.withNewTransaction {
      // NEW ENTITLEMENTS
      //def new_entitlements = EntitlementLogEntry.executeQuery(NEW_ENTITLEMENTS_QUERY, ['today': today], [readOnly: true])

      // In Hibernate 6 we'll have access to UNION and could do this in one query
      def new_direct_entitlements = EntitlementLogEntry.executeQuery(NEW_DIRECT_ENTITLEMENTS_QUERY, ['today': today], [readOnly: true])
      def new_pkg_entitlements = EntitlementLogEntry.executeQuery(NEW_PKG_ENTITLEMENTS_QUERY, ['today': today], [readOnly: true])

      new_direct_entitlements.each {
        String seq = String.format('%015d-%06d',start_time,seqno++)
        log.debug("  -> add entitlement for ${start_time} ${seq} ${it[0].id} pkg:${null} direct:${it[1]?.id}");
        
        EntitlementLogEntry ele = new EntitlementLogEntry(
                                        seqid: seq,
                                        startDate:today,
                                        endDate:null,
                                        res:it[0],
                                        packageEntitlement:null,
                                        directEntitlement:it[1],
                                        eventType:'ADD'
                                      ).save(failOnError:true);
      }

      new_pkg_entitlements.each {
        String seq = String.format('%015d-%06d',start_time,seqno++)
        log.debug("  -> add entitlement for ${start_time} ${seq} ${it[0].id} pkg:${it[1]?.id} direct:${null}");
        
        EntitlementLogEntry ele = new EntitlementLogEntry(
                                        seqid: seq,
                                        startDate:today,
                                        endDate:null,
                                        res:it[0],
                                        packageEntitlement:it[1],
                                        directEntitlement:null,
                                        eventType:'ADD'
                                      ).save(failOnError:true);
      }
    }
    EntitlementLogEntry.withNewTransaction {
      // TERMINATED ENTITLEMENTS
      def terminated_entitlements = EntitlementLogEntry.executeQuery(TERMINATED_ENTITLEMENTS_QUERY, ['today': today], [readOnly: true])
      terminated_entitlements.each {
        String seq = String.format('%015d-%06d',start_time,seqno++)

        log.debug("  -> close out entitlement for ${start_time} ${seq} ${it[0]}");
        // EntitlementLogEntry.executeUpdate('UPDATE EntitlementLogEntry set endDate = :ed where id=:id',[ed:today, id:it.id]);
        EntitlementLogEntry.executeUpdate('UPDATE EntitlementLogEntry set endDate = :ed, packageEntitlement=null, directEntitlement=null where id=:id',[ed:today, id:it[0]]);


        // We needed to update all of the EntitlementLogEntries to ensure no missing rows.
        // But we only need to create a single "remove" entry to document the deletion.
        // Key off the single "ADD" entry
        if (it[3] == 'ADD') {
          log.debug("  -> Create a new log entry that documents the closing out of the entitlement");
          EntitlementLogEntry ele = new EntitlementLogEntry(
            seqid: seq,
            startDate:it[1],
            endDate:today,
            res:it[2],
            // packageEntitlement:it.packageEntitlement,
            // directEntitlement:it.directEntitlement,
            eventType:'REMOVE'
          ).save(failOnError:true);
        }
      }
    }
    EntitlementLogEntry.withNewTransaction {
      // UPDATED ENTITLEMENTS
      def updated_entitlements = EntitlementLogEntry.executeQuery(UPDATED_ENTITLEMENTS_QUERY, ['cursor': last_run], [readOnly: true])
      updated_entitlements.each {
        String seq = String.format('%015d-%06d',start_time,seqno++)
        
        log.debug("  -> Create a new log entry that documents the updating of the entitlement");
        EntitlementLogEntry ele = new EntitlementLogEntry(
          seqid: seq,
          startDate:it.startDate,
          endDate:null,
          res:it.res,
          packageEntitlement:it.packageEntitlement,
          directEntitlement:it.directEntitlement,
          eventType:'UPDATE'
        ).save(failOnError:true);

      }
    }

    // Set cursor to start time for next run
    AppSetting.withNewTransaction {
      entitlement_log_update_cursor.value = start_time
      entitlement_log_update_cursor.save(flush: true, failOnError: true)
    }

    log.debug("At end - ${seqno} entitlements activated");
   
    return "OK"
  }

}

