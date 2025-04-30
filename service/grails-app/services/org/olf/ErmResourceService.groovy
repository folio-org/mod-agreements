package org.olf

import org.olf.erm.Entitlement
import org.olf.kb.Work

import static org.springframework.transaction.annotation.Propagation.MANDATORY

import org.olf.dataimport.internal.PackageSchema.ContentItemSchema

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

  /* This method takes in an ErmResource id, and walks up the heirachy of specificity
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

  String visualizePciHierarchy(String pciId, String initialIndent = "") {

    PackageContentItem pci = PackageContentItem.executeQuery("""
        SELECT pci FROM PackageContentItem pci
        WHERE pci.id = :pciId
      """.toString(), [pciId:pciId], [max:1]).get(0);

    if (!pci) {
      return "${initialIndent}Error: PCI ID cannot be null or empty."
    }

    StringBuilder output = new StringBuilder()

    try {

      if (!pci) {
        return "${initialIndent}PCI with ID ${pci.id} not found."
      }

      // Start building the tree string
      output.append(String.format("\n%sPCI: %s%n", initialIndent, pci.id)) // Use %n for platform-independent newline

      // Safely access linked objects using ?. (safe navigation)
      PlatformTitleInstance pti = pci?.pti
      output.append(String.format("%s  -> PTI: %s%n", initialIndent, pti?.id ?: "[Not Set or Not Loaded]"))

      TitleInstance ti = pti?.titleInstance
      output.append(String.format("%s     -> TI: %s%n", initialIndent, ti?.id ?: "[Not Set or Not Loaded]"))

      Work work = ti?.work
      output.append(String.format("%s        -> Work: %s%n", initialIndent, work?.id ?: "[Not Set or Not Loaded]"))

    } catch (Exception e) {
      // Catch potential exceptions during lookup or access
      log.error("Error visualizing hierarchy for PCI ID {}: {}", pci.id, e.message, e)
      output.append(String.format("%sError generating hierarchy for PCI ID %s: %s%n", initialIndent, pci.id, e.message))
    }

    return output.toString()
  }

  String visualizeWorkHierarchy(String workId) {
    if (!workId) {
      return "Error: Work ID cannot be null or empty."
    }

    StringBuilder output = new StringBuilder()
    Work work = null

    try {
      work = Work.executeQuery("""
        SELECT work FROM Work work
        WHERE work.id = :workId
      """.toString(), [workId:workId], [max:1]).get(0);

      output.append(String.format("\nWork: %s%n", work.id))

      // 2. Start the recursive process to find TIs linked to this Work
      findAndAppendChildrenRecursive(work.id, "Work", output, "  ")

    } catch (Exception e) {
      log.error("Error visualizing hierarchy for Work ID {}: {}", workId, e.message, e)
      output.append(String.format("Error generating hierarchy for Work ID %s: %s%n", workId, e.message))
    }

    return output.toString()
  }

  @CompileStatic
  private void findAndAppendChildrenRecursive(String parentId, String parentType, StringBuilder output, String indent) {

    List childrenResult = [] // Use a generic list initially
    String childType = ""
    String hql = ""
    Map params = [parentId: parentId]

    switch (parentType) {
      case "Work":
        hql = "SELECT ti FROM TitleInstance ti WHERE ti.work.id = :parentId"
        childType = "TI"
        break
      case "TI":
        hql = "SELECT pti FROM PlatformTitleInstance pti WHERE pti.titleInstance.id = :parentId"
        childType = "PTI"
        break
      case "PTI":
        hql = "SELECT pci FROM PackageContentItem pci WHERE pci.pti.id = :parentId"
        childType = "PCI"
        break
      default:
        log.warn("Unknown parent type encountered in hierarchy visualization: {}", parentType)
        return
    }

    // Execute the query within the tenant context
    childrenResult = (List) TitleInstance.executeQuery(hql, params) // Cast result to List just to be safe

    // Process each child found, providing type hints/casts for @CompileStatic
    childrenResult.each { Object child -> // Iterate receiving Object

      String currentChildId = null // Variable to hold the ID safely

      // *** Add Type Checks and Casts ***
      if (childType == "TI" && child instanceof TitleInstance) {
        TitleInstance ti = (TitleInstance) child // Cast
        currentChildId = ti.id
        output.append(String.format("%s%s: %s%n", indent, childType, currentChildId))
        findAndAppendChildrenRecursive(currentChildId, childType, output, indent + "  ")
      } else if (childType == "PTI" && child instanceof PlatformTitleInstance) {
        PlatformTitleInstance pti = (PlatformTitleInstance) child // Cast
        currentChildId = pti.id
        output.append(String.format("%s%s: %s%n", indent, childType, currentChildId))
        findAndAppendChildrenRecursive(currentChildId, childType, output, indent + "  ")
      } else if (childType == "PCI" && child instanceof PackageContentItem) {
        PackageContentItem pci = (PackageContentItem) child // Cast
        currentChildId = pci.id
        output.append(String.format("%s%s: %s%n", indent, childType, currentChildId))
        // No recursive call for PCI (leaf node)
      } else {
        // Log a warning if the type doesn't match expectations
        log.warn("Unexpected object type found in children list for parentType {}. Expected {}, Found: {}",
            parentType, childType, (child ? child.getClass().name : 'null'))
        output.append(String.format("%s%s: [Unexpected Type: %s]%n", indent, childType, (child ? child.getClass().simpleName : 'null')))
      }
    }
  }

  def heirarchicalDeletePCI(List<String> ids) {
    Map<String, List<String>> markForDeletion = new HashMap<>();
    markForDeletion.put('pci', new ArrayList<>());
    markForDeletion.put('pti', new ArrayList<>());
    markForDeletion.put('ti', new ArrayList<>());
    markForDeletion.put('work', new ArrayList<>());

    List<String> tisMarkedForWorkChecking = new ArrayList<>();

    ids.forEach { String id -> {
      // Find agreement lines for PCI.
      List<String> linesForResource = Entitlement.executeQuery(
          """
                SELECT ent.id FROM Entitlement ent
                WHERE ent.resource.id = :resId
                """.toString(), [resId:id], [max:1])

      // If no agreement lines exist for PCI, mark for deletion.
      if (linesForResource.size() == 0) {
        markForDeletion.get('pci').add(id);
      }
    }}

    // Check zero-case
    markForDeletion.get("pci").forEach{ String id -> {
      // Find PTIs for PCI.
      PlatformTitleInstance.executeQuery("""
        SELECT pci.pti.id FROM PackageContentItem pci
        WHERE pci.id = :pciId
        AND pci.pti.id NOT IN :ptiIdsForDeletion
      """.toString(), [pciId:id, ptiIdsForDeletion:markForDeletion.get("pti")]).forEach{ String ptiId -> {
        // Find agreement lines for PTI.
        List<String> linesForResource = Entitlement.executeQuery(
            """
                SELECT ent.id FROM Entitlement ent
                WHERE ent.resource.id = :resId
                """.toString(), [resId:ptiId], [max:1])

        // Find any other PCIs that have not been marked for deletion that exist for the PTI.
        List<String> pcisForPti = PackageContentItem.executeQuery("""
          SELECT pci.id FROM PackageContentItem pci
          WHERE pci.pti.id = :ptiId
          AND pci.id NOT IN :pcisForDeletion
        """.toString(), [ptiId:ptiId, pcisForDeletion:markForDeletion.get("pci")], [max:1])

        // If no agreement lines and no other non-deletable PCIs exist for the PTI, mark for deletion.
        if (linesForResource.size() == 0 && pcisForPti.size() == 0) {
          markForDeletion.get('pti').add(ptiId);
        }
      }}
    }}

    markForDeletion.get("pti").forEach{ String ptiId -> {
      // Find TIs that belongs to the PTI marked for deletion.
      TitleInstance.executeQuery("""
        SELECT pti.titleInstance.id from PlatformTitleInstance pti
        WHERE pti.id = :ptiId
      """.toString(), [ptiId:ptiId]).forEach{ String tiId -> {
        log.info("LOG DEBUG tis found - {}", tiId);

        // Find any other PTIs that have not been marked for deletion that exist for the TIs.
        List<String> ptisForTi = PlatformTitleInstance.executeQuery("""
          SELECT pti.id FROM PlatformTitleInstance pti
          WHERE pti.titleInstance.id = :tiId
          AND pti.id NOT IN :ptisForDeletion
        """.toString(), [tiId:tiId, ptisForDeletion:markForDeletion.get("pti")], [max:1])

        if (ptisForTi.size() == 0) {
          tisMarkedForWorkChecking.add(tiId);
        }
      }}
    }}

    // Work Checking
    tisMarkedForWorkChecking.forEach{ String tiId -> {

      String workId = TitleInstance.executeQuery("""
        SELECT ti.work.id FROM TitleInstance ti
        WHERE ti.id = :tiId
      """.toString(), [tiId: tiId])[0] //FIXME WHAT IF I DON@T EXIST


      List<String> tisForWork = Work.executeQuery("""
          SELECT ti.id from TitleInstance ti
          WHERE ti.work.id = :workId
        """.toString(), [workId:workId])

      List<String> ptisForWork = Work.executeQuery("""
        SELECT pti from PlatformTitleInstance pti
        WHERE pti.id NOT IN :ptisForDeletion
        AND pti.titleInstance.id in :tisForWork
      """.toString(), [ptisForDeletion: markForDeletion.get("pti"), tisForWork: tisForWork], [max:1])

      // Do we want to log when a work can't be deleted because PTIs exist? E.g.
//        log.info("LOG WARNING work could not be deleted: Work ID- {}, PTIs for Work- {}", workId, ptisForWork);

      if (ptisForWork.size() == 0) {
        markForDeletion.get('work').add(workId);
        markForDeletion.get('ti').addAll(tisForWork);
      }
    }}

    log.info("LOG DEBUG markForDeletion - {}", markForDeletion);
    return markForDeletion
  }
}

