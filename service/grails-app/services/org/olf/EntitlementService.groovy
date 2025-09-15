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

  void processExternalEntitlements() {
    findEntitlementsByAuthority("GOKB-RESOURCE").forEach{Entitlement entitlement -> {
      String packageId = entitlement.getReference().split(":")[0]
      String titleId = entitlement.getReference().split(":")[1]

//      ErmTitleList packageInLocalKb = ErmTitleList.findById(packageId) as ErmTitleList
      Pkg packageInLocalKb = Pkg.executeQuery("""
          SELECT id FROM Pkg AS pkg
          WHERE pkg.id = :resId""".toString(), [resId: packageId]) as Pkg

      if (packageInLocalKb && packageInLocalKb.getSyncContentsFromSource()) {
        // If we find the PCI via the TI that exists in the Entitlement reference (packageUuid:titleUuid)
        PackageContentItem pciInLocalKb = PackageContentItem.executeQuery("""
          SELECT id FROM PackageContentItem AS pci
          WHERE pci.pti.titleInstance.id = :resId""".toString(), [resId: titleId]) as PackageContentItem

        if (pciInLocalKb) {
          entitlement.reference = null;
          entitlement.authority = null;
          entitlement.type = "Internal";
          entitlement.resource = pciInLocalKb;
          entitlement.resourceName = null;
        }
      }
    }}
  }
}

