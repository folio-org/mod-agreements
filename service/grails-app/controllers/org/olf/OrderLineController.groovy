package org.olf

import com.k_int.accesscontrol.grails.AccessPolicyAwareController
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.erm.Entitlement

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import grails.gorm.transactions.Transactional
import org.olf.erm.OrderLine


/**
 * Access to OrderLine resources
 * FIXME THIS IS NOT MEANT FOR ACTUAL MERGE, BUT AS AN EXAMPLE OF FETCHING Resources owned by something owned by something owned by something policyControlled.
 */
@Slf4j
@CurrentTenant
class OrderLineController extends AccessPolicyAwareController<OrderLine> {
  OrderLineController() {
    super(OrderLine)
  }
}

