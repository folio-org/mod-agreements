package org.olf


import java.time.Instant

import org.olf.kb.Pkg
import org.olf.kb.metadata.PackageIngressMetadata
import org.olf.kb.metadata.ResourceIngressType

import org.olf.general.jobs.PackageTriggerResyncJob

import org.olf.general.pushKB.PushKBClient

import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class PackageSyncService {

  PackagePullService packagePullService

  public Map controlSyncStatus(List<String> packageIds, boolean syncStatusBool) {
    // Convert each package in packageId to syncStatus
    boolean returnVal = false;
    Map returnObj = [
        packagesUpdated: 0,
        packagesSkipped: 0,
        success        : false
    ];

    Pkg.withNewTransaction {
      packageIds.each { String pkgId ->
        Pkg pkg = Pkg.get(pkgId);
        if (pkg.syncContentsFromSource != syncStatusBool) {
          pkg.syncContentsFromSource = syncStatusBool;
          pkg.save(failOnError: true);
          returnObj.packagesUpdated++

          if (syncStatusBool == true) {
            PackageTriggerResyncJob job = new PackageTriggerResyncJob([
                name: "PackageTriggerResyncJob for ${pkgId}, ${Instant.now()}",
                packageId: pkgId
            ])

            job.setStatusFromString('Queued')
            job.save(failOnError: true)
          }
        } else {
          returnObj.packagesSkipped++
        }
      }
      returnObj.success = true;
    }

    return returnObj
  }

  /*
   * Read ingress metadata and request a resync through the appropriate source.
   * This runs after the synchronization status change has committed.
   */

  public void resyncPackage(String packageId) {
    Pkg.withTransaction {
      Pkg pkg = Pkg.get(packageId);

      if (pkg != null) {
        PackageIngressMetadata pim = PackageIngressMetadata.executeQuery("""
          SELECT pim FROM PackageIngressMetadata pim
          WHERE pim.resource.id = :pid
        """.toString(), [pid: packageId])[0];

        if (pim != null) {
          switch (pim.ingressType) {
            case ResourceIngressType.PUSHKB:
              resyncPushKBPackage(pkg, pim);
              break;
            case ResourceIngressType.HARVEST:
              if (pkg.syncContentsFromSource == false) {
                log.info("Skipping OAI resync for paused package ${packageId}")
              } else {
                // Reuse a pending pull if enabling and a manual request overlap.
                String jobId = packagePullService.enqueue(packageId, true)
                log.info("OAI resync for package ${packageId} uses pull job ${jobId}")
              }
              break;
            default:
              log.error("Package resync is not available for packages with ingress type: ${pim.ingressType}")
              break;
          }
        } else {
          log.error("There is no package ingress metadata for package \"${pkg.name}\" with id: ${packageId}")
        }
      } else {
        log.error("Could not obtain package with id: ${packageId}")
      }
    }
  }
  // Break out logic for specifically resyncing a pkg with ingressType == PUSH_KB
  public void resyncPushKBPackage(Pkg pkg, PackageIngressMetadata pim) {
    if (
        pim.contentIngressId != null &&
        pim.contentIngressUrl != null
    ) {
      String gokbUUID = pkg.approvedIdentifierOccurrences.find { io ->
        io.identifier.ns.value == "gokb_uuid"
      }.identifier.value;

      if (gokbUUID != null) {
        // FINALLY actually do the package resync
        PushKBClient client = new PushKBClient(pim.contentIngressUrl);
        client.temporaryPushTask(pim.contentIngressId, gokbUUID);
      } else {
        log.error("Cannot currently trigger resync for package \"${pkg.name}\" with id: ${pkg.id}, could not find identifier with namespace \"gokb_uuid\"")
      }
    } else {
      log.error("Cannot currently trigger resync for package \"${pkg.name}\" with id: ${pkg.id}, PackageIngressMetadata does not yet include enough information to trigger PushKB temporaryPushTask creation")
    }
  }
}