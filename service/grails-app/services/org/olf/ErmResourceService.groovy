package org.olf

import grails.converters.JSON
import groovy.sql.Sql
import org.hibernate.Session
import org.olf.general.jobs.ResourceDeletionJob
import org.olf.kb.Work
import org.olf.kb.http.response.DeleteResponse
import org.olf.kb.http.response.DeletionCounts
import org.olf.kb.http.response.MarkForDeleteMap
import org.olf.kb.Pkg
import org.olf.kb.http.response.MarkForDeleteResponse
import org.olf.general.ResourceDeletionJobType

import java.sql.Connection
import java.time.Instant

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

  public MarkForDeleteResponse markForDelete(List<String> idInputs, Class<? extends ErmResource> resourceClass) {
    switch (resourceClass) {
      case Pkg.class:
        // Find the PCI ids that belong to the package, then markForDelete in the same way as for PCI endpoint.
        Set<String> pciIdsForPackage = PackageContentItem.executeQuery("SELECT pci.id FROM PackageContentItem pci WHERE pci.pkg.id IN :packageId".toString(), [packageId: idInputs]) as Set<String>
        return markForDeleteInternal(new HashSet<String>(pciIdsForPackage), new HashSet<String>(), new HashSet<String>())
      case PackageContentItem.class:
        Set<String> pciIds = PackageContentItem.executeQuery("select p.id from PackageContentItem p where p.id in :ids", [ids: idInputs]) as Set<String>
        return markForDeleteInternal(pciIds, new HashSet<String>(), new HashSet<String>())
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
  // MarkForDeleteInternal uses temporary SQL tables (as opposed to batching with GORM) in order to
  // handle lists of IDs > 65535 items long. It does not create a new transaction but uses the current one (opened by MarkForDelete())
  private MarkForDeleteResponse markForDeleteInternal(Set<String> pciIds, Set<String> ptiIds, Set<String> tiIds) {
    log.info("Initiating markForDelete with PCI ids: {}, PTI ids: {}, TI ids: {}", pciIds.size(), ptiIds.size(), tiIds.size())
    MarkForDeleteMap markForDeletion = new MarkForDeleteMap()
    MarkForDeleteResponse response = new MarkForDeleteResponse();

    if (pciIds.isEmpty() && ptiIds.isEmpty() && tiIds.isEmpty()) {
      log.warn("No ids found.")
      return response
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

        // Get TI ids for TIs that belong to a PTI that is markedForDeletion.
        def tisFromDeletablePtis = fetchIds(sql, """
            SELECT DISTINCT pti.pti_ti_fk FROM platform_title_instance pti
            INNER JOIN temp_filter_ids t ON pti.id = t.id
        """)
        def allTisForPtis = new HashSet<>(tiIds)
        allTisForPtis.addAll(tisFromDeletablePtis)

        populateTempTable(sql, 'temp_main_ids', allTisForPtis) // Load all candidate TIs from the last step into main table

        // First- the inner join filters for any PTIs which reference the TIs which we found from "deletable PTIs"
        // Note that this could include PTIs that were not marked for deletion, but still happen to reference a TI
        // that does belong to a different PTI marked for deletion.
        // THEN, we join back on the temp table containing the deletable PTI ids. Any PTI existing that is not in the
        // original deletable PTIs table will be NULL in this new column.
        def tisWithRemainingPtis = fetchIds(sql, """
            SELECT DISTINCT pti.pti_ti_fk FROM platform_title_instance pti
            INNER JOIN temp_main_ids t_ti ON pti.pti_ti_fk = t_ti.id 
            LEFT JOIN temp_filter_ids t_del_pti ON pti.id = t_del_pti.id
            WHERE t_del_pti.id IS NULL
        """)

        // Remove the tisWithRemaining (non-deletable) PTIs- we now have a list of TI ids that are safe to delete.
        def tisForWorkChecking = allTisForPtis - tisWithRemainingPtis

        populateTempTable(sql, 'temp_main_ids', tisForWorkChecking) // Load TIs for work check into main table

        // We now need to find the works that are safe to delete, then work our way back up the tree to find all
        // the TIs that reference these works.
        def workIdsToCheck = fetchIds(sql, """
            SELECT DISTINCT ti.ti_work_fk FROM title_instance ti
            INNER JOIN temp_main_ids t ON ti.id = t.id
        """)

        populateTempTable(sql, 'temp_main_ids', workIdsToCheck) // Load candidate Works into main table

        // Start with the PTIs table, and join on the title instance table
        // The second inner join can then filter the table for workIds that belong to deletable TIs
        // The left join then joins back on the table of deletable PTIs. If a PTI exists for one of our "deletable works"
        // that is not eligible for deletion, it will be NULL.
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

        //  Temp tables are dropped automatically at transaction end
      }
    }

    log.info("Marked resources for delete completed: {} PCIs, {} PTIs, {} TIs, {} Works",
      markForDeletion.pci.size(), markForDeletion.pti.size(), markForDeletion.ti.size(), markForDeletion.work.size())


    response.resourceIds = markForDeletion
    response.statistics = getCountsFromDeletionMap(markForDeletion)
    return response;
  }

  // --- HELPER METHODS ---
  // Clears a table and populates it with a given set of IDs using batching.
  private void populateTempTable(Sql sql, String tableName, Collection<String> ids) {
    String truncateSql = "TRUNCATE TABLE ${tableName}" // Clears all data from the table.
    sql.execute(truncateSql)
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

    domainClass.withSession { currentSess ->
      domainClass.withTransaction {
        domainClass.withNewSession { newSess ->
          domainClass.withTransaction {
            ids.each { String id ->
              // For each ID, find the domain instance (e.g. the PCI with id xyz)
              def instance = domainClass.get(id)

              if (instance) {
                instance.delete() // delete the instance using GORM (will cascade to related objects)
                successfullyDeletedIds.add(id) // track the id that has been deleted
              } else {
                log.warn("Could not find instance of {} with id {} to delete.", domainClass.name, id)
                // we should never hit this, but useful to log incase.
              }
            }
          }
          newSess.clear()
        }
      }
    }

    return successfullyDeletedIds
  }

  public Map markForDeleteFromPackage(List<String> idInputs) {
    Map<String, MarkForDeleteResponse> deleteResourcesResponseMap = [:]

    // Collect responses for each package in a Map.
    idInputs.forEach{String id -> {
      MarkForDeleteResponse forDeletion = markForDelete([id], Pkg.class); // Finds all PCIs for package and deletes as though the PCI Ids were passed in.
      deleteResourcesResponseMap.put(id, forDeletion)
    }}

    // Calculate total deletion counts
    DeletionCounts totals = new DeletionCounts(0,0,0,0)
    deleteResourcesResponseMap.keySet().forEach{String packageId -> {
      totals.pci += deleteResourcesResponseMap.get(packageId).statistics.pci
      totals.pti += deleteResourcesResponseMap.get(packageId).statistics.pti
      totals.ti += deleteResourcesResponseMap.get(packageId).statistics.ti
      totals.work += deleteResourcesResponseMap.get(packageId).statistics.work
    }}

    Map outputMap = [:]
    Map statisticsMap = [:]

    statisticsMap.put("total_markedForDeletion", totals)

    outputMap.put("packages", deleteResourcesResponseMap)
    outputMap.put("statistics", statisticsMap)

    return outputMap;
  }

  @CompileStatic(SKIP)
  public createDeleteResourcesJob(List<String> idInputs, ResourceDeletionJobType type) {
    ResourceDeletionJob job = new ResourceDeletionJob([
      name: "ResourceDeletionJob, package IDs: ${idInputs.toString()} ${Instant.now()}",
      packageIds: new JSON(idInputs).toString(),
      deletionJobType: type
    ])

    job.setStatusFromString('Queued')
    job.save(failOnError: true, flush: true)

    return job;
  }

  public DeleteResponse deleteResources(List<String> idInputs, Class<? extends ErmResource> resourceClass) {
    MarkForDeleteMap forDeletion = markForDelete(idInputs, resourceClass).resourceIds; // get ids marked for deletion.
    return  deleteResourcesInternal(forDeletion)
  }

  public Map deleteResourcesPkg(List<String> pkgIds) {
    Map outputMap = [:]

    // Each package is passed to deleteResources individually.
    pkgIds.forEach{ String id -> {
      outputMap.put(id, deleteResources([id], Pkg))
    }}

    // return a map of form: {pkgId1: {DeleteResponse}, pkdId2: {DeleteResponse}} or {}
    return outputMap;
  }

  // Helper method to get counts of resources from a resource ID map (MarkForDeleteMap)
  DeletionCounts getCountsFromDeletionMap(MarkForDeleteMap deleteMap) {
    return new DeletionCounts(deleteMap.pci.size(), deleteMap.pti.size(), deleteMap.ti.size(), deleteMap.work.size())
  }

  @CompileStatic(SKIP)
  @Transactional
  private DeleteResponse deleteResourcesInternal(MarkForDeleteMap resourcesToDelete) {
    DeleteResponse response = new DeleteResponse()

    if (resourcesToDelete == null) {
      log.warn("deleteResources called with null MarkForDeleteResponse")
      DeletionCounts emptyCount = new DeletionCounts(0, 0, 0, 0)
      response.markedForDeletion.statistics = emptyCount
      response.deleted.statistics = emptyCount
      return response
    }

    // Delete each type of resource.
    if (resourcesToDelete.pci && !resourcesToDelete.pci.isEmpty()) {
      response.deleted.resourceIds.pci = deleteIds(PackageContentItem, resourcesToDelete.pci)
    }

    if (resourcesToDelete.pti && !resourcesToDelete.pti.isEmpty()) {
      response.deleted.resourceIds.pti = deleteIds(PlatformTitleInstance, resourcesToDelete.pti)
    }

    if (resourcesToDelete.ti && !resourcesToDelete.ti.isEmpty()) {
      response.deleted.resourceIds.ti = deleteIds(TitleInstance, resourcesToDelete.ti)
    }

    if (resourcesToDelete.work && !resourcesToDelete.work.isEmpty()) {
      response.deleted.resourceIds.work = deleteIds(Work, resourcesToDelete.work)
    }

    log.info("Deletion complete.")
    response.markedForDeletion.statistics = getCountsFromDeletionMap(resourcesToDelete)
    response.deleted.statistics = getCountsFromDeletionMap(response.deleted.resourceIds)
    response.markedForDeletion.resourceIds = resourcesToDelete

    return response
  }
}

