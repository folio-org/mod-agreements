package org.olf

import org.olf.erm.Entitlement
import org.olf.kb.Work
import org.olf.kb.http.response.DeleteResponse
import org.olf.kb.http.response.DeletionCounts
import org.olf.kb.http.response.MarkForDeleteResponse
import org.olf.kb.Pkg

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

  public Set<String> entitlementsForResource(String resourceId, int max) {
    Map options = [:]
    if (max) {
      options.max = max
    }
    return Entitlement.executeQuery(
      """
            SELECT ent.id FROM Entitlement ent
            WHERE ent.resource.id = :resId
          """.toString(),
      [resId:resourceId],
      options
    ) as Set
  }

  private Set<String> handleEmptyListMapping(Set<String> resourceSet) {
    // Workaround for HQL 'NOT IN' bug: https://stackoverflow.com/questions/36879116/hibernate-hql-not-in-clause-doesnt-seem-to-work
    return (resourceSet.size() == 0 ? ["PLACEHOLDER_RESOURCE"] : resourceSet) as Set<String>
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
        return markForDeleteInternal(new HashSet<String>(idInputs), new HashSet<String>(), new HashSet<String>())
        break;
      case PlatformTitleInstance.class:
        return markForDeleteInternal(new HashSet<String>(), new HashSet<String>(idInputs), new HashSet<String>())
        break;
      case TitleInstance.class:
        return markForDeleteInternal(new HashSet<String>(), new HashSet<String>(), new HashSet<String>(idInputs))
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

    // Check that ids actually exist and log/ignore any that don't
    pciIds = pciIds.isEmpty() ? [] as Set<String> : PackageContentItem.executeQuery("select p.id from PackageContentItem p where p.id in :ids", [ids: pciIds]) as Set<String>
    ptiIds = ptiIds.isEmpty() ? [] as Set<String> :PlatformTitleInstance.executeQuery("select p.id from PlatformTitleInstance p where p.id in :ids", [ids: ptiIds]) as Set<String>
    tiIds = tiIds.isEmpty() ? [] as Set<String> : TitleInstance.executeQuery("select t.id from TitleInstance t where t.id in :ids", [ids: tiIds]) as Set<String>

    if (pciIds.isEmpty() && ptiIds.isEmpty() && tiIds.isEmpty()) {
      log.warn("No ids found after filtering for existing ids.")
    }

    // PCI Level checking -- only need to test whether it has any AgreementLines
    Set<String> pcisWithEntitlements = Entitlement.executeQuery(
      "select distinct ent.resource.id from Entitlement ent where ent.resource.id in :pciIds",
      [pciIds: pciIds]
    ) as Set<String>
    // PCIs to delete are those that exist but are NOT in the set with entitlements.
    markForDeletion.pci.addAll(pciIds - pcisWithEntitlements)


    // Find all the PTIs in the system for PCIs we've decided to delete
    Set<String> ptisForDeleteCheck = PlatformTitleInstance.executeQuery(
      """
        SELECT pci.pti.id FROM PackageContentItem pci
        WHERE pci.id IN :pcisForDelete 
      """.toString(),
      [pcisForDelete:markForDeletion.pci]
    ) as Set<String>

    ptiIds.addAll(ptisForDeleteCheck);

    // PTIs are valid to delete if there are no AgreementLines for them AND they have no not-for-delete PCIs

    Set<String> ptisWithEntitlements = Entitlement.executeQuery(
      "select distinct ent.resource.id from Entitlement ent where ent.resource.id in :ptiIds",
      [ptiIds: ptisForDeleteCheck]
    ) as Set<String>

    Set<String> ptisWithRemainingPcis = PlatformTitleInstance.executeQuery(
      """
            select distinct pci.pti.id from PackageContentItem pci
            where pci.pti.id in :ptiIds and pci.id not in :deletablePcis
            """,
      [ptiIds: ptisForDeleteCheck, deletablePcis: handleEmptyListMapping(markForDeletion.pci)]
    ) as Set<String>

    Set<String> nonDeletablePtis = ptisWithEntitlements + ptisWithRemainingPcis
    markForDeletion.pti.addAll(ptisForDeleteCheck - nonDeletablePtis)

    // Find all TIs for PTIs we've marked for deletion and mark for "work checking"
    Set<String> allTisForPtis = TitleInstance.executeQuery(
      """
        SELECT pti.titleInstance.id FROM PlatformTitleInstance pti
        WHERE pti.id IN :ptisForDelete 
      """.toString(),
      [ptisForDelete:markForDeletion.pti]
    ) as Set<String>
    allTisForPtis.addAll(tiIds)

    // It is valid to delete a Work and ALL attached TIs if none of the TIs have a PTI that is not marked for deletion
    Set<String> tisWithRemainingPtis = new HashSet<String>();
    tisWithRemainingPtis = PlatformTitleInstance.executeQuery("""
          SELECT pti.titleInstance.id FROM PlatformTitleInstance pti
          WHERE pti.titleInstance.id IN :tiIds
          AND pti.id NOT IN :ignorePtis
        """.toString(), [tiIds:allTisForPtis, ignorePtis:handleEmptyListMapping(markForDeletion.pti)], [max:1])  as Set<String>
    Set<String> tisForWorkChecking = allTisForPtis - tisWithRemainingPtis

    List<String> workIdList = TitleInstance.executeQuery(
      "select ti.work.id from TitleInstance ti where ti.id in :tiIds",
      [tiIds: tisForWorkChecking]
    )

    Set<String> workIdsToCheck = workIdList.unique() as Set<String>
    Set<String> worksWithRemainingPtis = Work.executeQuery(
      """
            select distinct pti.titleInstance.work.id from PlatformTitleInstance pti
            where pti.titleInstance.work.id in :workIds and pti.id not in :deletablePtis
            """,
      [workIds: workIdsToCheck, deletablePtis: handleEmptyListMapping(markForDeletion.pti)]
    ) as Set<String>

    markForDeletion.work.addAll(workIdsToCheck - worksWithRemainingPtis)

    if (!markForDeletion.work.isEmpty()) {
      Set<String> tisForDeletableWorks = TitleInstance.executeQuery(
        "select ti.id from TitleInstance ti where ti.work.id in :workIds",
        [workIds: markForDeletion.work]
      ) as Set<String>
      markForDeletion.ti.addAll(tisForDeletableWorks)
    }

    log.info("Marked resources for delete completed: {} PCIs, {} PTIs, {} TIs, {} Works",
      markForDeletion.pci.size(), markForDeletion.pti.size(), markForDeletion.ti.size(), markForDeletion.work.size())

    return markForDeletion
  }

  @CompileStatic(SKIP)
  private Set<String> deleteByIds(Class domainClass, Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return new HashSet<String>()
    }

    Set<String> successfullyDeletedIds = new HashSet<>()

    ids.each { id ->
      def instance = domainClass.get(id)

      if (instance) {
        try {
          instance.delete()
          successfullyDeletedIds.add(id)
          log.trace("Successfully deleted id: {}", id)
        } catch (Exception e) {
          log.error("Failed to delete id {}: {}", id, e.message, e)
        }
      } else {
        log.warn("{} id {} not found for deletion.", domainClass.simpleName, id)
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

  @Transactional
  private DeleteResponse deleteResourcesInternal(MarkForDeleteResponse resourcesToDelete) {
    DeleteResponse response = new DeleteResponse()
    MarkForDeleteResponse deletedIds = new MarkForDeleteResponse(); // Track deleted Ids

    if (resourcesToDelete == null) {
      log.warn("deleteResources called with null MarkForDeleteResponse")
      DeletionCounts emptyCount = new DeletionCounts(0, 0, 0, 0);
      response.statistics = emptyCount;
      return response;
    }

    log.info("Attempting to delete resources: {}", resourcesToDelete)
    DeletionCounts deletionCounts = new DeletionCounts();

    if (resourcesToDelete.pci && !resourcesToDelete.pci.isEmpty()) {
      log.debug("Deleting PCIs: {}", resourcesToDelete.pci)
      deletedIds.pci = deleteByIds(PackageContentItem, resourcesToDelete.pci)
    }
    deletionCounts.pciDeleted =  deletedIds.pci.size()

    if (resourcesToDelete.pti && !resourcesToDelete.pti.isEmpty()) {
      log.debug("Deleting PTIs: {}", resourcesToDelete.pti)
      deletedIds.pti = deleteByIds(PlatformTitleInstance, resourcesToDelete.pti)
    }
    deletionCounts.ptiDeleted = deletedIds.pti.size()

    List<String> tiAndWorkIds = new ArrayList<>()
    tiAndWorkIds.addAll(resourcesToDelete.ti)
    tiAndWorkIds.addAll(resourcesToDelete.work)

    if (resourcesToDelete.ti && !resourcesToDelete.ti.isEmpty()) {
      log.debug("Deleting TIs: {}", resourcesToDelete.ti)
      deletedIds.ti = deleteByIds(TitleInstance, resourcesToDelete.ti)
    }
    deletionCounts.tiDeleted = deletedIds.ti.size()

    if (resourcesToDelete.work && !resourcesToDelete.work.isEmpty()) {
      log.debug("Deleting Works: {}", resourcesToDelete.work)
      deletedIds.work = deleteByIds(Work, resourcesToDelete.work)
    }
    deletionCounts.workDeleted = deletedIds.work.size()

    log.info("Deletion complete. Counts: {}", deletionCounts)
    response.statistics = deletionCounts
    response.deletedIds = deletedIds
    return response;
  }
}

