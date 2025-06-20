package org.olf

import groovy.sql.Sql
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

        def pcisWithEntitlements = fetchIds(sql, """
            SELECT DISTINCT t.id FROM temp_main_ids t
            INNER JOIN entitlement ent ON t.id = ent.ent_resource_fk
        """)
        markForDeletion.pci.addAll(pciIds - pcisWithEntitlements)

        // Mark PTIs for Delete
        populateTempTable(sql, 'temp_filter_ids', markForDeletion.pci) // Load deletable PCIs into the filter table

        def ptisForDeleteCheck = fetchIds(sql, """
            SELECT pci.pci_pti_fk FROM package_content_item pci
            INNER JOIN temp_filter_ids t_del ON t_del.id = pci.id
        """)
        ptisForDeleteCheck.addAll(ptiIds) // Combine with initial ptiIds

        populateTempTable(sql, 'temp_main_ids', ptisForDeleteCheck) // Load all candidate PTIs into the main table

        def ptisWithEntitlements = fetchIds(sql, """
            SELECT DISTINCT t.id FROM temp_main_ids t
            INNER JOIN entitlement ent ON t.id = ent.ent_resource_fk
        """)

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
          populateTempTable(sql, 'temp_main_ids', markForDeletion.work) // Load final deletable Works
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
    if (ids == null || ids.isEmpty()) {
      return new HashSet<>()
    }
    String hql = "from ${domainClass.name} where id in :idList"

    List instancesToDelete = domainClass.findAll(hql, [idList: new ArrayList(ids)])

    if (instancesToDelete.isEmpty()) {
      return new HashSet<>()
    }

    Set<String> successfullyDeletedIds = new HashSet<>()

    instancesToDelete.each { instance ->
      try {
        instance.delete()
        successfullyDeletedIds.add(instance.id)
        log.trace("Successfully deleted id: {}", instance.id)
      } catch (Exception e) {
        log.error("Failed to delete id {}: {}", instance.id, e.message, e)
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

//  def sessionFactory // Injected by Grails

//  @Transactional
//  @CompileStatic(SKIP)
//  void batchDelete(Class domainClass, Collection<String> allIds) {
//    if (!allIds) {
//      log.info("No IDs provided for deletion.")
//      return
//    }
//
//    int batchSize = 200
//    int totalDeleted = 0
//
//    // Groovy's collate() breaks a list into batches
//    List<List<String>> idBatches = allIds.collate(batchSize)
//
//    for (List<String> idBatch in idBatches) {
//      String hql = "from ${domainClass.name} where id in :idList"
//
//      List recordsInBatch = domainClass.findAll(hql, [idList: new ArrayList(idBatch)])
//
//      for (def record in recordsInBatch) {
//        record.delete(flush: false)
//      }
//
//
//      Session session = sessionFactory.currentSession
//      session.flush()
//      session.clear()
//
//      totalDeleted += recordsInBatch.size()
//      log.info("Deleted ${totalDeleted} of ${allIds.size()} records so far...")
//    }
//    log.info("Finished batch deletion.")
//  }

  @Transactional
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

//    log.info("Attempting to delete resources: {}", resourcesToDelete)
    DeletionCounts deletionCounts = new DeletionCounts()

    if (resourcesToDelete.pci && !resourcesToDelete.pci.isEmpty()) {
//      log.debug("Deleting PCIs: {}", resourcesToDelete.pci)

//      deletedIds.pci = deleteIds(PackageContentItem, resourcesToDelete.pci)
      List<String> pciIds = new ArrayList<>(resourcesToDelete.pci)

      if (pciIds) { // Ensure the list is not empty before proceeding
        // Now the compiler knows for sure it's passing an Iterable<String>
        List<PackageContentItem> itemsToDelete = PackageContentItem.getAll(pciIds)
        if (itemsToDelete) {
          PackageContentItem.deleteAll(itemsToDelete)
        }
      }
    }
    deletionCounts.pciDeleted =  deletedIds.pci?.size()

    if (resourcesToDelete.pti && !resourcesToDelete.pti.isEmpty()) {
//      log.debug("Deleting PTIs: {}", resourcesToDelete.pti)

//      deletedIds.pti = deleteIds(PlatformTitleInstance, resourcesToDelete.pti)
      List<String> ptiIds = new ArrayList<>(resourcesToDelete.pti)
      List<PlatformTitleInstance> itemsToDelete = PlatformTitleInstance.getAll(ptiIds)
      if (itemsToDelete) {
        PlatformTitleInstance.deleteAll(itemsToDelete)
      }
    }
    deletionCounts.ptiDeleted = deletedIds.pti?.size()

    if (resourcesToDelete.ti && !resourcesToDelete.ti.isEmpty()) {
//      log.debug("Deleting TIs: {}", resourcesToDelete.ti)
//      deletedIds.ti = deleteIds(TitleInstance, resourcesToDelete.ti)
      List<String> tiIds = new ArrayList<>(resourcesToDelete.ti)
      List<TitleInstance> itemsToDelete = TitleInstance.getAll(tiIds)
      if (itemsToDelete) {
        TitleInstance.deleteAll(itemsToDelete)
      }
    }
    deletionCounts.tiDeleted = deletedIds.ti?.size()

    if (resourcesToDelete.work && !resourcesToDelete.work.isEmpty()) {
//      log.debug("Deleting Works: {}", resourcesToDelete.work)
//      deletedIds.work = deleteIds(Work, resourcesToDelete.work)
      List<String> workIds = new ArrayList<>(resourcesToDelete.work)
      List<Work> itemsToDelete = Work.getAll(workIds)
      if (itemsToDelete) {
        Work.deleteAll(itemsToDelete)
      }
    }
    deletionCounts.workDeleted = deletedIds.work?.size()

    log.info("Deletion complete. Counts: {}", deletionCounts)
    response.statistics = deletionCounts
//    response.deletedIds = deletedIds
//    response.selectedForDeletion = resourcesToDelete

    return response
  }
}

