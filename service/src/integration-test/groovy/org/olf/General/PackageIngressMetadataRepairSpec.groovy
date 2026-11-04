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

  void 'Multiple sources including inactive ones are ambiguous'() {
    given:
      String packageId
      withTenantNewTransaction {
        new RemoteKB(name: 'Second repair source', type: GOKbOAIAdapter.name,
          uri: 'https://other.example.org/oai/', active: false, rectype: RemoteKB.RECTYPE_PACKAGE)
          .save(failOnError: true)
        packageId = legacyPackage('Ambiguous source', true).id
      }

    when:
      packageIngressMetadataRepairService.onTenantEnabled(tenantId, true, true, '7.5.0', '7.4.0')

    then:
      withTenantNewTransaction {
        assert !PackageIngressMetadata.findByResource(Pkg.get(packageId))
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
  void 'Fresh tenant enable and PushKB upgrades do not attempt a Harvest repair'() {
    given:
      def repair = Spy(PackageIngressMetadataRepairService)
      repair.kbManagementBean = new KBManagementBean(ingressType: ResourceIngressType.HARVEST)

    when:
      repair.onTenantEnabled(currentTenant, false, false, '7.5.0', null)
      repair.kbManagementBean.ingressType = ResourceIngressType.PUSHKB
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
