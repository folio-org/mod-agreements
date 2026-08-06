package org.olf.general.events

import groovy.util.logging.Slf4j
import org.olf.erm.Entitlement
import org.springframework.beans.factory.annotation.Autowired

/**
 * Single seam for {@link Entitlement} domain events — snapshot / projection
 * capture, envelope construction, tenant resolution, topic naming, post-commit
 * publish. Consolidates the CREATE and DELETE paths (called from
 * {@code EntitlementEventListenerService}) and the UPDATE path (called from
 * {@code EntitlementController}).
 *
 * Callers MUST invoke from within an active tx — the publish methods register
 * a post-commit sync on that tx (see {@link EventPublisherService}).
 */
@Slf4j
class EntitlementEventService {

  private static final String ENTITY = 'entitlement'

  @Autowired EventPublisherService eventPublisherService
  @Autowired TopicNameResolver     topicNameResolver
  @Autowired TenantContext         tenantContext

  /**
   * Snapshot the entitlement then {@code discard()} it so any dirty session
   * state left by walking the lazy hasMany collections cannot participate in a
   * subsequent flush — three of the four cascade {@code all-delete-orphan},
   * and leaving them dirty surfaces as a StaleStateException at
   * {@code super.update()} flush time.
   */
  Map<String, Object> captureSnapshotAndDiscard(Entitlement ent) {
    if (ent == null) return null
    Map<String, Object> snapshot = EntitlementSnapshotBuilder.snapshot(ent)
    ent.discard()
    return snapshot
  }

  void publishCreate(Entitlement ent) {
    if (ent == null) return
    try {
      Map<String, Object> snapshot = EntitlementSnapshotBuilder.snapshot(ent)
      DomainEvent<Map> event = DomainEvent.createEvent(snapshot, tenantContext.currentTenant())
      eventPublisherService.publishAfterCommit(topicNameResolver.topicFor(ENTITY), event)
    } catch (Exception e) {
      log.error("Failed to enqueue CREATE event for Entitlement ${ent?.id}", e)
    }
  }

  void publishUpdate(Map<String, Object> oldSnapshot, Map<String, Object> newSnapshot) {
    if (oldSnapshot == null || newSnapshot == null) return
    try {
      DomainEvent<Map> event = DomainEvent.updateEvent(oldSnapshot, newSnapshot, tenantContext.currentTenant())
      eventPublisherService.publishAfterCommit(topicNameResolver.topicFor(ENTITY), event)
    } catch (Exception e) {
      log.error("Failed to enqueue UPDATE event for Entitlement id=${newSnapshot?.id}", e)
    }
  }

  /**
   * Build the lean pre-delete projection while the row still exists.
   *
   * Unlike {@link #captureSnapshotAndDiscard}, this deliberately does NOT
   * {@code discard()} the entity. That discard exists to
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