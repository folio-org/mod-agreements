package org.olf

import com.k_int.okapi.OkapiTenantResolver
import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import groovy.util.logging.Slf4j
import org.olf.dataimport.internal.KBManagementBean
import org.olf.kb.Pkg
import org.olf.kb.RemoteKB
import org.olf.kb.adapters.GOKbOAIAdapter
import org.olf.kb.metadata.PackageIngressMetadata
import org.olf.kb.metadata.ResourceIngressType

/** Repair legacy packages which predate ingress metadata, without re-harvesting contents. */
@Slf4j
class PackageIngressMetadataRepairService {
  private static final int BATCH_SIZE = 100

  KBManagementBean kbManagementBean

  @Subscriber('okapi:tenant_enabled')
  void onTenantEnabled(String tenantId, boolean existingTenant, boolean upgrading,
                       String toVersion, String fromVersion) {
    // A re-enabled tenant may retain legacy data even without module_from.
    if (kbManagementBean.ingressType == ResourceIngressType.HARVEST) {
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName(tenantId)) {
        Map result = repairMissingMetadata()
        log.info("Package ingress metadata repair for tenant ${tenantId}: ${result}")
      }
    }
  }

  /**
   * Run in a tenant context. Repeat on enables so an interrupted repair can resume.
   * Package.source is the adapter's label (GOKb), NOT the RemoteKB name or ID.
   * Count inactive sources too: disabling a source does not remove its provenance.
   */
  Map repairMissingMetadata() {
    Map result = [created: 0, skipped: 0]
    if (kbManagementBean.ingressType != ResourceIngressType.HARVEST) return result

    List<String> sourceIds = RemoteKB.withNewTransaction {
      RemoteKB.executeQuery('''
        select kb.id from RemoteKB kb
        where kb.type = :type and kb.rectype = :rectype and kb.readonly = false
      ''', [type: GOKbOAIAdapter.name, rectype: RemoteKB.RECTYPE_PACKAGE])
    }
    if (sourceIds.size() != 1) {
      log.warn("Cannot backfill Harvest package ingress metadata: expected exactly one configured GOKb OAI package source, found ${sourceIds.size()}; review packages with missing metadata")
      return result
    }

    String lastId = ''
    while (true) {
      List<String> ids = Pkg.withNewTransaction {
        Pkg.executeQuery('''
          select p.id from Pkg p
          where p.id > :lastId and lower(p.source) = :source
            and not exists (select pim.id from PackageIngressMetadata pim where pim.resource.id = p.id)
          order by p.id
        ''', [lastId: lastId, source: 'gokb'], [max: BATCH_SIZE])
      }
      if (!ids) break

      // A fresh session per batch bounds Hibernate's cache as well as transaction size.
      Pkg.withNewSession {
        Pkg.withNewTransaction {
          ids.each { String id ->
            Pkg pkg = Pkg.lock(id)
            // Recheck after locking: another upgrade worker may have repaired this row.
            if (pkg && !PackageIngressMetadata.findByResource(pkg)) {
              List<String> uuids = pkg.approvedIdentifierOccurrences.findAll {
                it.identifier.ns.value == 'gokb_uuid'
              }.collect { it.identifier.value }.unique()
              if (uuids.size() == 1 && uuids[0]?.trim()) {
                new PackageIngressMetadata(resource: pkg, ingressType: ResourceIngressType.HARVEST,
                  ingressId: sourceIds[0]).save(failOnError: true)
                result.created++
              } else {
                result.skipped++
                log.warn("Cannot backfill package ingress metadata for ${id}: expected exactly one approved gokb_uuid identifier")
              }
            }
          }
        }
      }
      lastId = ids[-1]
    }
    return result
  }
}
