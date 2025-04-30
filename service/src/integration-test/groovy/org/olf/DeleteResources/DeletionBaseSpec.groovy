package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import org.olf.kb.ErmResource
import spock.lang.Ignore
import spock.lang.Stepwise

@Integration
@Stepwise
class DeletionBaseSpec extends BaseSpec {

  @Ignore
  void clearResources() {
    ErmResource.withTransaction {
      ErmResource.executeUpdate(
          """DELETE FROM PackageContentItem"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM PlatformTitleInstance"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM TitleInstance"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM Work"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM ErmResource"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM ErmTitleList"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM SubscriptionAgreement"""
      )

      ErmResource.executeUpdate(
          """DELETE FROM Entitlement"""
      )
    }
  }

  void "Scenario 1: Fully delete one PCI chain with no other references"() {
    when: 'We check what resources are in the system'
    Map kbStatsResp = doGet("/erm/statistics/kbCount")
    Map sasStatsResp = doGet("/erm/statistics/sasCount")
    then:
    kbStatsResp.ErmResource == 0
    kbStatsResp.PackageContentItems == 0
    kbStatsResp.PlatformTitleInstance == 0
    kbStatsResp.TitleInstance == 0
    kbStatsResp.Work == 0

    sasStatsResp.SubscriptionAgreement == 0
    sasStatsResp.Entitlement == 0
  }

}
