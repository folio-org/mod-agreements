package org.olf

import org.olf.erm.Entitlement
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.Work
import org.olf.kb.http.response.DeleteResponse
import org.olf.kb.http.response.DeletionCounts
import org.olf.kb.http.response.MarkForDeleteResponse

import static org.springframework.transaction.annotation.Propagation.MANDATORY

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
    //}

    resourceList
  }

  public boolean checkResourceExists(String resourceId, String resourceType) {
    List<String> resourceExists = ErmResource.executeQuery(
      """
            SELECT id FROM ${resourceType}
            WHERE id = :resourceId
          """.toString(),
      [resourceId:resourceId],
      [max:1]
    )


    if (resourceExists != null && !resourceExists.isEmpty()) {
      log.info("LOG DEBUG - RESOURCE EXISTS: ${resourceId} for type ${resourceType}. ${resourceExists}")
      return true
    } else {
      log.info("LOG DEBUG - RESOURCE DOES NOT EXIST: ${resourceId} for type ${resourceType}. ${resourceExists}")
      return false
    }
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

  public MarkForDeleteResponse markForDelete(List<String> idInputs, Class<? extends ErmResource> resourceClass) {
    switch (resourceClass) {
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
    MarkForDeleteResponse markForDeletion = new MarkForDeleteResponse()

    // Check that ids actually exist and log/ignore any that don't
    pciIds = pciIds.findAll{String id -> {checkResourceExists(id, "PackageContentItem")}}
    ptiIds.findAll{String id -> {checkResourceExists(id, "PlatformTitleInstance")}}
    tiIds.findAll{String id -> {checkResourceExists(id, "TitleInstance")}}

    // PCI Level checking -- only need to test whether it has any AgreementLines
    pciIds.forEach{String id -> {
      Set<String> linesForResource = entitlementsForResource(id, 1)
      if (linesForResource.size() == 0) {
        markForDeletion.pci.add(id);
      }
    }}

    // Find all the PTIs in the system for PCIs we've decided to delete
    Set<String> ptisForDeleteCheck = PlatformTitleInstance.executeQuery(
      """
        SELECT pci.pti.id FROM PackageContentItem pci
        WHERE pci.id IN :pcisForDelete 
      """.toString(),
      [pcisForDelete:markForDeletion.pci]
    ) as Set

    ptiIds.addAll(ptisForDeleteCheck);

    // PTIs are valid to delete if there are no AgreementLines for them AND they have no not-for-delete PCIs
    ptiIds.forEach{ String id -> {
      Set<String> linesForResource = entitlementsForResource(id, 1)
      if (linesForResource.size() != 0) {
        return null;
      }

      // Find any other PCIs that have not been marked for deletion that exist for the PTI.
      Set<String> pcisForPti = PackageContentItem.executeQuery(
        """
            SELECT pci.id FROM PackageContentItem pci
            WHERE pci.pti.id = :ptiId
            AND pci.id NOT IN :ignorePcis
          """.toString(),
        [ptiId:id, ignorePcis:handleEmptyListMapping(markForDeletion.pci)],
        [max:1]
      ) as Set

      // If no agreement lines and no other non-deletable PCIs exist for the PTI, mark for deletion.
      if (pcisForPti.size() != 0) {
        return null
      }

      markForDeletion.pti.add(id);
    }}

    // Find all TIs for PTIs we've marked for deletion and mark for "work checking"
    Set<String> allTisForPtis = TitleInstance.executeQuery(
      """
        SELECT pti.titleInstance.id FROM PlatformTitleInstance pti
        WHERE pti.id IN :ptisForDelete 
      """.toString(),
      [ptisForDelete:markForDeletion.pti]
    ) as Set
    allTisForPtis.addAll(tiIds)

    // It is valid to delete a Work and ALL attached TIs if none of the TIs have a PTI that is not marked for deletion
    Set<String> tisForWorkChecking = new HashSet<String>();
    allTisForPtis.forEach{ String id -> {
      Set<String> ptisForTi = PlatformTitleInstance.executeQuery("""
          SELECT pti.id FROM PlatformTitleInstance pti
          WHERE pti.titleInstance.id = :tiId
          AND pti.id NOT IN :ignorePtis
        """.toString(), [tiId:id, ignorePtis:handleEmptyListMapping(markForDeletion.pti)], [max:1])  as Set

      if (ptisForTi.size() != 0) {
        log.info("LOG WARNING: PTIs that have not been marked for deletion exist for TI: TI ID- {}, PTIs found- {}", id, ptisForTi);
        return null
      }
      tisForWorkChecking.add(id);
    }}

    tisForWorkChecking.forEach{String id -> {
      List<String> workIdList = TitleInstance.executeQuery("""
        SELECT ti.work.id FROM TitleInstance ti
        WHERE ti.id = :tiId
      """.toString(), [tiId: id], [max: 1])

      String workId;

      if (workIdList.size() == 1) {
        workId = workIdList.get(0);
        log.info("Work Id found for deletion: {}", workId)
      } else {
        // No work ID exists or multiple works returned for one TI.
        return null;
      }

      Set<String> ptisForWork = Work.executeQuery("""
          SELECT pti from PlatformTitleInstance pti
          WHERE pti.id NOT IN :ignorePtis
          AND pti.titleInstance.work.id = :workId
        """.toString(), [ignorePtis:handleEmptyListMapping(markForDeletion.pti), workId: workId], [max:1]) as Set

      log.info("Ptis for Work: {}", ptisForWork.toListString())

      if (ptisForWork.size() != 0) {
        log.info("LOG WARNING: PTIs that have not been marked for deletion exist for work: Work ID- {}, PTIs found- {}", workId, ptisForWork);
        return null
      }

      markForDeletion.work.add(workId)
    }}

    markForDeletion.work.forEach{String id -> {
        Set<String> tisForWork = Work.executeQuery("""
          SELECT ti.id from TitleInstance ti
          WHERE ti.work.id = :workId
        """.toString(), [workId:id]) as Set

        // If we can delete a work, delete any other TIs attached to it
      markForDeletion.ti.addAll(tisForWork)
      }}

    return markForDeletion
  }

  private int deleteByIds(Class domainClass, Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0
    }

    // TODO: Or should we be using .delete()?
    String hql = "DELETE FROM ${domainClass.simpleName} WHERE id IN (:idsToDelete)"
    return ErmResource.executeUpdate(hql, [idsToDelete: new ArrayList<>(ids)])
  }

  // FIXME can we have a DeleteResponse similar to the MarkForDeleteResponse please? :)
  public DeleteResponse deleteResources(List<String> idInputs, Class<? extends ErmResource> resourceClass) {
    MarkForDeleteResponse forDeletion = markForDelete(idInputs, resourceClass);
    log.info("Marked resources for delete: {}, continuing to delete", forDeletion)

    return deleteResourcesInternal(forDeletion);
  }

  @Transactional
  private DeleteResponse deleteResourcesInternal(MarkForDeleteResponse resourcesToDelete) {
    DeleteResponse response = new DeleteResponse()

    if (resourcesToDelete == null) {
      log.warn("deleteResources called with null MarkForDeleteResponse")
      DeletionCounts emptyCount = new DeletionCounts(0, 0, 0, 0);
      response.statistics = emptyCount;
      return response;
    }

    log.info("Attempting to delete resources: {}", resourcesToDelete)
    DeletionCounts deletionCounts = new DeletionCounts();

    int pciDeletedCount = 0
    if (resourcesToDelete.pci && !resourcesToDelete.pci.isEmpty()) {
      log.info("Deleting PCIs: {}", resourcesToDelete.pci)
      pciDeletedCount = deleteByIds(PackageContentItem, resourcesToDelete.pci)
      log.info("Deleted {} PCIs", pciDeletedCount)
    }
    deletionCounts.pciDeleted = pciDeletedCount

    int ptiDeletedCount = 0
    if (resourcesToDelete.pti && !resourcesToDelete.pti.isEmpty()) {
      log.info("Deleting PTIs: {}", resourcesToDelete.pti)
      ptiDeletedCount = deleteByIds(PlatformTitleInstance, resourcesToDelete.pti)
      log.info("Deleted {} PTIs", ptiDeletedCount)
    }
    deletionCounts.ptiDeleted = ptiDeletedCount

    List<String> tiAndWorkIds = new ArrayList<>()
    tiAndWorkIds.addAll(resourcesToDelete.ti)
    tiAndWorkIds.addAll(resourcesToDelete.work)

    // DELETE THE IdentifierOccurrences
    List<String> ioIdsToDelete = IdentifierOccurrence.executeQuery("""
      SELECT io.id FROM IdentifierOccurrence io
      WHERE io.resource.id IN (:ermTitleListIds)
    """, [ermTitleListIds: tiAndWorkIds])

    deleteByIds(IdentifierOccurrence, ioIdsToDelete);

    int tiDeletedCount = 0
    if (resourcesToDelete.ti && !resourcesToDelete.ti.isEmpty()) {
      log.info("Deleting TIs: {}", resourcesToDelete.ti)
      tiDeletedCount = deleteByIds(TitleInstance, resourcesToDelete.ti)
      log.info("Deleted {} TIs", tiDeletedCount)
    }
    deletionCounts.tiDeleted = tiDeletedCount

    int workDeletedCount = 0
    if (resourcesToDelete.work && !resourcesToDelete.work.isEmpty()) {
      log.info("Deleting Works: {}", resourcesToDelete.work)
      workDeletedCount = deleteByIds(Work, resourcesToDelete.work)
      log.info("Deleted {} Works", workDeletedCount)
    }
    deletionCounts.workDeleted = workDeletedCount

    log.info("Deletion complete. Counts: {}", deletionCounts)
    response.statistics = deletionCounts
    return response;
  }
}

