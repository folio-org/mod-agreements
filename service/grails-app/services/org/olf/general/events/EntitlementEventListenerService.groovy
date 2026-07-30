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