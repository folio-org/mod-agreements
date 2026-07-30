package org.olf.general.events

import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.olf.erm.Entitlement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

/**
 * Publishes DELETE events for {@link Entitlement} via a GORM PreDeleteEvent
 * listener.
 *
 * A controller hook on {@code EntitlementController.delete()} would miss the
 * only path users actually exercise: ui-agreements removes an agreement line
 * with {@code PUT /erm/sas/{id}} carrying {@code items: [{id, _delete: true}]}
 * (AgreementLineViewRoute.js), which orphan-deletes the row through
 * {@code SubscriptionAgreement.items cascade: 'all-delete-orphan'} without ever
 * entering that controller. Listening for the delete itself covers every path —
 * REST endpoint, orphan removal, and anything added later.
 *
 * Pre-delete state is free here: the entity is still in the session, so there
 * is no extra SELECT to capture the projection.
 *
 * {@code ApplicationListener} is intentionally generic + instanceof-filtered —
 * narrowing to {@code PreDeleteEvent} is unreliable under Spring's
 * type-erasure dispatch.
 */
@Slf4j
class EntitlementEventListenerService implements ApplicationListener<ApplicationEvent> {

  @Autowired EntitlementEventService entitlementEventService

  @Override
  void onApplicationEvent(ApplicationEvent event) {
    if (!(event instanceof AbstractPersistenceEvent)) return
    if (!(event instanceof PreDeleteEvent)) return
    if (!(event.entityObject instanceof Entitlement)) return

    Entitlement entitlement = (Entitlement) event.entityObject
    entitlementEventService.publishDelete(
      entitlementEventService.captureProjection(entitlement))
  }
}