package org.olf.general.jobs

import grails.gorm.MultiTenant

class EHoldingsEntitlementSyncJob extends PersistentJob implements MultiTenant<EHoldingsEntitlementSyncJob>{

  final Closure work = {
    log.info "Attempt to process external eHoldings entitlements"
    eholdingsService.processEHoldingsEntitlements()
  }
}