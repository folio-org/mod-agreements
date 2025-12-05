package org.olf

import com.k_int.accesscontrol.grails.AccessPolicyAwareController
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import org.olf.erm.Entitlement
import org.olf.erm.OrderLine

/**
 * FIXME this isn't something we ACTUALLY want on our application, here for hierarchy testing in AccessControl
 * Access to OrderLine resources
 */
@Slf4j
@CurrentTenant
class OrderLineController extends AccessPolicyAwareController<OrderLine> {
  OrderLineController() {
    super(OrderLine)
  }
}

