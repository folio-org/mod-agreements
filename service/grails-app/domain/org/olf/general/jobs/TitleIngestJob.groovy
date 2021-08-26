package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants

class TitleIngestJob extends PersistentJob implements MultiTenant<TitleIngestJob>{

  final Closure work = {
    log.info "Running Title Ingest Job"
    kbHarvestService.triggerTitleCacheUpdate()
  }
}
