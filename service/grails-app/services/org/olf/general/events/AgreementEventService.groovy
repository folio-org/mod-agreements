package org.olf.general.events

import groovy.util.logging.Slf4j
import org.olf.erm.SubscriptionAgreement
import org.springframework.beans.factory.annotation.Autowired

/**
 * Single seam for {@link SubscriptionAgreement} domain events — snapshot
 * capture, envelope construction, tenant resolution, topic naming,
 * post-commit publish. Consolidates the CREATE path (called from
 * {@code AgreementEventListenerService}) and the UPDATE path (called from
 * {@code SubscriptionAgreementController}).
 *
 * Callers MUST invoke from within an active tx — the publish methods
 * register a post-commit sync on that tx (see {@link EventPublisherService}).
 */
@Slf4j
class AgreementEventService {

  private static final String ENTITY = 'agreement'

  @Autowired EventPublisherService eventPublisherService
  @Autowired TopicNameResolver     topicNameResolver
  @Autowired TenantContext         tenantContext

  /**
   * Snapshot the agreement then {@code discard()} it so any dirty session
   * state left by walking lazy collections cannot participate in a subsequent
   * flush.
   */
  Map<String, Object> captureSnapshotAndDiscard(SubscriptionAgreement sa) {
    if (sa == null) return null
    Map<String, Object> snapshot = AgreementSnapshotBuilder.snapshot(sa)
    sa.discard()
    return snapshot
  }

  void publishCreate(SubscriptionAgreement sa) {
    if (sa == null) return
    try {
      Map<String, Object> snapshot = AgreementSnapshotBuilder.snapshot(sa)
      DomainEvent<Map> event = DomainEvent.createEvent(snapshot, tenantContext.currentTenant())
      eventPublisherService.publishAfterCommit(topicNameResolver.topicFor(ENTITY), event)
    } catch (Exception e) {
      log.error("Failed to enqueue CREATE event for SubscriptionAgreement ${sa?.id}", e)
    }
  }

  void publishUpdate(Map<String, Object> oldSnapshot, Map<String, Object> newSnapshot) {
    if (oldSnapshot == null || newSnapshot == null) return
    try {
      DomainEvent<Map> event = DomainEvent.updateEvent(oldSnapshot, newSnapshot, tenantContext.currentTenant())
      eventPublisherService.publishAfterCommit(topicNameResolver.topicFor(ENTITY), event)
    } catch (Exception e) {
      log.error("Failed to enqueue UPDATE event for SubscriptionAgreement id=${newSnapshot?.id}", e)
    }
  }
}
