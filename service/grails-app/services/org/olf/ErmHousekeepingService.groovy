package org.olf

import grails.gorm.transactions.Transactional

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Transactional
public class ErmHousekeepingService {

  def coverageService
  def entitlementLogService
  def subscriptionAgreementCleanupService
  def remoteKbCleanupService

  public void triggerHousekeeping() {
    entitlementLogService.triggerUpdate();

    // A process to ensure the correct start/end date is stored per agreement
    subscriptionAgreementCleanupService.triggerDateCleanup();

    // A process to ensure any unused Org records are deleted
    subscriptionAgreementCleanupService.triggerOrgsCleanup();
    
    remoteKbCleanupService.checkLocal();
  }
}
