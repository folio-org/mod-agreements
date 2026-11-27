package org.olf

import com.k_int.accesscontrol.grails.AccessPolicyAwareController
import org.olf.erm.Entitlement
import org.olf.general.events.EntitlementEventService

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import grails.gorm.transactions.Transactional



/**
 * Access to Entitlement resources
 */
@Slf4j
@CurrentTenant
class EntitlementController extends AccessPolicyAwareController<Entitlement> {

  EntitlementEventService entitlementEventService

  EntitlementController() {
    super(Entitlement)
  }

  /**
   * Wraps {@code super.update()} to publish a Kafka UPDATE domain event
   * carrying pre- and post- snapshots. Delegates snapshot capture and
   * publish to {@link EntitlementEventService} — this method's only job is
   * orchestration around {@code super.update()}.
   *
   * CREATE and DELETE are not hooked here; they are published from
   * {@code EntitlementEventListenerService} so that lines written through the
   * parent agreement are covered too.
   */
  @Transactional
  def update() {
    Map<String, Object> oldSnapshot = entitlementEventService.captureSnapshotAndDiscard(
      Entitlement.get(params.id))

    super.update()

    if (oldSnapshot == null) return
    if (response.status < 200 || response.status >= 300) return

    Map<String, Object> newSnapshot = entitlementEventService.captureSnapshotAndDiscard(
      Entitlement.get(params.id))


    entitlementEventService.publishUpdate(oldSnapshot, newSnapshot)
  }

  @Transactional(readOnly=true)
  def index(Integer max) {
    super.index(max)
  }

  @Transactional(readOnly=true)
  def show() {
    super.show()
  }

  def external() {
    Entitlement ent = new Entitlement ()
    ent.properties = params
    
    // Force external type.
    ent.type = 'external'
    
    // Ensure we have uppercase reference.
    ent.authority = ent.authority?.toUpperCase()
    respond ent
  }
}

