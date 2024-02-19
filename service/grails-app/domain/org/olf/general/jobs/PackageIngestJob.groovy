package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants

class PackageIngestJob extends PersistentJob implements MultiTenant<PackageIngestJob>{

  final Closure work = {
    log.info "Running Package Ingest Job"
    kbHarvestService.triggerPackageCacheUpdate()
  }

	@Override
  public void handleInterruptedJob() {
		// We need to update the harvest job and set syncStatus to something other than in-process
		// Probably we should just update all rkb.syncStatus = 'idle'
    log.info "Requesting that kbHarvestService resets syncStatus to idle on any interrupted KB connections"
    kbHarvestService.handleInterruptedJob()
  }

}
