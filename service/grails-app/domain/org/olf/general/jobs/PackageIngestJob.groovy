package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants

class PackageIngestJob extends PersistentJob implements MultiTenant<PackageIngestJob>{

  final Closure work = {
    log.info "Running Package Ingest Job"
    kbHarvestService.triggerPackageCacheUpdate()
  }
}
