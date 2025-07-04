package com.k_int.accesscontrol.grails

import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import org.olf.erm.SubscriptionAgreement

import java.time.Duration

@Slf4j
@CurrentTenant
class AccessPolicyController extends AccessPolicyAwareController<AccessPolicyEntity> {
  AccessPolicyController() {
    super(AccessPolicyEntity)
  }
}
