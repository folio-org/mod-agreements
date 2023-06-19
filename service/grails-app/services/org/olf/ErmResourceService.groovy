package org.olf

import org.olf.dataimport.internal.PackageSchema.ContentItemSchema

import org.olf.kb.ErmResource
import org.olf.kb.TitleInstance
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg

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
  public List<String> getFullResourceList (ErmResource res) {
    List<String> resourceList = [res.id]
    List<String> ptis = []
    List<String> pcis = []
    ErmResource.withNewTransaction {
      // If res is a TI, find all the associated PTIs and store them
      if (res instanceof TitleInstance) {
        ptis.addAll(
          PlatformTitleInstance.executeQuery(PTI_HQL, [resId: res.id])
        )
      }

      // If res is a PTI, find all PCIS associated and store them
      if (res instanceof PlatformTitleInstance) {
        pcis.addAll(
          PackageContentItem.executeQuery(PCI_HQL, [resId: res.id])
        )
      }
      // Also store any PCIs attached to any PTIs stored earlier
      ptis.each {String ptiId ->
        pcis.addAll(PackageContentItem.executeQuery(PCI_HQL, [resId: ptiId]))
      }

      // At this point we have a comprehensive list of resources at various levels
      resourceList.addAll(ptis)
      resourceList.addAll(pcis)
    }

    resourceList
  }

  public void handleResourceHierarchyUpdate(ErmResource res) {
    if (res instanceof TitleInstance) {
      TitleInstance ti = (TitleInstance) res

      List<PlatformTitleInstance> ptis = PlatformTitleInstance.executeQuery("""
      SELECT pti FROM PlatformTitleInstance AS pti
        WHERE pti.titleInstance.id = :tiId
        """, [tiId: ti.id])

      ptis.each { PlatformTitleInstance pti ->
        pti.lastUpdated = ti.lastUpdated
        pti.save(failOnError: true)
      }
    } else if (res instanceof PlatformTitleInstance) {
        PlatformTitleInstance pti = (PlatformTitleInstance) res

        List<PackageContentItem> pcis = PackageContentItem.executeQuery("""
        SELECT pci FROM PackageContentItem AS pci
        WHERE pci.pti.id = :ptiId
        """, [ptiId: pti.id])

        pcis.each { PackageContentItem pci ->
          pci.lastUpdated = pti.lastUpdated
          pci.save(failOnError: true)
        }
    }  else if (res instanceof PackageContentItem) {
        PackageContentItem pci = (PackageContentItem) res

        Pkg pkg = Pkg.executeQuery("""
        SELECT pkg FROM Pkg AS pkg
        WHERE pkg.id = :pciPkgId
        """, [pciPkgId: pci.pkg.id])[0]

        pkg.lastUpdated = pci.lastUpdated
        pkg.save(failOnError: true)
    }
  }

  // FIXME do we actually want this?
  // This method should take in an ErmResource and return a ContentItemSchema, which can then be used to create matchKeys
  ContentItemSchema resourceToSchema(ErmResource resource) {

  }
}

