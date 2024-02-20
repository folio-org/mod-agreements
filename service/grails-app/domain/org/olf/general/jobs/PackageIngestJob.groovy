package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants

class PackageIngestJob extends PersistentJob implements MultiTenant<PackageIngestJob>{

  final Closure work = {
    log.info "Running Package Ingest Job"
    kbHarvestService.triggerPackageCacheUpdate()
  }

  final Closure onInterrupted = {String tenantId, String jobId ->
		// We need to update the harvest job and set syncStatus to something other than in-process
		// Probably we should just update all rkb.syncStatus = 'idle'
    // This work is handled in kbHarvestService
    log.info("onInterrupted called for job(${jobId}) in tenant(${tenantId}).")
    kbHarvestService.handleInterruptedJob()
  }
}
