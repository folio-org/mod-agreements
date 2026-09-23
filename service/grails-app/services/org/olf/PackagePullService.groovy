package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import org.olf.dataimport.internal.KBManagementBean
import org.olf.general.StringUtils
import org.olf.general.jobs.PackagePullJob
import org.olf.kb.PackagePullException
import org.olf.kb.Pkg
import org.olf.kb.RemoteKB
import org.olf.kb.adapters.GOKbOAIAdapter
import org.olf.kb.metadata.ResourceIngressType

/** One-off OAI package retrieval. Never advances the incremental harvest cursor. */
@Slf4j
@CurrentTenant
class PackagePullService {
  KBManagementBean kbManagementBean
  IngressMetadataService ingressMetadataService
  KnowledgeBaseCacheService knowledgeBaseCacheService
  PackageIngestService packageIngestService

  String enqueue(String packageId) {
    if (!packageId?.trim()) {
      throw new PackagePullException(400, 'packageId is required')
    }
    PackagePullJob.withNewTransaction {
      // Serialize requests for this package so duplicate jobs cannot be queued.
      Pkg pkg = Pkg.lock(packageId)
      Map target = validateTarget(pkg)
      if (PackagePullJob.executeQuery("""
        select j.id from PackagePullJob j
        where j.packageId = :packageId and j.status.value in ('queued', 'in_progress')
      """, [packageId: packageId])) {
        throw new PackagePullException(409, 'A pull for this package is already queued or running')
      }
      PackagePullJob job = new PackagePullJob(
        name: StringUtils.truncate("OAI pull for ${pkg.name}"), packageId: pkg.id, remoteKbId: target.remoteKbId
      )
      job.setStatusFromString('Queued')
      job.save(failOnError: true, flush: true)
      return job.id
    }
  }

  /** Called inside a tenant transaction; return scalar values only. */
  private Map validateTarget(Pkg pkg) {
    if (kbManagementBean.ingressType != ResourceIngressType.HARVEST) {
      throw new PackagePullException(409, 'Package pulling requires Harvest ingress mode')
    }
    if (!pkg) {
      throw new PackagePullException(404, 'Package not found')
    }
    if (pkg.syncContentsFromSource == false) {
      throw new PackagePullException(409, 'Package synchronization is paused')
    }
    def metadata = ingressMetadataService.getPIMFromPackageId(pkg.id)
    if (metadata?.ingressType != ResourceIngressType.HARVEST) {
      throw new PackagePullException(409, 'Package must have Harvest ingress metadata')
    }
    RemoteKB source = RemoteKB.get(metadata.ingressId)
    if (!source || source.type != GOKbOAIAdapter.name || source.rectype != RemoteKB.RECTYPE_PACKAGE ||
        !source.active || !source.uri || source.readonly) {
      throw new PackagePullException(409, 'Package requires an active, writable GOKB OAI package source')
    }
    def identifiers = pkg.approvedIdentifierOccurrences.findAll {
      it.identifier.ns.value == 'gokb_uuid'
    }.collect { it.identifier.value }.unique()
    if (identifiers.size() != 1 || !identifiers[0]?.trim()) {
      throw new PackagePullException(409, 'Package requires exactly one approved gokb_uuid identifier')
    }
    return [remoteKbId: source.id, sourceName: source.name, baseUrl: source.uri,
            trustedSourceTI: source.trustedSourceTI, identifier: identifiers[0]]
  }

  void pull(String jobId) {
    Map target = PackagePullJob.withNewTransaction {
      PackagePullJob job = PackagePullJob.lock(jobId)
      if (!job) throw new IllegalArgumentException('Package pull job not found')
      job.refresh()
      Map current = validateTarget(Pkg.get(job.packageId))
      if (current.remoteKbId != job.remoteKbId) {
        throw new PackagePullException(409, 'Package source changed after the pull was queued')
      }
      RemoteKB source = RemoteKB.lock(job.remoteKbId)
      // Validation has already loaded this source; refresh after acquiring the lock.
      source.refresh()
      if (source.syncStatus == 'in-process') {
        throw new PackagePullException(409, 'RemoteKB is already being harvested; retry after it finishes')
      }
      source.syncStatus = 'in-process'
      source.save(failOnError: true, flush: true)
      job.sourceClaimed = true
      job.save(failOnError: true, flush: true)
      current.packageId = job.packageId
      return current
    }
    try {
      Map result = new GOKbOAIAdapter().importPackage(target + [beforeIngest: { packageData ->
        // Recheck after the HTTP request too: settings may have changed while downloading.
        Pkg.withNewSession {
          Pkg.withNewTransaction {
            Map current = validateTarget(Pkg.get(target.packageId))
            if (current != target.subMap(current.keySet())) {
              throw new PackagePullException(409, 'Package source or identifier changed during retrieval')
            }
            if (packageIngestService.lookupPkg(packageData)?.id != target.packageId) {
              throw new PackagePullException(409, 'OAI record does not match the requested local package')
            }
          }
        }
      }], knowledgeBaseCacheService)
      log.info("OAI package pull completed for ${target.packageId}: ${result}")
    } finally {
      releaseSource(jobId)
    }
  }

  void releaseSource(String jobId) {
    PackagePullJob.withNewTransaction {
      PackagePullJob job = PackagePullJob.lock(jobId)
      job?.refresh()
      if (job?.sourceClaimed) {
        RemoteKB source = RemoteKB.lock(job.remoteKbId)
        if (source) {
          source.syncStatus = 'idle'
          source.save(failOnError: true, flush: true)
        }
        job.sourceClaimed = false
        job.save(failOnError: true, flush: true)
      }
    }
  }
}
