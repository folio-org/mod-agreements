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

  public testRequestContext() {
    List<String> policySql = getPolicySql();

    long beforeLookup = System.nanoTime();
    respond doTheLookup(SubscriptionAgreement) {
      policySql.each {psql -> {
        sqlRestriction(psql)
      }};
    }


    long endTime = System.nanoTime();
    Duration lookupToEnd = Duration.ofNanos(endTime - beforeLookup);
    log.debug("LOGDEBUG query time: ${lookupToEnd}")
    return null // Probably not necessary, prevent fallthrough after response
  }
}
