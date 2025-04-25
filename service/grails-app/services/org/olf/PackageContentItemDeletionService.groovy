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

//    def generateERMsInAgreementsSet() {
//        // Get all subscription agreements.
//        List<SubscriptionAgreement> agreementList = SubscriptionAgreement.getAll();
//
//        HashSet<String> ermsWithAgreements = new HashSet<>();
//
//        // This is the list of all ERM Resources (PTIs, PCIs, Packages) for all agreement lines in the agreement.
//        agreementList.forEach { SubscriptionAgreement agreement -> agreement.items.forEach {Entitlement it -> ermsWithAgreements.add(it.resource.id)} }
//
//        return ermsWithAgreements;
//    }

  boolean isAttachedToAgreementLines(String id) {
    // if true, cannot delete PCI.

    // Get all subscription agreements.
    // List<SubscriptionAgreement> agreementList = SubscriptionAgreement.getAll();

    // Iterate over entitlements of each subscription agreement. If the package the PCI relates to is present, return true.
    // agreementList.forEach { SubscriptionAgreement agreement -> if (agreement.items.contains(pci.pkg)) return true; }

    // NOTE: there's probably a faster way of doing this. Otherwise you have to traverse all agreement lines on the tenant for every delete.
    // Would be faster to build a set of all packages present in agreement lines and store in memory to be hit.
//        if (generateERMsInAgreementsSet().contains(id)) return true;

    List<Entitlement> linesForResource = Entitlement.executeQuery("""
        SELECT ent FROM Entitlement ent
        WHERE ent.resource.id = :resId
        LIMIT 1
        """.toString(), [resId:id])

    return linesForResource.size() > 0;
  }

  def ptiExistsInAnyPci(String ptiId, String currentPciId) {
    if (!ptiId) {
      return false
    }

    List<PackageContentItem> pcisForPti = PackageContentItem.executeQuery("""
            SELECT pci FROM PackageContentItem pci
            WHERE pti.id = 
        """)

    return PackageContentItem.where {
      pti.id == ptiId &&
          id != currentPciId
    }
  }

  boolean tiExistsInAnyPti(String tiId, currentPtiId) {
    if (!tiId) {
      return false
    }
    return PlatformTitleInstance.where {
      titleInstance.id == tiId &&
          id != currentPtiId
    }
  }

  List<TitleInstance> tisForWork(String workId) {
    if (!workId) {
      return null
    }

    return TitleInstance.createCriteria().get {
      work {
        eq('id', workId)
      }
      projections {
        countDistinct('id')
      }
    }
  }




  def heirarchicalDeletePCI(List<String> ids) {
    Map<String, List<String>> markForDeletion = new HashMap<>();
    List<String> pcisForDeletion = new ArrayList<>();

    ids.forEach { String id -> {
      List<String> linesForResource = Entitlement.executeQuery(
          """
                SELECT ent.id FROM Entitlement ent
                WHERE ent.resource.id = :resId
                """.toString(), [resId:id], [max:1])

      if (linesForResource.size() == 0) {
        pcisForDeletion.add(id);
      };


    }}
    markForDeletion.put("PCIs", pcisForDeletion);

    // Check zero-case
    List<String> ptisForDeletion;
    markForDeletion.get("PCIs").forEach{ String id -> {
      PlatformTitleInstance.executeQuery("""
        SELECT pci.pti.id FROM PackageContentItem pci
        WHERE pci.id = :pciId
      """.toString(), [pciId:id]).forEach{ String ptiId -> {
        List<String> linesForResource = Entitlement.executeQuery(
            """
                SELECT ent.id FROM Entitlement ent
                WHERE ent.resource.id = :resId
                """.toString(), [resId:ptiId], [max:1])

        List<String> pcisForPti = PackageContentItem.executeQuery("""
          SELECT pci.id FROM PackageContentItem pci
          WHERE pci.pti.id = :ptiId
          AND pci.id NOT IN :pcisForDeletion
        """.toString(), [ptiId:ptiId, pcisForDeletion:markForDeletion.get("PCIs")], [max:1])

        if (linesForResource.size() == 0 && pcisForPti.size() == 0) {
          ptisForDeletion.add(ptiId);
        }
      }}
    }}

    markForDeletion.put("PTIs", ptisForDeletion);

    log.info("LOG DEBUG - {}", markForDeletion);

//    List<Entitlement> linesForResource = Entitlement.executeQuery("""
//        SELECT ent FROM Entitlement ent
//        WHERE ent.resource.id = :resId
//        LIMIT 1
//        """.toString(), [resId:id])
//
//    List<PackageContentItem> pcisForPti = PackageContentItem.executeQuery("""
//            SELECT pci FROM PackageContentItem pci
//            WHERE pci.pti.id = :ptiId
//
//        """)
//
//    if (linesForResource.size() > 0) return null;
//
//    if (isAttachedToAgreementLines(pci.id)) return null;
//
//    if (isAttachedToAgreementLines(pci.pti.id)) return null;
//
//    log.info("Attempting to find PCIs");
//    log.info(pci.pti.id);
//
//    if (ptiExistsInAnyPci(pci.pti.id, pciId)) return null;
//
//    log.info("Attempting to find PTIs");
//
//    if (tiExistsInAnyPti(pci.pti.titleInstance.id, pci.pti.id)) return null;
//
//    log.info("Attempting to find work");
//    Work work = pci.pti.titleInstance.work;



    // If we get to here, the TI, PTI and PCI can all be deleted (I suppose this means there's a 1:1:1 relationship?)



//    return work;
  }
}
