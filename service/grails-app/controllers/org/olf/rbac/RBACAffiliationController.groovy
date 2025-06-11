package org.olf.rbac

import com.k_int.okapi.OkapiTenantAwareController

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class RBACAffiliationController extends OkapiTenantAwareController<RBACAffiliation>  {
  RBACAffiliationController() {
    super(RBACAffiliation)
  }
}

