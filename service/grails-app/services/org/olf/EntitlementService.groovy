package org.olf

import org.olf.kb.ErmResource
import org.olf.kb.ErmTitleList
import org.olf.kb.Pkg
import org.olf.kb.TitleInstance
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.PackageContentItem

import static org.springframework.transaction.annotation.Propagation.MANDATORY
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW

import org.olf.erm.Entitlement


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import grails.gorm.transactions.Transactional

/**
 * This service deals with logic that handles updates on content being reflected in entitlements
 */
@Slf4j
@CompileStatic
public class EntitlementService {

  ErmResourceService ermResourceService
  PackageSyncService packageSyncService

  private final static String PCI_HQL = """
    SELECT id FROM PackageContentItem AS pci
    WHERE pci.pti.id = :resId
  """

  private final static String PTI_HQL = """
    SELECT id FROM PlatformTitleInstance AS pti
    WHERE pti.titleInstance.id = :resId
  """

  private final static String ENT_HQL = """
    SELECT ent FROM Entitlement AS ent
    WHERE ent.resource.id = :resId
  """



  @Transactional(propagation = MANDATORY)
  public void handleErmResourceChange(ErmResource res) {
    Date now = new Date();

    List<String> resourcesToQuery = ermResourceService.getFullResourceList(res)

    List<Entitlement> entitlements = [];

    // When ErmResource has changed, update contentUpdated for all entitlements for that resource
//    Entitlement.withNewTransaction {
    resourcesToQuery.each {String resId ->
      entitlements.addAll(
        (List<Entitlement>) Entitlement.executeQuery(ENT_HQL, [resId: resId])
      )
    }

    entitlements.each {
      it.contentUpdated = now
      it.save(failOnError: true)
      }
//    }

  }

  List<Entitlement> findEntitlementsByAuthority(authority="GOKB-RESOURCE") {
    return Entitlement.executeQuery("""
          SELECT ent FROM Entitlement AS ent
          WHERE ent.authority = :authorityName""".toString(), [authorityName: authority]) as List<Entitlement>
  }

  @Transactional
  void processExternalEntitlements() {
    findEntitlementsByAuthority("GOKB-RESOURCE").forEach{Entitlement entitlement ->
      {
        log.info("Entitlement found: {} {}", entitlement.authority, entitlement.reference)
        String packageGokbId = entitlement.getReference().split(":")[0]
        String titleGokbId = entitlement.getReference().split(":")[1]

        log.info("{}", packageGokbId)
        log.info("{}", titleGokbId)

        Pkg packageInLocalKb = (Pkg) Pkg.executeQuery("""
          SELECT p FROM Pkg p
          WHERE p.id IN (
          SELECT io.resource.id FROM IdentifierOccurrence io
          WHERE io.identifier.value = :resId
          AND io.identifier.ns.value = 'gokb_uuid')""".toString(), [resId: packageGokbId])[0]

        TitleInstance titleInstanceInLocalKb = (TitleInstance) TitleInstance.executeQuery("""
         SELECT ti FROM TitleInstance ti
          WHERE ti.id IN ( SELECT io.resource.id FROM IdentifierOccurrence io
          WHERE io.identifier.value = :resId
          AND io.identifier.ns.value = 'gokb_uuid')""".toString(), [resId: titleGokbId])[0]

        log.info("{}", packageInLocalKb)
        log.info("{}", titleInstanceInLocalKb)

        if (!packageInLocalKb) {
          log.debug("Package {} containing title {} for Entitlement {} not found.", packageGokbId, titleGokbId, entitlement.id)
        }

        if (packageInLocalKb && packageInLocalKb.getSyncContentsFromSource()) {
          // If we find the PCI via the TI that exists in the Entitlement reference (packageUuid:titleUuid)
          // The TI isn't necessarily directly related to a PCI, so find the work then use this ID to find the PCI.
          PackageContentItem pciInLocalKb = PackageContentItem.executeQuery("""
          SELECT pci FROM PackageContentItem AS pci
          WHERE pci.pti.titleInstance.work.id = :workId""".toString(), [workId: titleInstanceInLocalKb.work.id])[0] as PackageContentItem
          // TODO: Could this search return more than one PCI?

          log.info("ti ID used in search: {}", titleInstanceInLocalKb.id)
          if (pciInLocalKb) {
            log.info("PCI found: {}", pciInLocalKb)
            entitlement.setReference(null);
            entitlement.authority = null;
            entitlement.type = "internal";
            entitlement.resource = pciInLocalKb;
            entitlement.resourceName = null;
            log.info(entitlement.reference)
            entitlement.save(failOnError:true);
          } else {
            log.info("No PCI found")
          }
          // TODO: What will happen if we've found the package in the localKB, but not the Title/PCI?
        }

        if (packageInLocalKb && !packageInLocalKb.getSyncContentsFromSource()) {
          packageSyncService.controlSyncStatus([packageInLocalKb.id], true)
        }
      }}
  }
}

