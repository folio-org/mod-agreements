package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants

class ResourceRematchJob extends PersistentJob implements MultiTenant<ResourceRematchJob>{

  final Closure work = {
    log.info "Running Resource Rematch Job"
    kbManagementService.runRematchProcess()
  }
}
