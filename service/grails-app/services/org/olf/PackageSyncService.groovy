package org.olf

import org.olf.kb.Pkg
import org.olf.kb.metadata.PackageIngressMetadata
import org.olf.kb.metadata.ResourceIngressType

import org.olf.general.jobs.PackageTriggerResyncJob

import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@CurrentTenant
class PackageSyncService {

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

          new PackageTriggerResyncJob([
              packageId: pkgId
          ]).save(failOnError: true)
        } else {
          returnObj.packagesSkipped++
        }
      }
      returnObj.success = true;
    }

    return returnObj
  }

  /*
   * Handed a package Id, read ingress metadata and perform resync-package logic, either
   * warning that the package ingress is not type pushKB, or actually making the call to pushKB
   * to setup a temporary push task
   */

  public void resyncPackage(String packageId) {
    Package.withTransaction {
      Package pkg = Package.get(packageId);

      if (pkg != null) {
        PackageIngressMetadata pim = PackageIngressMetadata.executeQuery("""
          SELECT pim FROM PackageIngressMetadata pim
          WHERE pim.resource.id = :pid
        """.toString(), [pid: packageId]);

        if (pim != null) {
          switch (pim.ingressType) {
            case ResourceIngressType.PUSHKB:
              resyncPushKBPackage(pkg, pim);
              break;
            case ResourceIngressType.HARVEST:
              log.error("Package resync is not currently automatically triggered for packages with ingress type: ${pim.ingressType}. A resync will require either the package to come in via PushKB, or a cursor reset on remoteKb ${pim.ingressId} to be done manually.")
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
  public void resyncPushKBPackage(Package pkg, PackageIngressMetadata pim) {
    if (
        pim.contentIngressId != null &&
            pim.contentIngressUrl != null
    ) {
      String gokbUUID = pkg.approvedIdentifierOccurrences.find { io ->
        io.identifier.ns.value == "gokb_uuid"
      }.value;

      if (gokbUUID != null) {
        // FINALLY actually do the package resync
        PushKBClient client = new PushKBClient(pim.contentIngressUrl);
        client.temporaryPushTask(contentIngressId, gokbUUID);
      } else {
        log.error("Cannot currently trigger resync for package \"${pkg.name}\" with id: ${packageId}, could not find identifier with namespace \"gokb_uuid\"")
      }
    } else {
      log.error("Cannot currently trigger resync for package \"${pkg.name}\" with id: ${packageId}, PackageIngressMetadata does not yet include enough information to trigger PushKB temporaryPushTask creation")
    }
  }
}