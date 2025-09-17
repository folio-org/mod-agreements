package org.olf.general.jobs

import grails.gorm.MultiTenant

class GokbResourceEntitlementJob extends PersistentJob implements MultiTenant<GokbResourceEntitlementJob>{

  final Closure getWork() {
    final Closure theWork = { final String jobId, final String tenantId ->
      log.info "Attempt to process external gokb entitlements"
      entitlementService.processExternalEntitlements()
    }.curry( this.id )
    theWork
  }


  static mapping = {
    table 'gokb_resource_entitlement_job'
    version false
  }
}
