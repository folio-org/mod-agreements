package org.olf.General

import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import org.olf.PackageIngressMetadataRepairService
import org.olf.dataimport.internal.KBManagementBean
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.Pkg
import org.olf.kb.RemoteKB
import org.olf.kb.adapters.GOKbOAIAdapter
import org.olf.kb.metadata.PackageIngressMetadata
import org.olf.kb.metadata.ResourceIngressType
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

@Integration
@Stepwise
class PackageIngressMetadataRepairSpec extends BaseSpec {
  PackageIngressMetadataRepairService packageIngressMetadataRepairService

  void 'Upgrade repairs legacy packages in batches and preserves existing metadata and sync state'() {
    given:
      List<String> packageIds = []
      String sourceId
      String existingId
      String missingIdentifierId
      String localId
      List<String> sourceLabels = ['GOKb', 'gokb', 'GOKB', 'GoKB']
      withTenantNewTransaction {
        RemoteKB.executeUpdate('update RemoteKB set active = false')
        RemoteKB source = new RemoteKB(name: 'Repair source', type: GOKbOAIAdapter.name,
          uri: 'https://repair.example.org/gokb/oai/', active: false,
          rectype: RemoteKB.RECTYPE_PACKAGE, cursor: '2026-01-01T00:00:00Z', lastCheck: 123L
        ).save(failOnError: true)
        sourceId = source.id
        // More than one batch, including legacy null and explicitly paused flags.
        105.times { int i ->
          packageIds << legacyPackage("Legacy ${i}", i % 3 == 0 ? null : i % 3 == 1,
            sourceLabels[i % sourceLabels.size()]).id
        }
        Pkg existing = legacyPackage('Existing PIM', true)
        existingId = existing.id
        new PackageIngressMetadata(resource: existing, ingressType: ResourceIngressType.JSON_IMPORT,
          ingressId: 'original-source').save(failOnError: true)
        missingIdentifierId = new Pkg(name: 'No identifier', source: 'GOKb', reference: 'no-id')
          .save(failOnError: true).id
        Pkg local = legacyPackage('Local import', false)
        local.source = 'LOCAL'
        local.save(failOnError: true)
        localId = local.id
      }

    when:
      packageIngressMetadataRepairService.onTenantEnabled(tenantId, true, true, '7.5.0', '7.4.0')

    then:
      withTenantNewTransaction {
        assert packageIds.every { String id ->
          PackageIngressMetadata pim = PackageIngressMetadata.findByResource(Pkg.get(id))
          pim?.ingressType == ResourceIngressType.HARVEST && pim.ingressId == sourceId &&
            pim.dateCreated != null && pim.lastUpdated != null
        }
        assert packageIds.withIndex().every { String id, int i ->
          Pkg.get(id).syncContentsFromSource == (i % 3 == 0 ? null : i % 3 == 1)
        }
        assert packageIds.withIndex().every { String id, int i ->
          Pkg.get(id).source == sourceLabels[i % sourceLabels.size()]
        }
        assert PackageIngressMetadata.findByResource(Pkg.get(existingId)).ingressType == ResourceIngressType.JSON_IMPORT
        assert PackageIngressMetadata.findByResource(Pkg.get(existingId)).ingressId == 'original-source'
        assert !PackageIngressMetadata.findByResource(Pkg.get(missingIdentifierId))
        assert !PackageIngressMetadata.findByResource(Pkg.get(localId))
        assert RemoteKB.get(sourceId).cursor == '2026-01-01T00:00:00Z'
        assert RemoteKB.get(sourceId).lastCheck == 123L
        assert PackageIngressMetadata.count() == 106
        true
      }

    when: 'A subsequent upgrade repeats the repair'
      packageIngressMetadataRepairService.onTenantEnabled(tenantId, true, true, '7.5.1', '7.5.0')

    then:
      withTenantNewTransaction {
        assert PackageIngressMetadata.count() == 106
        true
      }
  }

  void 'Tenant enable without module_from repairs retained data and can be repeated'() {
    given:
      String packageId
      String originalMetadataId
      withTenantNewTransaction {
        packageId = legacyPackage('Retained package after disable', false).id
        assert !PackageIngressMetadata.findByResource(Pkg.get(packageId))
      }

    when: 'Okapi enables the tenant without identifying a previous module'
      def response = doPost('/_/tenant', {
        module_to('mod-agreements-7.4.1')
      })

    then:
      response != null
      new PollingConditions(timeout: 20).eventually {
        withTenantNewTransaction {
          Pkg pkg = Pkg.get(packageId)
          PackageIngressMetadata pim = PackageIngressMetadata.findByResource(pkg)
          assert pim?.ingressType == ResourceIngressType.HARVEST
          assert pim.ingressId == RemoteKB.findByName('Repair source').id
          assert pkg.syncContentsFromSource == false
          originalMetadataId = pim.id
        }
      }

    when: 'The same enable request is repeated'
      response = doPost('/_/tenant', {
        module_to('mod-agreements-7.4.1')
      })

    then:
      response != null
      withTenantNewTransaction {
        assert PackageIngressMetadata.findAllByResource(Pkg.get(packageId))*.id == [originalMetadataId]
        true
      }
  }

  void 'Reference fallback restores only absent identifiers and is idempotent'() {
    given:
      String sharedReference = UUID.randomUUID().toString()
      List<Map> cases = [
        [reference: UUID.randomUUID().toString(), restore: true],
        [reference: UUID.randomUUID().toString(), restore: true, reuse: true],
        [reference: sharedReference, restore: true, source: 'GOKb'],
        [reference: sharedReference, restore: true, source: 'gokb'],
        [reference: '1-1-1-1-1', restore: false],
        [reference: 'dfbfa79f-ba78-4fde-af92-d41ce9592f2g', restore: false],
        [reference: ' ' + UUID.randomUUID().toString(), restore: false],
        [reference: UUID.randomUUID().toString(), restore: false, statuses: ['error']],
        [reference: UUID.randomUUID().toString(), restore: false, statuses: ['approved', 'approved']],
        [reference: UUID.randomUUID().toString(), restore: false, source: 'LOCAL'],
        [reference: UUID.randomUUID().toString(), restore: false, existingPim: true]
      ]
      withTenantNewTransaction {
        IdentifierNamespace ns = IdentifierNamespace.findByValue('gokb_uuid')
        cases.eachWithIndex { Map entry, int i ->
          Pkg pkg = new Pkg(name: "Reference fallback ${i}", source: entry.source ?: 'gokb',
            reference: entry.reference, syncContentsFromSource: false).save(failOnError: true)
          entry.id = pkg.id
          if (entry.reuse) {
            entry.identifierId = new Identifier(ns: ns, value: entry.reference).save(failOnError: true).id
          }
          entry.statuses?.each { String status ->
            Identifier identifier = new Identifier(ns: ns, value: UUID.randomUUID().toString())
              .save(failOnError: true)
            new IdentifierOccurrence(resource: pkg, identifier: identifier,
              status: IdentifierOccurrence.lookupOrCreateStatus(status)).save(failOnError: true)
          }
          if (entry.existingPim) {
            new PackageIngressMetadata(resource: pkg, ingressType: ResourceIngressType.JSON_IMPORT)
              .save(failOnError: true)
          }
        }
      }
      // Domain validation trims strings. After inserting, retain legacy whitespace via HQL.
      withTenantNewTransaction {
        cases.each { Map entry ->
          entry.originalIdentifiers = IdentifierOccurrence.findAllByResource(Pkg.get(entry.id))*.id
        }
        cases.findAll { it.reference.startsWith(' ') }.each { Map entry ->
          assert Pkg.executeUpdate('update Pkg set reference = :reference where id = :id',
            [reference: entry.reference, id: entry.id]) == 1
        }
      }

    when:
      withTenant { packageIngressMetadataRepairService.repairMissingMetadata() }

    then:
      withTenantNewTransaction {
        cases.each { Map entry ->
          Pkg pkg = Pkg.get(entry.id)
          List<IdentifierOccurrence> occurrences = IdentifierOccurrence.findAllByResource(pkg)
          PackageIngressMetadata pim = PackageIngressMetadata.findByResource(pkg)
          assert pkg.reference == entry.reference
          assert pkg.syncContentsFromSource == false
          if (entry.restore) {
            assert occurrences.size() == 1
            assert occurrences[0].identifier.ns.value == 'gokb_uuid'
            assert occurrences[0].identifier.value == entry.reference
            assert Identifier.countByNsAndValue(occurrences[0].identifier.ns, entry.reference) == 1
            assert occurrences[0].status.value == 'approved'
            assert pim?.ingressType == ResourceIngressType.HARVEST
            if (entry.reuse) assert occurrences[0].identifier.id == entry.identifierId
          } else {
            assert occurrences*.id == entry.originalIdentifiers
            assert entry.existingPim ? pim?.ingressType == ResourceIngressType.JSON_IMPORT : pim == null
          }
          entry.repairedIdentifiers = occurrences*.id
          entry.metadataId = pim?.id
        }
        true
      }

    when:
      withTenant { packageIngressMetadataRepairService.repairMissingMetadata() }

    then:
      withTenantNewTransaction {
        cases.each { Map entry ->
          Pkg pkg = Pkg.get(entry.id)
          assert IdentifierOccurrence.findAllByResource(pkg)*.id == entry.repairedIdentifiers
          assert PackageIngressMetadata.findByResource(pkg)?.id == entry.metadataId
        }
        true
      }
  }

  void 'Multiple sources including inactive ones are ambiguous'() {
    given:
      String packageId
      withTenantNewTransaction {
        new RemoteKB(name: 'Second repair source', type: GOKbOAIAdapter.name,
          uri: 'https://other.example.org/oai/', active: false, rectype: RemoteKB.RECTYPE_PACKAGE)
          .save(failOnError: true)
        packageId = new Pkg(name: 'Ambiguous fallback source', source: 'GOKb',
          reference: UUID.randomUUID().toString()).save(failOnError: true).id
      }

    when:
      packageIngressMetadataRepairService.onTenantEnabled(tenantId, true, true, '7.5.0', '7.4.0')

    then:
      withTenantNewTransaction {
        assert !PackageIngressMetadata.findByResource(Pkg.get(packageId))
        assert IdentifierOccurrence.countByResource(Pkg.get(packageId)) == 0
        true
      }
  }

  void 'No configured source leaves legacy packages unresolved'() {
    when:
      withTenantNewTransaction {
        RemoteKB.executeUpdate('update RemoteKB set type = null')
      }
      Map result = withTenant { packageIngressMetadataRepairService.repairMissingMetadata() }

    then:
      result == [created: 0, skipped: 0]
  }

  // Do not change the shared application bean: this instance exercises the event guards.
  void 'PushKB tenant enables and upgrades do not attempt a Harvest repair'() {
    given:
      def repair = Spy(PackageIngressMetadataRepairService)
      repair.kbManagementBean = new KBManagementBean(ingressType: ResourceIngressType.PUSHKB)

    when:
      repair.onTenantEnabled(currentTenant, false, false, '7.5.0', null)
      repair.onTenantEnabled(currentTenant, true, true, '7.5.0', '7.4.0')

    then:
      0 * repair.repairMissingMetadata()
  }

  private Pkg legacyPackage(String name, Boolean sync, String source = 'GOKb') {
    String uuid = UUID.randomUUID().toString()
    Pkg pkg = new Pkg(name: name, source: source, reference: uuid, syncContentsFromSource: sync)
      .save(failOnError: true)
    IdentifierNamespace ns = IdentifierNamespace.findByValue('gokb_uuid') ?:
      new IdentifierNamespace(value: 'gokb_uuid').save(failOnError: true)
    Identifier identifier = new Identifier(ns: ns, value: uuid).save(failOnError: true)
    new IdentifierOccurrence(resource: pkg, identifier: identifier,
      status: IdentifierOccurrence.lookupOrCreateStatus('approved')).save(failOnError: true)
    pkg
  }
}
