package org.olf.general.jobs

import grails.gorm.MultiTenant

class ExternalEntitlementEholdingsSyncJob extends PersistentJob implements MultiTenant<ExternalEntitlementEholdingsSyncJob>{

  final Closure work = {
    log.info "Attempt to process external eHoldings entitlements"
    eholdingsService.processExternalEntitlementsEholdings()
  }
}