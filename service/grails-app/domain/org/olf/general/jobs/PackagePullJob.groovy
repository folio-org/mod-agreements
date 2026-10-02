package org.olf.general.jobs

import grails.gorm.MultiTenant

class PackagePullJob extends PersistentJob implements MultiTenant<PackagePullJob> {
  String packageId
  String remoteKbId
  boolean sourceClaimed = false

  final Closure getWork() {
    final String jobId = id
    return { String tenantId -> packagePullService.pull(jobId) }
  }

  final Closure getOnInterrupted() {
    return { String tenantId, String jobId -> packagePullService.releaseSource(jobId) }
  }

  static constraints = {
    packageId nullable: false, blank: false
    remoteKbId nullable: false, blank: false
  }

  static mapping = {
    table 'package_pull_job'
    version false
    packageId column: 'package_id'
    remoteKbId column: 'remote_kb_id'
    sourceClaimed column: 'source_claimed'
  }
}
