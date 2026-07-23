package org.olf.general.events

import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.olf.erm.SubscriptionAgreement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

/**
 * Publishes CREATE events for {@link SubscriptionAgreement} via a GORM
 * PostInsertEvent listener. Covers all save paths (REST, clones, background
 * jobs) without touching {@code AccessPolicyAwareController.save()}.
 *
 * {@code ApplicationListener} is intentionally generic + instanceof-filtered —
 * narrowing to {@code PostInsertEvent} is unreliable under Spring's
 * type-erasure dispatch.
 */
@Slf4j
class AgreementEventListenerService implements ApplicationListener<ApplicationEvent> {

  @Autowired AgreementEventService agreementEventService

  @Override
  void onApplicationEvent(ApplicationEvent event) {
    if (!(event instanceof AbstractPersistenceEvent)) return
    if (!(event instanceof PostInsertEvent)) return
    if (!(event.entityObject instanceof SubscriptionAgreement)) return

    agreementEventService.publishCreate((SubscriptionAgreement) event.entityObject)
  }
}
