package org.olf

import grails.gorm.services.Service
import groovy.util.logging.Slf4j
import org.olf.erm.Entitlement
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Work

@Slf4j
@Service
class PackageContentItemDeletionService {

  PackageContentItemDeletionService() {
  }


//    List<Entitlement> linesForResource = Entitlement.executeQuery("""
//        SELECT ent FROM Entitlement ent
//        WHERE ent.resource.id = :resId
//        LIMIT 1
//        """.toString(), [resId:id])

  def heirarchicalDeletePCI(List<String> ids) {
    Map<String, List<String>> markForDeletion = new HashMap<>();
    List<String> pcisForDeletion = new ArrayList<>();

    ids.forEach { String id -> {
      // Find agreement lines for PCI.
      List<String> linesForResource = Entitlement.executeQuery(
          """
                SELECT ent.id FROM Entitlement ent
                WHERE ent.resource.id = :resId
                """.toString(), [resId:id], [max:1])

      // If no agreement lines exist for PCI, mark for deletion.
      if (linesForResource.size() == 0) {
        pcisForDeletion.add(id);
      };


    }}
    markForDeletion.put("PCIs", pcisForDeletion);

    // Check zero-case
    List<String> ptisForDeletion = new ArrayList<>();
    markForDeletion.get("PCIs").forEach{ String id -> {
      // Find PTIs for PCI.
      PlatformTitleInstance.executeQuery("""
        SELECT pci.pti.id FROM PackageContentItem pci
        WHERE pci.id = :pciId
      """.toString(), [pciId:id]).forEach{ String ptiId -> {
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
        """.toString(), [ptiId:ptiId, pcisForDeletion:markForDeletion.get("PCIs")], [max:1])

        // If no agreement lines and no other non-deletable PCIs exist for the PTI, mark for deletion.
        if (linesForResource.size() == 0 && pcisForPti.size() == 0) {
          ptisForDeletion.add(ptiId);
        }
      }}
    }}

    markForDeletion.put("PTIs", ptisForDeletion);

    List<String> tisForDeletion = new ArrayList<>();
    markForDeletion.get("PTIs").forEach{ String ptiId -> {
      // Find TI that belongs to the PTI marked for deletion.
      TitleInstance.executeQuery("""
        SELECT pti.titleInstance.id from PlatformTitleInstance pti
        WHERE pti.id = :ptiId
      """.toString(), [ptiId:ptiId]).forEach{ String tiId -> {

        // Find any other PTIs that have not been marked for deletion that exist for the TI.
        List<String> ptisForTi = PlatformTitleInstance.executeQuery("""
          SELECT pti.id FROM PlatformTitleInstance pti
          WHERE pti.titleInstance.id = :tiId
          AND pti.id NOT IN :ptisForDeletion
        """.toString(), [tiId:tiId, ptisForDeletion:markForDeletion.get("PTIs")], [max:1])


        if (ptisForTi.size() == 0) {
          tisForDeletion.add(tiId);
        }
      }}
    }}

    markForDeletion.put("TIs", tisForDeletion);

    // Work Checking
    List<String> worksForDeletion = new ArrayList<>();
    markForDeletion.get("TIs").forEach{ String tiId -> {

      TitleInstance.executeQuery("""
          SELECT ti.work.id FROM TitleInstance ti
          WHERE ti.id = :tiId
        """.toString(), [tiId: tiId]).forEach{String workId -> {

        List<String> ptisForWork = Work.executeQuery("""
          SELECT pti from PlatformTitleInstance pti
          WHERE pti.id NOT IN :ptisForDeletion
          AND pti.titleInstance.id NOT IN :tisForDeletion
          AND pti.titleInstance.work.id = :workId
        """.toString(), [workId:workId, ptisForDeletion: markForDeletion.get("PTIs"), tisForDeletion:markForDeletion.get("TIs")], [max:1])

        // Do we want to log when a work can't be deleted because PTIs exist? E.g.
//        log.info("LOG WARNING work could not be deleted: Work ID- {}, PTIs for Work- {}", workId, ptisForWork);

        if (ptisForWork.size() == 0) {
          worksForDeletion.add(workId);
        }
      }}
    }}

    markForDeletion.put("Works", worksForDeletion);

    log.info("LOG DEBUG - {}", markForDeletion);
    return markForDeletion
  }
}
