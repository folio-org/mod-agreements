package org.olf.general.events

import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.olf.erm.Entitlement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

/**
 * Publishes CREATE and DELETE events for {@link Entitlement} via GORM
 * PostInsertEvent / PreDeleteEvent listeners. Covers every save and delete
 * path — the direct {@code /erm/entitlements} endpoints, lines nested under
 * {@code /erm/sas}, agreement clones, background jobs — without touching
 * {@code AccessPolicyAwareController}. UPDATE cannot be served this way (the
 * pre-update state is not reconstructable here) and lives in
 * {@code EntitlementController.update()}.
 *
 * {@code ApplicationListener} is intentionally generic + instanceof-filtered —
 * narrowing to a specific event type is unreliable under Spring's type-erasure
 * dispatch.
 */
@Slf4j
class EntitlementEventListenerService implements ApplicationListener<ApplicationEvent> {

  @Autowired EntitlementEventService entitlementEventService

  @Override
  void onApplicationEvent(ApplicationEvent event) {
    if (!(event instanceof AbstractPersistenceEvent)) return
    if (!(event.entityObject instanceof Entitlement)) return

    Entitlement entitlement = (Entitlement) event.entityObject

    if (event instanceof PostInsertEvent) {
      entitlementEventService.publishCreate(entitlement)
    } else if (event instanceof PreDeleteEvent) {
      entitlementEventService.publishDelete(
        entitlementEventService.captureProjection(entitlement))
    }
  }
}