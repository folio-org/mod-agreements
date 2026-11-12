package org.olf.General

import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import org.olf.PackageIngestService
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.TitleInstance
import spock.lang.Stepwise

@Integration
@Stepwise
class PackageIngestServiceSpec extends BaseSpec {
  PackageIngestService packageIngestService

  void 'Title hierarchy returns the persisted PCI ID for both creation and update'() {
    given: 'A package and title with no existing content item'
      String packageId
      String titleId
      withTenantNewTransaction {
        packageId = new Pkg(
          name: 'PCI ID regression package',
          source: 'PCI ID regression',
          reference: 'pci-id-regression'
        ).save(failOnError: true, flush: true).id
        titleId = new TitleInstance(name: 'PCI ID regression title')
          .save(failOnError: true, flush: true).id
      }
      // No coverage: coverage processing can save a new PCI before the final save,
      // masking an ID read performed too early.
      PackageContentImpl content = new PackageContentImpl(
        title: 'PCI ID regression title',
        platformName: 'PCI ID regression platform',
        platformUrl: 'https://pci-id-regression.example.org',
        url: 'https://pci-id-regression.example.org/title',
        coverage: []
      )
      long updateTime = System.currentTimeMillis()

    when: 'The hierarchy creates a new package content item'
      Map created
      withTenantNewTransaction {
        created = packageIngestService.lookupOrCreateTitleHierarchy(
          titleId, packageId, false, content, updateTime, 0L
        )
      }

    then: 'The returned ID identifies the persisted item'
      created.pciStatus == 'new'
      created.pciId != null
      assertPersistedItem(created, packageId, titleId, null)

    when: 'The same content item is ingested again with an updated note'
      content.coverageNote = 'Updated note'
      Map updated
      withTenantNewTransaction {
        updated = packageIngestService.lookupOrCreateTitleHierarchy(
          titleId, packageId, false, content, updateTime + 1000L, 0L
        )
      }

    then: 'The existing item is updated and its ID is retained'
      updated.pciStatus == 'updated'
      updated.pciId == created.pciId
      assertPersistedItem(updated, packageId, titleId, 'Updated note')
  }

  private void assertPersistedItem(Map result, String packageId, String titleId, String note) {
    withTenantNewTransaction {
      PackageContentItem pci = PackageContentItem.get(result.pciId)
      assert pci != null
      assert pci.pkg.id == packageId
      assert pci.pti.id == result.ptiId
      assert pci.pti.titleInstance.id == titleId
      assert pci.note == note
      assert PackageContentItem.countByPkg(Pkg.get(packageId)) == 1
    }
  }
}
