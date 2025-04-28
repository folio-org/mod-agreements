package org.olf.General

import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import org.olf.PackageContentItemDeletionService
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Work
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
class HierarchicalDeletionServiceSpec extends BaseSpec{

  PackageContentItemDeletionService packageContentItemDeletionService


  private Map createPciPtiTiWorkInstance(String suffix = "") {
    Work work = new Work(name: "Test Work ${suffix}").save(flush: true, failOnError: true)
    TitleInstance ti = new TitleInstance(name: "Test TI ${suffix}", work: work).save(flush: true, failOnError: true)
    PlatformTitleInstance pti = new PlatformTitleInstance(name: "Test PTI ${suffix}", titleInstance: ti).save(flush: true, failOnError: true)
    PackageContentItem pci = new PackageContentItem(name: "Test PCI ${suffix}", pti: pti).save(flush: true, failOnError: true)

    return [work: work, ti: ti, pti: pti, pci: pci]
  }

  def setup() {

  }

  def "Scenario 1: Fully delete one PCI chain with no other references"() {
    given: "One PCI -> PTI -> TI -> Work chain with no entitlements or other references"
    Map chain = createPciPtiTiWorkInstance("Scenario1")
    PackageContentItem pci = chain.pci
    PlatformTitleInstance pti = chain.pti
    TitleInstance ti = chain.ti
    Work work = chain.work

    // Ensure no entitlements exist
    assert Entitlement.countByResource(pci) == 0
    assert Entitlement.countByResource(pti) == 0
    assert PackageContentItem.count() == 1
    assert PlatformTitleInstance.count() == 1
    assert TitleInstance.count() == 1
    assert Work.count() == 1

    when: "The service is called with the PCI ID"
    List<String> pciIdsToTest = [pci.id.toString()]
    Map<String, List<String>> result = packageContentItemDeletionService.heirarchicalDeletePCI(pciIdsToTest)

    then: "The result map contains IDs for PCI, PTI, TI, and Work"
    result != null
    result.PCIs?.size() == 1
    result.PCIs?.contains(pci.id.toString())

    result.PTIs?.size() == 1
    result.PTIs?.contains(pti.id.toString())

    result.TIs?.size() == 1
    result.TIs?.contains(ti.id.toString())

    result.Works?.size() == 1
    result.Works?.contains(work.id.toString())
  }

}
