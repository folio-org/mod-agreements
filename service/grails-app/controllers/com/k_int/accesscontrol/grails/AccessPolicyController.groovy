package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyTypeIds
import com.k_int.accesscontrol.core.PolicyRestriction
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class AccessPolicyController extends PolicyEngineController<AccessPolicyEntity> {
  AccessPolicyController() {
    super(AccessPolicyEntity)
  }

  private List<AccessPolicyTypeIds> getPolicyIds(PolicyRestriction restriction) {
    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    return policyEngine.getPolicyIds(grailsHeaders, restriction)
  }

  @Transactional
  def getReadPolicyIds() {
    log.trace("AccessPolicyController::getReadPolicyIds")

    respond([readPolicyIds: getPolicyIds(PolicyRestriction.READ)]) // FIXME should be a proper response here
  }

  @Transactional
  def getUpdatePolicyIds() {
    log.trace("AccessPolicyController::getUpdatePolicyIds")

    respond([updatePolicyIds: getPolicyIds(PolicyRestriction.UPDATE)]) // FIXME should be a proper response here
  }

  @Transactional
  def getCreatePolicyIds() {
    log.trace("AccessPolicyController::getCreatePolicyIds")

    respond([createPolicyIds: getPolicyIds(PolicyRestriction.CREATE)]) // FIXME should be a proper response here
  }

  @Transactional
  def getDeletePolicyIds() {
    log.trace("AccessPolicyController::getDeletePolicyIds")

    respond([deletePolicyIds: getPolicyIds(PolicyRestriction.DELETE)]) // FIXME should be a proper response here
  }

  @Transactional
  def getClaimPolicyIds() {
    log.trace("AccessPolicyController::getClaimPolicyIds")

    respond([claimPolicyIds: getPolicyIds(PolicyRestriction.CLAIM)]) // FIXME should be a proper response here
  }
}
