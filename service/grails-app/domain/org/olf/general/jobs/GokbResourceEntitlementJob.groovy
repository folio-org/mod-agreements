package org.olf.general.jobs

import grails.gorm.MultiTenant

class GokbResourceEntitlementJob extends PersistentJob implements MultiTenant<GokbResourceEntitlementJob>{
  String packageId

  final Closure getWork() {
    final Closure theWork = { final String jobId, final String tenantId ->
      log.info "Attempt to retrigger package title sync"
      packageSyncService.resyncPackage(packageId)
    }.curry( this.id )
    theWork
  }

  static constraints = {
    packageId     nullable:false
  }

  static mapping = {
    table 'gokb_resource_entitlement_job'
    version false
    packageId     column: 'package_id'
  }
}
