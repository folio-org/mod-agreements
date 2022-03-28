package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants

class NaiveMatchKeyAssignmentJob extends PersistentJob implements MultiTenant<NaiveMatchKeyAssignmentJob>{

  final Closure work = {
    log.info "Running Naive Match Key Assignment Job"
    matchKeyService.generateMatchKeys()
  }
}
