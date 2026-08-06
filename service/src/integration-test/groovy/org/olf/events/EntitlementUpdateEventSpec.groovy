package org.olf.events

import java.time.LocalDate

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Stepwise

import org.olf.kb.PackageContentItem

@Slf4j
@Integration
@Stepwise
class EntitlementUpdateEventSpec extends AgreementEventBaseSpec {

  @Shared
  String pciId

  @Shared
  String agreementId

  @Shared
  String topic

  void 'Load the package fixture used for internal agreement lines'() {
    when: 'a package is imported and a PackageContentItem resolved'
      Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
      pciId = withTenant {
        PackageContentItem.executeQuery('SELECT pci.id FROM PackageContentItem pci')[0]
      }
      topic = topicFor('entitlement')

    and: 'an agreement to hang the lines off'
      agreementId = createAgreement("kafka-ent-update-owner-${System.currentTimeMillis()}").id

    then: 'we have something to entitle'
      result.packageImported == true
      pciId != null
      agreementId != null
  }

  void 'PUT /erm/entitlements/{id} emits an UPDATE event with pre- and post- snapshots'() {
    given: 'an existing line that is enabled'
      Map created = createLine([
        resource  : [id: pciId],
        activeFrom: today.toString(),
        enabled   : true
      ])
      created.enabled == true

    when: 'we PUT enabled=false'
      Map updated = doPut("/erm/entitlements/${created.id}", [enabled: false]) as Map
      Map event = pollForUpdateEvent(topic, created.id as String, 15_000L)

    then: 'the PUT succeeded'
      updated.id == created.id
      updated.enabled == false

    and: 'an UPDATE envelope landed with both snapshots'
      event != null
      event.type == 'UPDATE'
      event.tenant == tenantId
      event.eventId != null
      event.eventTs != null
      event.old != null
      event.new != null

    and: 'the id never changes across an update'
      event.old.id == created.id
      event.new.id == created.id

    and: 'the enabled diff is observable (also guards the same-instance-mutation gotcha)'
      event.old.enabled == true
      event.new.enabled == false

    and: 'the parent agreement travels on both sides'
      event.old.owner?.id == agreementId
      event.new.owner?.id == agreementId
  }

  void 'an active-window shift is observable on both sides'() {
    given: 'an existing line with a known active window'
      LocalDate originalTo = today.plusDays(1)
      LocalDate shiftedFrom = today.plusDays(2)
      LocalDate shiftedTo = today.plusDays(3)
      Map created = createLine([
        resource  : [id: pciId],
        activeFrom: today.toString(),
        activeTo  : originalTo.toString()
      ])

    when: 'we shift the window'
      doPut("/erm/entitlements/${created.id}", [
        activeFrom: shiftedFrom.toString(),
        activeTo  : shiftedTo.toString()
      ])
      Map event = pollForUpdateEvent(topic, created.id as String, 15_000L)

    then: 'all four values are present in the one event'
      event != null
      event.old.activeFrom == today.toString()
      event.old.activeTo == originalTo.toString()
      event.new.activeFrom == shiftedFrom.toString()
      event.new.activeTo == shiftedTo.toString()
  }

  void 'the resource discriminator is preserved on both sides'() {
    given: 'an existing line against a PackageContentItem'
      Map created = createLine([
        resource  : [id: pciId],
        activeFrom: today.toString()
      ])

    when: 'we update a scalar, leaving the resource untouched'
      doPut("/erm/entitlements/${created.id}", [note: 'discriminator check'])
      Map event = pollForUpdateEvent(topic, created.id as String, 15_000L)

    then: 'both sides still name the concrete subclass'
      event != null
      event.old.resource?.id == pciId
      event.new.resource?.id == pciId
      // NB bracket access — map.class would return the Map's own Class
      event.old.resource['class'] == 'PackageContentItem'
      event.new.resource['class'] == 'PackageContentItem'
      event.new.note == 'discriminator check'
  }

  // Regression: pre-snapshot walking lazy collections in the outer session
  // leaves dirty state that conflicts with super.update()'s save, surfacing as
  // a StaleStateException at flush time. This is what captureSnapshotAndDiscard
  // protects against. The trigger is any PUT on a line with populated
  // collections — the mutating operation need not touch them, so a scalar-only
  // PUT is the minimal reproduction.
  void 'PUT succeeds when the line has populated collections'() {
    given: 'an existing line with coverage and tags populated'
      Map created = createLine([
        resource  : [id: pciId],
        activeFrom: today.toString(),
        coverage  : [[startDate: today.toString(), endDate: tomorrow.toString()]],
        tags      : ['kafka-update-tag']
      ])

    and: 'the collections really were persisted — otherwise this test proves nothing'
      Map persisted = doGet("/erm/entitlements/${created.id}") as Map
      (persisted.coverage as List)?.size() == 1
      (persisted.tags as List)?.size() == 1

    when: 'we PUT a scalar-only change that does not touch the collections'
      Map updated = null
      Exception caught = null
      try {
        updated = doPut("/erm/entitlements/${created.id}", [note: 'scalar only']) as Map
      } catch (Exception e) {
        caught = e
      }

    then: 'the PUT completes without a StaleStateException / optimistic-locking failure'
      caught == null
      updated?.id == created.id
      updated.note == 'scalar only'
      (updated.coverage as List)?.size() == 1

    when: 'we consume the UPDATE event'
      Map event = pollForUpdateEvent(topic, created.id as String, 15_000L)

    then: 'the collections are intact on both snapshots'
      event != null
      (event.old.coverage as List)?.size() == 1
      (event.new.coverage as List)?.size() == 1
      (event.old.tags as List)?.size() == 1
      (event.new.tags as List)?.size() == 1
      event.old.note != 'scalar only'
      event.new.note == 'scalar only'
  }

  void 'no event is published when the PUT fails validation'() {
    given: 'an existing internal line'
      Map created = createLine([
        resource  : [id: pciId],
        activeFrom: today.toString()
      ])

    when: 'we snapshot the topic timestamp then PUT an invalid change'
      long snapshotTs = System.currentTimeMillis()
      boolean caught = false
      try {
        // An external line may not keep an internal resource link.
        doPut("/erm/entitlements/${created.id}", [type: 'external'])
      } catch (Exception e) {
        caught = true
      }

    then: 'the request was rejected'
      caught

    when: 'we drain the topic for a few seconds'
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)

    then: 'no UPDATE event for our line appeared after the snapshot'
      events.findAll {
        (it.eventTs as long) >= snapshotTs && it.type == 'UPDATE' && it.new?.id == created.id
      }.isEmpty()
  }

  private Map createAgreement(String agreementName) {
    return doPost('/erm/sas', [
      name           : agreementName,
      agreementStatus: 'active',
      periods        : [[startDate: today.toString(), endDate: tomorrow.toString()]]
    ]) as Map
  }

  private Map createLine(Map line) {
    return doPost('/erm/entitlements', [owner: [id: agreementId]] + line) as Map
  }

  private Map pollForUpdateEvent(String topic, String entitlementId, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)
      Map match = events.find { it.type == 'UPDATE' && it.new?.id == entitlementId }
      if (match != null) return match
    }
    return null
  }
}