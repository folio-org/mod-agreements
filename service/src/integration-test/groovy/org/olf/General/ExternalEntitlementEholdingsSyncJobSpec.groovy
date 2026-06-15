package org.olf.General

import grails.testing.mixin.integration.Integration
import jakarta.inject.Inject
import org.olf.BaseSpec
import org.olf.EholdingsService
import org.olf.KbManagementService
import org.olf.erm.Entitlement
import org.olf.erm.SubscriptionAgreement
import org.olf.general.jobs.ExternalEntitlementEholdingsSyncJob
import org.olf.general.jobs.PackageIngestJob
import org.olf.general.jobs.PersistentJob
import org.olf.general.jobs.TitleIngestJob
import spock.lang.Ignore
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

import java.time.Instant
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await
import static org.junit.jupiter.api.Assertions.assertEquals

@Stepwise
@Integration
class ExternalEntitlementEholdingsSyncJobSpec extends BaseSpec {

  @Inject
  EholdingsService eholdingsService

  @Inject
  KbManagementService kbManagementService

  def mockDomains = [
    ExternalEntitlementEholdingsSyncJob,
    PackageIngestJob,
    TitleIngestJob,
    PersistentJob
  ]

  static final String EKB_PACKAGE_AUTHORITY = "EKB-PACKAGE"
  static final String EKB_TITLE_AUTHORITY = "EKB-TITLE"
  static final String EKB_PACKAGE_REFERENCE = "19-1615"
  static final String EKB_TITLE_REFERENCE = "32498-16793-19948731"
  static final String EXAMPLE_GOKB_REFERENCE = "26929514-237c-11ed-861d-0242ac120002:26929514-237c-11ed-861d-0242ac120001"

  @Ignore
  Map postEntitlement(String agreementName, String authority, String reference, String resourceName) {
    def payload = [
      items: [
        [
          'type'         : 'external',
          'reference'    : reference,
          'authority'    : authority,
          'resourceName' : resourceName,
          'description'  : "eHoldings sync test entitlement"
        ]
      ],
      periods: [
        [ startDate: '2025-08-20' ]
      ],
      name: agreementName,
      agreementStatus: "active"
    ]

    return doPost("/erm/sas", payload) as Map
  }

  void "findEholdingsEntitlementsWithoutResourceName returns only EKB entitlements with null resourceName" () {
    setup:
      withTenant {
        // Should be returned: EKB-PACKAGE with null resourceName
        postEntitlement("test_ekb_pkg_null", EKB_PACKAGE_AUTHORITY, EKB_PACKAGE_REFERENCE, null)
        // Should be returned: EKB-TITLE with null resourceName
        postEntitlement("test_ekb_title_null", EKB_TITLE_AUTHORITY, EKB_TITLE_REFERENCE, null)
        // Should NOT be returned: EKB-PACKAGE that already has resourceName
        postEntitlement("test_ekb_pkg_named", EKB_PACKAGE_AUTHORITY, "${EKB_PACKAGE_REFERENCE}-named", "Already Named Package")
        // Should NOT be returned: GOKB-RESOURCE (different authority)
        postEntitlement("test_gokb", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, "gokb resource")
      }

    when:
      List<Entitlement> found
      withTenant {
        found = eholdingsService.findEholdingsEntitlementsWithoutResourceName()
      }

    then:
      assert found != null
      assert found.size() == 2
      assert found.every { it.authority in [EKB_PACKAGE_AUTHORITY, EKB_TITLE_AUTHORITY] }
      assert found.every { it.resourceName == null }
      assert found.every { it.reference != null }

    cleanup:
      withTenant {
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) }
      }
  }

  void "When no eHoldings entitlements need resourceName, no ExternalEntitlementEholdingsSyncJob is created" () {
    setup:
      withTenant {
        // Only entitlements that should be excluded: GOKB and EKB with resourceName already set.
        postEntitlement("test_gokb_only", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, "gokb resource")
        postEntitlement("test_ekb_named", EKB_PACKAGE_AUTHORITY, EKB_PACKAGE_REFERENCE, "Already Named")
      }

    when:
      withTenant {
        kbManagementService.triggerEntitlementEholdingsJob()
      }

    then:
      await().atMost(5, TimeUnit.SECONDS).untilAsserted {
        assertEquals(0, withTenant { ExternalEntitlementEholdingsSyncJob.count() })
      }

    cleanup:
      withTenant {
        ExternalEntitlementEholdingsSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) }
      }
  }

  void "When EKB entitlements need resourceName, ExternalEntitlementEholdingsSyncJob is created" () {
    setup:
      withTenant {
        postEntitlement("test_ekb_pkg_needs_sync", EKB_PACKAGE_AUTHORITY, EKB_PACKAGE_REFERENCE, null)
      }

    when:
      withTenant {
        kbManagementService.triggerEntitlementEholdingsJob()
      }

    then:
      await().atMost(5, TimeUnit.SECONDS).untilAsserted {
        assertEquals(1, withTenant { ExternalEntitlementEholdingsSyncJob.count() })
      }

    cleanup:
      withTenant {
        ExternalEntitlementEholdingsSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) }
      }
  }

  void "When existing queued ExternalEntitlementEholdingsSyncJob exists, no job is created" () {
    setup:
      withTenant {
        postEntitlement("test_ekb_pkg_queued", EKB_PACKAGE_AUTHORITY, EKB_PACKAGE_REFERENCE, null)
        ExternalEntitlementEholdingsSyncJob existing = new ExternalEntitlementEholdingsSyncJob(name: "Test ExternalEntitlementEholdingsSyncJob ${Instant.now()}")
        existing.setStatusFromString('queued')
        existing.save(failOnError: true, flush: true)
      }

    when:
      withTenant {
        kbManagementService.triggerEntitlementEholdingsJob()
      }

    then:
      await().atMost(5, TimeUnit.SECONDS).untilAsserted {
        assertEquals(1, withTenant { ExternalEntitlementEholdingsSyncJob.count() })
      }

    cleanup:
      withTenant {
        ExternalEntitlementEholdingsSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) }
      }
  }

  void "When existing in progress ExternalEntitlementEholdingsSyncJob exists, no job is created" () {
    setup:
      withTenant {
        postEntitlement("test_ekb_pkg_inprogress", EKB_PACKAGE_AUTHORITY, EKB_PACKAGE_REFERENCE, null)
        ExternalEntitlementEholdingsSyncJob existing = new ExternalEntitlementEholdingsSyncJob(name: "Test ExternalEntitlementEholdingsSyncJob ${Instant.now()}")
        existing.setStatusFromString('In progress')
        existing.save(failOnError: true, flush: true)
      }

    when:
      withTenant {
        kbManagementService.triggerEntitlementEholdingsJob()
      }

    then:
      await().atMost(5, TimeUnit.SECONDS).untilAsserted {
        assertEquals(1, withTenant { ExternalEntitlementEholdingsSyncJob.count() })
      }

    cleanup:
      withTenant {
        ExternalEntitlementEholdingsSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) }
      }
  }

  void "Queued ExternalEntitlementEholdingsSyncJob runs to completion without wiring errors" () {
    setup:
      withTenant {
        postEntitlement("test_ekb_pkg_runs", EKB_PACKAGE_AUTHORITY, EKB_PACKAGE_REFERENCE, null)
        kbManagementService.triggerEntitlementEholdingsJob()
      }

    expect: 'Job is picked up by the runner and reaches Ended without an unhandled exception'
      def conditions = new PollingConditions(timeout: 90)
      conditions.eventually {
        ExternalEntitlementEholdingsSyncJob job = withTenant {
          ExternalEntitlementEholdingsSyncJob.findAll([sort: 'dateCreated', order: 'desc']).find()
        }
        assert job != null
        assert job.status?.value == 'ended'
        assert job.result?.value in ['success', 'partial_success']
      }

    cleanup:
      withTenant {
        ExternalEntitlementEholdingsSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) }
      }
  }

  void "Trigger creates a new job alongside an already-ended one (no interval guard - cadence is governed by the _timer interface)" () {
    setup:
      withTenant {
        postEntitlement("test_ekb_pkg_after_ended", EKB_PACKAGE_AUTHORITY, EKB_PACKAGE_REFERENCE, null)
        ExternalEntitlementEholdingsSyncJob completed = new ExternalEntitlementEholdingsSyncJob(name: "Previously ended ${Instant.now()}")
        completed.setStatusFromString('Ended')
        completed.ended = Instant.now()
        completed.save(failOnError: true, flush: true)
      }

    when:
      withTenant {
        kbManagementService.triggerEntitlementEholdingsJob()
      }

    then:
      // Trigger only checks for queued/in-progress; an Ended job does not block, so we expect 2 records.
      await().atMost(5, TimeUnit.SECONDS).untilAsserted {
        assertEquals(2, withTenant { ExternalEntitlementEholdingsSyncJob.count() })
      }

    cleanup:
      withTenant {
        ExternalEntitlementEholdingsSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) }
      }
  }
}