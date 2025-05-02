package org.olf

import org.olf.erm.Entitlement
import org.olf.kb.Work

import java.util.stream.Collectors

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

  // TODO this is helpful but raw, potentially either comment out or refine
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

  // FIXME this is currently ONLY the mark for delete at the PCI level...
  // TODO should probs be Set in, what happens if a user passes input with duplicates and we cast to Set?
  public Map<String, Set<String>> markForDelete(List<String> ids) {
    log.info("LOG DEBUG - markForDelete({})", ids);

    Map<String, Set<String>> markForDeletion = new HashMap<>();
    markForDeletion.put('pci', new HashSet<String>());
    markForDeletion.put('pti', new HashSet<String>());
    markForDeletion.put('ti', new HashSet<String>());
    markForDeletion.put('work', new HashSet<String>());

    Set<String> tisMarkedForWorkChecking = new HashSet<String>();

    markForDeletion.get('pci').addAll(markForDeletePCI((Set) ids)); // FIXME with the above, this may be unnecessary, this feels gross
    log.info("LOG DEBUG - PCIs marked for deletion {}", markForDeletion.get("pci"))

    Set<String> ptisForDeleteCheck = PlatformTitleInstance.executeQuery(
      """
        SELECT DISTINCT pci.pti.id FROM PackageContentItem pci
        WHERE pci.id IN :pcisForDelete 
      """.toString(),
      [pcisForDelete:markForDeletion.get("pci")]
    ) as Set
    log.info("LOG DEBUG - PTIs for delete checking {}", ptisForDeleteCheck)

    // TODO consider case where user has sent PCI: [1,2,3], PTI: [4,5].
    // We could either run through PCIs to end of process then PTIs etc and use the Set to handle distinct
    // Or run _just_ PCI check, then add the PTI outcomes to the user ones and run all PTIs etc etc

    markForDeletion.get('pti').addAll(markForDeletePTI(ptisForDeleteCheck, markForDeletion.get('pci')));
    log.info("LOG DEBUG - PTIs marked for deletion {}", markForDeletion.get("pti"))

    // TODO Check zero-case

    Set<String> tisForDeleteCheck = TitleInstance.executeQuery(
      """
        SELECT DISTINCT pti.titleInstance.id FROM PlatformTitleInstance pti
        WHERE pti.id IN :ptisForDelete 
      """.toString(),
      [ptisForDelete:markForDeletion.get("pti")]
    ) as Set

    Tuple2<Set<String>, Set<String>> tisAndWorksForDeletion = markForDeleteTI(tisForDeleteCheck, markForDeletion.get("pti"));
    markForDeletion.get('ti').addAll(tisAndWorksForDeletion.v1);
    markForDeletion.get('work').addAll(tisAndWorksForDeletion.v2);

    log.info("LOG DEBUG markForDeletion - {}", markForDeletion);
    return markForDeletion
  }

  public Set<String> markForDeletePCI(Set<String> ids) {
    return ids
        .stream()
        .map( id -> {
          log.debug("LOG DEBUG CHECKING PCI ID: {}", id)
          // Find agreement lines for PCI.
          Set<String> linesForResource = entitlementsForResource(id, 1)
          log.debug("LOG DEBUG linesForResource: {}", linesForResource)

          // If no agreement lines exist for PCI, mark for deletion.
          if (linesForResource.size() != 0) {
            return null
          }

          return id;
        })
        .filter({id -> id != null })
        .collect(Collectors.toSet());
  }

  // ignorePcis is for any PCIs which would otherwise block PTI deletion but we can safely ignore and allow for delete
  public Set<String> markForDeletePTI(Set<String> ids, Set<String> ignorePcis) {
    return ids
      .stream()
      .map(id -> {
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
          [ptiId:id, ignorePcis:ignorePcis],
          [max:1]
        ) as Set

        // If no agreement lines and no other non-deletable PCIs exist for the PTI, mark for deletion.
        if (pcisForPti.size() != 0) {
          return null
        }

        return id;
      })
      .filter({id -> id != null })
      .collect(Collectors.toSet());
  }


  // Outputs a list of TIs to mark for deletion AND a list of works to mark for deletion
  public Tuple2<Set<String>, Set<String>> markForDeleteTI(Set<String> ids, Set<String> ignorePtis) {
    Set<String> worksToDelete = ids
      .stream()
      .map(id -> {
        // Find any other PTIs that have not been marked for deletion that exist for the TIs.
        Set<String> ptisForTi = PlatformTitleInstance.executeQuery("""
          SELECT pti.id FROM PlatformTitleInstance pti
          WHERE pti.titleInstance.id = :tiId
          AND pti.id NOT IN :ignorePtis
        """.toString(), [tiId:id, ignorePtis:ignorePtis], [max:1])  as Set

        if (ptisForTi.size() != 0) {
          log.info("LOG WARNING: PTIs that have not been marked for deletion exist for TI: TI ID- {}, PTIs found- {}", id, ptisForTi);
          return null
        }

        String workIdList = TitleInstance.executeQuery("""
          SELECT ti.work.id FROM TitleInstance ti
          WHERE ti.id = :tiId
        """.toString(), [tiId: id], [max: 1])

        String workId;

        if (workIdList.size() == 1) {
          workId = workIdList.get(0);
        } else {
          // No work ID exists or multiple works returned for one TI.
          return null;
        }

          Set<String> ptisForWork = Work.executeQuery("""
          SELECT pti from PlatformTitleInstance pti
          WHERE pti.id NOT IN :ignorePtis
          AND pti.titleInstance.work.id = :workId
        """.toString(), [ignorePtis: ignorePtis, workId: workId], [max:1]) as Set

        if (ptisForWork.size() != 0) {
          log.info("LOG WARNING: PTIs that have not been marked for deletion exist for work: Work ID- {}, PTIs found- {}", workId, ptisForWork);
          return null
        }

        return workId // FIXME THEN ADD tisForWork
      })
      .filter({id -> id != null })
      .collect(Collectors.toSet());

    Set<String> tisToDelete = worksToDelete
      .stream()
      .flatMap(id -> {
        Set<String> tisForWork = Work.executeQuery("""
          SELECT ti.id from TitleInstance ti
          WHERE ti.work.id = :workId
        """.toString(), [workId:id]) as Set

        return tisForWork.stream()
      })
      .collect(Collectors.toSet());

    return Tuple.tuple(tisToDelete, worksToDelete);
  }
}

