package org.olf.general.events

import groovy.util.logging.Slf4j
import org.olf.erm.Entitlement
import org.springframework.beans.factory.annotation.Autowired

/**
 * Single seam for {@link Entitlement} domain events — projection capture,
 * envelope construction, tenant resolution, topic naming, post-commit publish.
 * Called from {@code EntitlementController.delete()}.
 *
 * Callers MUST invoke from within an active tx — the publish method registers
 * a post-commit sync on that tx (see {@link EventPublisherService}).
 */
@Slf4j
class EntitlementEventService {

  private static final String ENTITY = 'entitlement'

  @Autowired EventPublisherService eventPublisherService
  @Autowired TopicNameResolver     topicNameResolver
  @Autowired TenantContext         tenantContext

  /**
   * Build the lean pre-delete projection while the row still exists.
   *
   * Unlike {@code AgreementEventService.captureSnapshotAndDiscard}, this
   * deliberately does NOT {@code discard()} the entity. That discard exists to
   * shed dirty session state left by walking lazy hasMany collections; this
   * projection reads only scalars and two many-to-ones, so there is nothing to
   * shed — and detaching an instance that is about to be deleted would only
   * get in the way.
   */
  Map<String, Object> captureProjection(Entitlement ent) {
    if (ent == null) return null
    try {
      return EntitlementDeleteProjection.of(ent)
    } catch (Exception e) {
      log.error("Failed to build DELETE projection for Entitlement ${ent.id}", e)
      return null
    }
  }

  void publishDelete(Map<String, Object> oldProjection) {
    if (oldProjection == null) return
    try {
      DomainEvent<Map> event = DomainEvent.deleteEvent(oldProjection, tenantContext.currentTenant())
      eventPublisherService.publishAfterCommit(topicNameResolver.topicFor(ENTITY), event)
    } catch (Exception e) {
      log.error("Failed to enqueue DELETE event for Entitlement id=${oldProjection?.id}", e)
    }
  }
}