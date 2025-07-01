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

        // Create temp tables: the "initial" tables store any ids passed directly into the method (the starting ids)
        // the "canditiate" tables store those that we start each step with (before checks) e.g. the PTI ids belonging to deletable PCIs.
        // the "deletable" tables are those that contain the final IDs that are safe for deletion.
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_initial_pcis (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_initial_ptis (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_initial_tis (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_deletable_pcis (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_candidate_ptis (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_deletable_ptis (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_candidate_tis (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_candidate_works (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_deletable_works (id VARCHAR(255) PRIMARY KEY)")
        sql.execute("CREATE TEMP TABLE IF NOT EXISTS temp_deletable_tis (id VARCHAR(255) PRIMARY KEY)")

        sql.execute("TRUNCATE TABLE temp_initial_pcis, temp_initial_ptis, temp_initial_tis, temp_deletable_pcis, temp_candidate_ptis, temp_deletable_ptis, temp_candidate_tis, temp_candidate_works, temp_deletable_works, temp_deletable_tis")

        // Populate initial ID tables from the input sets
        populateTempTable(sql, 'temp_initial_pcis', pciIds)
        populateTempTable(sql, 'temp_initial_ptis', ptiIds)
        populateTempTable(sql, 'temp_initial_tis', tiIds)

        log.info("Starting markForDelete() using pcis: {}, ptis: {}, tis: {}", pciIds.size(), ptiIds.size(), tiIds.size())

        // Populate the table which contains the PCIs that don't have entitlements
        sql.execute("""
                INSERT INTO temp_deletable_pcis (id)
                SELECT p.id FROM temp_initial_pcis p
                LEFT JOIN entitlement ent ON p.id = ent.ent_resource_fk
                WHERE ent.ent_resource_fk IS NULL
            """)

        // Mark PTIs for Delete
        // Adds the initial set of PTI ids (if any)
        sql.execute("""INSERT INTO temp_candidate_ptis (id) SELECT id FROM temp_initial_ptis ON CONFLICT DO NOTHING""")

        // Inserts PTIs linked to deletable PCIs.
        sql.execute("""
                INSERT INTO temp_candidate_ptis (id)
                SELECT DISTINCT pci.pci_pti_fk
                FROM package_content_item pci
                INNER JOIN temp_deletable_pcis d ON pci.id = d.id
                ON CONFLICT DO NOTHING;
            """)

        // A candidate PTI can be deleted if it has no entitlements AND it is not linked to any PCI that is not being deleted.
        sql.execute("""
                INSERT INTO temp_deletable_ptis (id)
                SELECT c.id FROM temp_candidate_ptis c
                WHERE
                  -- Condition 1: The PTI itself is not an entitled resource
                  NOT EXISTS (SELECT 1 FROM entitlement ent WHERE ent.ent_resource_fk = c.id)
                  AND
                  -- Condition 2: The PTI is not linked to any PCI that is NOT in our deletable list
                  NOT EXISTS (
                    SELECT 1 FROM package_content_item pci
                    WHERE pci.pci_pti_fk = c.id
                    AND pci.id NOT IN (SELECT id FROM temp_deletable_pcis)
                  )
            """)

        // 4. Mark TIs and Works for Delete
        // First, determine candidate TIs: those from the initial set plus those linked to deletable PTIs.
        sql.execute("""INSERT INTO temp_candidate_tis (id) SELECT id FROM temp_initial_tis ON CONFLICT DO NOTHING""")
        sql.execute("""
                INSERT INTO temp_candidate_tis (id)
                SELECT DISTINCT pti.pti_ti_fk
                FROM platform_title_instance pti
                INNER JOIN temp_deletable_ptis d ON pti.id = d.id
                ON CONFLICT DO NOTHING;
            """)

        // Find all Works linked to our deletable TIs
        // Create a table that contains works linked to deletable TIs
        sql.execute("""
                INSERT INTO temp_candidate_works (id)
                SELECT DISTINCT ti.ti_work_fk
                FROM title_instance ti
                INNER JOIN temp_candidate_tis c_ti ON ti.id = c_ti.id;
            """)

        // A Work can be deleted if it is not referenced by any PTI that is not being deleted.
        // Use the not exists clause to say get me the deletable works that aren't linked to a PTI that isn't in temp_deletable_ptis
        sql.execute("""
                INSERT INTO temp_deletable_works (id)
                SELECT c.id FROM temp_candidate_works c
                WHERE
                  NOT EXISTS (
                    SELECT 1
                    FROM title_instance ti
                    INNER JOIN platform_title_instance pti ON ti.id = pti.pti_ti_fk
                    WHERE ti.ti_work_fk = c.id
                    AND pti.id NOT IN (SELECT id FROM temp_deletable_ptis)
                  )
            """)

        // Finally, all TIs that reference a deletable Work can be marked for deletion.
        sql.execute("""
                INSERT INTO temp_deletable_tis (id)
                SELECT ti.id
                FROM title_instance ti
                INNER JOIN temp_deletable_works dw ON ti.ti_work_fk = dw.id;
            """)


        // Populate response using ids from temp tables
        def finalPciIds = fetchIds(sql, "SELECT id FROM temp_deletable_pcis")
        def finalPtiIds = fetchIds(sql, "SELECT id FROM temp_deletable_ptis")
        def finalTiIds = fetchIds(sql, "SELECT id FROM temp_deletable_tis")
        def finalWorkIds = fetchIds(sql, "SELECT id FROM temp_deletable_works")

        response.resourceIds.pci.addAll(finalPciIds)
        response.resourceIds.pti.addAll(finalPtiIds)
        response.resourceIds.ti.addAll(finalTiIds)
        response.resourceIds.work.addAll(finalWorkIds)

        response.statistics = getCountsFromDeletionMap(response.resourceIds)

        log.info("Mark for delete finished. PCIs: {}, PTIs: {}, TIs: {}, Works: {}",
          response.resourceIds.pci.size(),
          response.resourceIds.pti.size(),
          response.resourceIds.ti.size(),
          response.resourceIds.work.size())
      }
    }
    return response
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
      name: "ResourceDeletionJob, resource IDs: ${idInputs.toString()} ${Instant.now()}",
      resourceInputs: new JSON(idInputs).toString(),
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

