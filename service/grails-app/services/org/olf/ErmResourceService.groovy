package org.olf

import groovy.sql.Sql
import org.grails.datastore.gorm.GormEntity
import org.hibernate.Session
import org.olf.erm.Entitlement
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.Work
import org.olf.kb.http.response.DeleteResponse
import org.olf.kb.http.response.DeletionCounts
import org.olf.kb.http.response.MarkForDeleteResponse
import org.olf.kb.Pkg
import org.olf.kb.Embargo
import org.olf.kb.CoverageStatement

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

import static org.springframework.transaction.annotation.Propagation.MANDATORY
import static groovy.transform.TypeCheckingMode.SKIP

import org.olf.kb.ErmResource
import org.olf.kb.TitleInstance

import grails.gorm.transactions.Transactional

import org.olf.kb.PlatformTitleInstance
import org.olf.kb.PackageContentItem

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
public class ErmResourceService {
  KbManagementService kbManagementService

  private final static String PCI_HQL = """
    SELECT id FROM PackageContentItem AS pci
    WHERE pci.pti.id = :resId
  """

  private final static String PTI_HQL = """
    SELECT id FROM PlatformTitleInstance AS pti
    WHERE pti.titleInstance.id = :resId
  """

  /* This method takes in an ErmResource id, and walks up the hierarchy of specificity
   * for that resource, returning a list of ids of related ErmResources
   * ie if the resource is a TI, then the list will comprise of itself,
   * all the PTIs for that TI and all the PCIs for those PTIs.
   *
   * If the passed resource is a PTI then the returned list will comprise
   * of the resource's id, and the ids of all PCIs for that PTI
   * 
   * If the passed resource is a PCI then the returned list should only comprise
   * of the resource's own id
   */
	@Transactional(propagation = MANDATORY)
  public List<String> getFullResourceList (ErmResource res) {
    List<String> resourceList = [res.id]
    List<String> ptis = []
    List<String> pcis = []
    //ErmResource.withNewTransaction {
      // If res is a TI, find all the associated PTIs and store them
      if (res instanceof TitleInstance) {
				PlatformTitleInstance.executeQuery(PTI_HQL, [resId: res.id]).each ( ptis.&add )
      }

      // If res is a PTI, find all PCIS associated and store them    
      if (res instanceof PlatformTitleInstance) {
				PackageContentItem.executeQuery(PCI_HQL, [resId: res.id]).each ( pcis.&add )
      }
			
      // Also store any PCIs attached to any PTIs stored earlier
      ptis.each { String ptiId ->
        PackageContentItem.executeQuery(PCI_HQL, [resId: ptiId]).each ( pcis.&add )
      }

      // At this point we have a comprehensive list of resources at various levels
      resourceList.addAll(ptis)
      resourceList.addAll(pcis)

    resourceList
  }

  private Set<String> handleEmptyListMapping(Set<String> resourceSet) {
    // Workaround for HQL 'NOT IN' bug: https://stackoverflow.com/questions/36879116/hibernate-hql-not-in-clause-doesnt-seem-to-work
    return (resourceSet.size() == 0 ? ["PLACEHOLDER_RESOURCE"] : resourceSet) as Set<String>
  }

  private String getPcisForPackageSubquery(List<String> packageId) {
    return """
        SELECT pci.id FROM PackageContentItem pci
        WHERE pci.pkg.id IN ${packageId.get(0)}
      """.toString()
  }

  private Set<String> getPcisForPackage(List<String> packageId) {
    return PackageContentItem.executeQuery(
      """
        SELECT pci.id FROM PackageContentItem pci
        WHERE pci.pkg.id IN :packageId
      """.toString(),
      [packageId:packageId]
    ) as Set
  }

  public MarkForDeleteResponse markForDelete(List<String> idInputs, Class<? extends ErmResource> resourceClass) {
    switch (resourceClass) {
      case Pkg.class:
        // Find the PCI ids that belong to the package, then markForDelete in the same way as for PCI endpoint.
        return markForDeleteInternal(new HashSet<String>(getPcisForPackage(idInputs)), new HashSet<String>(), new HashSet<String>())
      case PackageContentItem.class:
        Set<String> pciIds = PackageContentItem.executeQuery("select p.id from PackageContentItem p where p.id in :ids", [ids: idInputs]) as Set<String>
        return markForDeleteInternal(pciIds, new HashSet<String>(), new HashSet<String>())

//        return markForDeleteInternal("select p.id from PackageContentItem p where p.id in ${idInputs}", new HashSet<String>(), new HashSet<String>())
        break;
      case PlatformTitleInstance.class:
        Set<String> ptiIds = PlatformTitleInstance.executeQuery("select p.id from PlatformTitleInstance p where p.id in :ids", [ids: idInputs]) as Set<String>
        return markForDeleteInternal(new HashSet<String>(), ptiIds, new HashSet<String>())
        break;
      case TitleInstance.class:
        Set<String> tiIds = TitleInstance.executeQuery("select t.id from TitleInstance t where t.id in :ids", [ids: idInputs]) as Set<String>
        return markForDeleteInternal(new HashSet<String>(), new HashSet<String>(), tiIds)
        break;
      default:
        throw new RuntimeException("Unexpected resource class, cannot delete for class: ${resourceClass.name}");
    }
  }

  // We make use of the fact that these are Sets to deduplicate in the case that we have, say, two PCIs for a PTI
  // Normally a SELECT for PTIs from PCIs would return twice, but we can dedupe for free here.
  private MarkForDeleteResponse markForDeleteInternal(Set<String> pciIds, Set<String> ptiIds, Set<String> tiIds) {
    log.info("Initiating markForDelete with PCI ids: {}, PTI ids: {}, TI ids: {}", pciIds.size(), ptiIds.size(), tiIds.size())
    MarkForDeleteResponse markForDeletion = new MarkForDeleteResponse()

    if (pciIds.isEmpty() && ptiIds.isEmpty() && tiIds.isEmpty()) {
      log.warn("No ids found.")
      return markForDeletion
    }

    PackageContentItem.withSession { Session session ->
      session.doWork { Connection connection ->
        def sql = new Sql(connection)

        // Create two reusable temp tables:
        // the "main_ids" table is the ids we want to check for deletion
        // the "filter_ids" table is for joining to filter for (inner) or exclude (left join and null check) certain ids
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_main_ids (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_filter_ids (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("TRUNCATE TABLE temp_main_ids")
        sql.execute("TRUNCATE TABLE temp_filter_ids")

        // Mark PCIs for Delete
        populateTempTable(sql, 'temp_main_ids', pciIds)

        // PCI Ids in the temporary table are filtered for those that exist in the entitlements table (using inner join).
        def pcisWithEntitlements = fetchIds(sql, """
            SELECT DISTINCT t.id FROM temp_main_ids t
            INNER JOIN entitlement ent ON t.id = ent.ent_resource_fk
        """)
        // We can delete the PCIs from the original set minus the ones with entitlements.
        markForDeletion.pci.addAll(pciIds - pcisWithEntitlements)

        // Mark PTIs for Delete
        // Populate the temp filter table with the PCI Ids we want to delete.
        populateTempTable(sql, 'temp_filter_ids', markForDeletion.pci)

        // Filter the PCI table for those we want to delete, then select the PTI ids.
        def ptisForDeleteCheck = fetchIds(sql, """
            SELECT pci.pci_pti_fk FROM package_content_item pci
            INNER JOIN temp_filter_ids t_del ON t_del.id = pci.id
        """)
        ptisForDeleteCheck.addAll(ptiIds) // Combine with initial ptiIds

        // Load PTI ids into main temp table.
        populateTempTable(sql, 'temp_main_ids', ptisForDeleteCheck)

        // Get PTIs with entitlements
        def ptisWithEntitlements = fetchIds(sql, """
            SELECT DISTINCT t.id FROM temp_main_ids t
            INNER JOIN entitlement ent ON t.id = ent.ent_resource_fk
        """)

        // Take the PCI table, and filter for PCIs belonging to PTIs we marked for deletion.
        // THEN, join back on the PCIs we have marked for deletion using the PCI Id.
        // If a PTI that was marked for deletion does not have a PCI that was also marked for deletion (i.e. it is referenced by another pci),
        // then it will be NULL in the t_del.id column.
        def ptisWithRemainingPcis = fetchIds(sql, """
            SELECT DISTINCT pci.pci_pti_fk FROM package_content_item pci
            INNER JOIN temp_main_ids t_pti ON pci.pci_pti_fk = t_pti.id
            LEFT JOIN temp_filter_ids t_del ON pci.id = t_del.id
            WHERE t_del.id IS NULL
        """)

        markForDeletion.pti.addAll(ptisForDeleteCheck - (ptisWithEntitlements + ptisWithRemainingPcis))

        // Mark TIs and Works for Delete
        populateTempTable(sql, 'temp_filter_ids', markForDeletion.pti) // Load deletable PTIs into filter table

        def tisFromDeletablePtis = fetchIds(sql, """
            SELECT DISTINCT pti.pti_ti_fk FROM platform_title_instance pti
            INNER JOIN temp_filter_ids t ON pti.id = t.id
        """)
        def allTisForPtis = new HashSet<>(tiIds)
        allTisForPtis.addAll(tisFromDeletablePtis)

        populateTempTable(sql, 'temp_main_ids', allTisForPtis) // Load all candidate TIs into main table

        def tisWithRemainingPtis = fetchIds(sql, """
            SELECT DISTINCT pti.pti_ti_fk FROM platform_title_instance pti
            INNER JOIN temp_main_ids t_ti ON pti.pti_ti_fk = t_ti.id
            LEFT JOIN temp_filter_ids t_del_pti ON pti.id = t_del_pti.id
            WHERE t_del_pti.id IS NULL
        """)

        def tisForWorkChecking = allTisForPtis - tisWithRemainingPtis

        populateTempTable(sql, 'temp_main_ids', tisForWorkChecking) // Load TIs for work check into main table

        def workIdsToCheck = fetchIds(sql, """
            SELECT DISTINCT ti.ti_work_fk FROM title_instance ti
            INNER JOIN temp_main_ids t ON ti.id = t.id
        """)

        populateTempTable(sql, 'temp_main_ids', workIdsToCheck) // Load candidate Works into main table

        def worksWithRemainingPtis = fetchIds(sql, """
            SELECT DISTINCT ti.ti_work_fk FROM platform_title_instance pti
            INNER JOIN title_instance ti ON pti.pti_ti_fk = ti.id
            INNER JOIN temp_main_ids t_work ON ti.ti_work_fk = t_work.id
            LEFT JOIN temp_filter_ids t_del_pti ON pti.id = t_del_pti.id
            WHERE t_del_pti.id IS NULL
        """)

        markForDeletion.work.addAll(workIdsToCheck - worksWithRemainingPtis)

        if (!markForDeletion.work.isEmpty()) {
          // Load final deletable Works to mark all related TIs for deletion.
          populateTempTable(sql, 'temp_main_ids', markForDeletion.work)
          def finalTisToDelete = fetchIds(sql, """
                SELECT ti.id FROM title_instance ti
                INNER JOIN temp_main_ids t ON ti.ti_work_fk = t.id
            """)
          markForDeletion.ti.addAll(finalTisToDelete)
        }

        // --- Teardown: Temp tables are dropped automatically at transaction end ---
      }
    }

    log.info("Marked resources for delete completed: {} PCIs, {} PTIs, {} TIs, {} Works",
      markForDeletion.pci.size(), markForDeletion.pti.size(), markForDeletion.ti.size(), markForDeletion.work.size())

    return markForDeletion
  }

  // --- HELPER METHODS ---
  // Clears a table and populates it with a given set of IDs using efficient batching.
  private void populateTempTable(Sql sql, String tableName, Collection<String> ids) {
    String deleteSql = "DELETE FROM ${tableName}"
    sql.execute(deleteSql)
    if (ids.isEmpty()) return

    sql.withBatch(1000, "INSERT INTO ${tableName} (id) VALUES (?)") { ps ->
      ids.each { id ->
        ps.addBatch([id])
      }
    }
  }

 // Executes a SELECT query that returns a single column of IDs and returns them as a Set.
  private Set<String> fetchIds(Sql sql, String query) {
    sql.rows(query).collect { it[0] } as Set<String>
  }

  @CompileStatic(SKIP)
  private Set<String> deleteIds(Class domainClass, Collection<String> ids) {
    Set<String> successfullyDeletedIds = new HashSet<>()

    ids.each { String id ->
        def instance = domainClass.get(id)

        if (instance) {
          instance.delete()
          successfullyDeletedIds.add(id)
          log.trace("Successfully deleted id: {}", id)
        } else {
          log.warn("Could not find instance of {} with id {} to delete.", domainClass.name, id)
        }
      }

    return successfullyDeletedIds
  }


  public Map<String, DeleteResponse> deleteResourcesFromPackage(List<String> idInputs) {
    Map<String, DeleteResponse> deleteResourcesResponseMap = [:]

    // Collect responses for each package in a Map.
    idInputs.forEach{String id -> {
      MarkForDeleteResponse forDeletion = markForDelete(idInputs, Pkg.class); // Finds all PCIs for package and deletes as though the PCI Ids were passed in.
      deleteResourcesResponseMap.put(id, deleteResourcesInternal(forDeletion))
    }}

    return deleteResourcesResponseMap;
  }

  public DeleteResponse deleteResources(List<String> idInputs, Class<? extends ErmResource> resourceClass) {
    MarkForDeleteResponse forDeletion = markForDelete(idInputs, resourceClass);
    return deleteResourcesInternal(forDeletion);
  }

  @CompileStatic(SKIP)
  private int executeBatchDelete(Class<? extends GormEntity> domainClass, Set<String> ids, int batchSize = 10000) {
    int totalDeleted = 0
    // Split id set into batches using collate()
    List<List<String>> batches = ids.toList().collate(batchSize)

    for (int i = 0; i < batches.size(); i++) {
      List<String> batchOfIds = batches[i]

      try {
        // Use a transaction for each batch
        domainClass.withTransaction { status ->
          String hql = "from ${domainClass.name} where id in :idList"
          List<? extends GormEntity> instancesToDelete = domainClass.findAll(hql, [idList: batchOfIds])

          if (instancesToDelete) {
            domainClass.deleteAll(instancesToDelete)
            totalDeleted += instancesToDelete.size()
            log.debug("Successfully committed batch {}/{} for {}", i + 1, batches.size(), domainClass.name)
          }
        }
      } catch (Exception e) {
        // The transaction for this batch will be automatically rolled back by withTransaction
        log.error("Failed to process batch {}/{} for {}. The process will stop. " +
          "Previous batches are already committed. Error: {}",
          i + 1, batches.size(), domainClass.name, e.message)

        // rethrow error to stop delete operation.
        throw new RuntimeException("Failed on batch ${i + 1}. See logs for details.", e)
      }
    }

    return totalDeleted
  }

  @CompileStatic(SKIP)
  private DeleteResponse deleteResourcesInternal(MarkForDeleteResponse resourcesToDelete) {
    DeleteResponse response = new DeleteResponse()
    MarkForDeleteResponse deletedIds = new MarkForDeleteResponse()

    if (resourcesToDelete == null) {
      log.warn("deleteResources called with null MarkForDeleteResponse")
      DeletionCounts emptyCount = new DeletionCounts(0, 0, 0, 0)
      response.statistics = emptyCount
      return response
    }

    DeletionCounts deletionCounts = new DeletionCounts()

    if (resourcesToDelete.pci && !resourcesToDelete.pci.isEmpty()) {
      deletionCounts.pciDeleted = executeBatchDelete(PackageContentItem, resourcesToDelete.pci)
    }


    if (resourcesToDelete.pti && !resourcesToDelete.pti.isEmpty()) {
      deletionCounts.ptiDeleted = executeBatchDelete(PlatformTitleInstance, resourcesToDelete.pti)
    }

    if (resourcesToDelete.ti && !resourcesToDelete.ti.isEmpty()) {
      deletionCounts.tiDeleted = executeBatchDelete(TitleInstance, resourcesToDelete.ti)
    }

    if (resourcesToDelete.work && !resourcesToDelete.work.isEmpty()) {
      deletionCounts.workDeleted = executeBatchDelete(Work, resourcesToDelete.work)
    }

    log.info("Deletion complete. Counts: {}", deletionCounts)
    response.statistics = deletionCounts
//    response.deletedIds = deletedIds
//    response.selectedForDeletion = resourcesToDelete

    return response
  }
}

