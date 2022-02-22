package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants
import java.time.Instant

class ResourceRematchJob extends PersistentJob implements MultiTenant<ResourceRematchJob>{
  Instant since

  static mapping = {
    since column: 'since'
  }

  final Closure work = {
    log.info "Running Resource Rematch Job"
    kbManagementService.runRematchProcess(since)
  }
}
