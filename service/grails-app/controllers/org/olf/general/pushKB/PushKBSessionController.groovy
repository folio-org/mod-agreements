package org.olf.general.pushKB

import com.k_int.okapi.OkapiTenantAwareController

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class PushKBSessionController extends OkapiTenantAwareController<PushKBSession>  {
  PushKBSessionController() {
    super(PushKBSession)
  }
}

