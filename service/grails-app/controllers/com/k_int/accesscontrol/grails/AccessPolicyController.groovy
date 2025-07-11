package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyTypeIds
import com.k_int.accesscontrol.core.PolicyRestriction
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 *  Controller for managing access policies.
 * This controller extends the PolicyEngineController to provide specific functionality for access policies.
 * It includes methods to retrieve policy IDs based on different restrictions.
 */
@Slf4j
@CurrentTenant
class AccessPolicyController extends PolicyEngineController<AccessPolicyEntity> {
  AccessPolicyController() {
    super(AccessPolicyEntity)
  }

  /**
   * Retrieves the PolicyEngine instance configured for the current request.
   * This method builds the FolioClientConfig based on environment variables or Grails application configuration.
   * It also resolves the tenant and patron information.
   *
   * @return A PolicyEngine instance configured for the current request.
   */
  private List<AccessPolicyTypeIds> getPolicyIds(PolicyRestriction restriction) {
    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    return policyEngine.getPolicyIds(grailsHeaders, restriction)
  }

  /**
   * Retrieves the policy IDs for the READ restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of read policy IDs.
   */
  @Transactional
  def getReadPolicyIds() {
    log.trace("AccessPolicyController::getReadPolicyIds")

    respond([readPolicyIds: getPolicyIds(PolicyRestriction.READ)]) // FIXME should be a proper response here
  }

  /**
   * Retrieves the policy IDs for the UPDATE restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of update policy IDs.
   */
  @Transactional
  def getUpdatePolicyIds() {
    log.trace("AccessPolicyController::getUpdatePolicyIds")

    respond([updatePolicyIds: getPolicyIds(PolicyRestriction.UPDATE)]) // FIXME should be a proper response here
  }

  /**
   * Retrieves the policy IDs for the CREATE restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of create policy IDs.
   */
  @Transactional
  def getCreatePolicyIds() {
    log.trace("AccessPolicyController::getCreatePolicyIds")

    respond([createPolicyIds: getPolicyIds(PolicyRestriction.CREATE)]) // FIXME should be a proper response here
  }

  /**
   * Retrieves the policy IDs for the DELETE restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of delete policy IDs.
   */
  @Transactional
  def getDeletePolicyIds() {
    log.trace("AccessPolicyController::getDeletePolicyIds")

    respond([deletePolicyIds: getPolicyIds(PolicyRestriction.DELETE)]) // FIXME should be a proper response here
  }

  /**
   * Retrieves the policy IDs for the CLAIM restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of claim policy IDs.
   */
  @Transactional
  def getClaimPolicyIds() {
    log.trace("AccessPolicyController::getClaimPolicyIds")

    respond([claimPolicyIds: getPolicyIds(PolicyRestriction.CLAIM)]) // FIXME should be a proper response here
  }
}
