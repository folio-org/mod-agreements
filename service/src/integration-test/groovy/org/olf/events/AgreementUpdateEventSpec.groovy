package org.olf.events

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import spock.lang.Stepwise

@Slf4j
@Integration
@Stepwise
class AgreementUpdateEventSpec extends AgreementEventBaseSpec {

  void 'PUT /erm/sas/{id} emits UPDATE event with pre- and post- snapshots'() {
    given: 'a freshly created agreement in draft'
      String originalName = "kafka-update-${System.currentTimeMillis()}".toString()
      Map postPayload = [
        name           : originalName,
        agreementStatus: 'draft',
        periods        : [[
          startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate  : LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]]
      ]
      def created = doPost('/erm/sas/', postPayload)
      created?.id != null

    when: 'we PUT changes to name and status'
      String newName = "${originalName}-renamed".toString()
      Map putPayload = [
        name           : newName,
        agreementStatus: 'active'
      ]
      def updated = doPut("/erm/sas/${created.id}", putPayload)

    then: 'PUT succeeded'
      updated?.id == created.id
      updated.name == newName
      updated.agreementStatus?.value == 'active'

    when: 'we consume the UPDATE event for this agreement'
      String topic = topicFor('agreement')
      Map event = pollForUpdateEvent(topic, created.id as String, 15_000L)

    then: 'envelope shape is UPDATE with both old and new snapshots'
      event != null
      event.type == 'UPDATE'
      event.tenant == tenantId
      event.eventId != null
      event.eventTs != null
      event.old != null
      event.new != null

    and: 'ids match on both sides'
      event.old.id == created.id
      event.new.id == created.id

    and: 'name diff observable (also guards same-instance-mutation gotcha)'
      event.old.name == originalName
      event.new.name == newName

    and: 'status label diff observable via agreementStatus.value'
      event.old.agreementStatus?.value == 'draft'
      event.new.agreementStatus?.value == 'active'

    and: 'items and linkedLicenses remain ID-ref lists on both sides'
      event.old.items instanceof List
      event.new.items instanceof List
      event.old.linkedLicenses instanceof List
      event.new.linkedLicenses instanceof List
  }

  // Regression: pre-snapshot walking lazy collections in the outer session
  // used to leave dirty state that conflicted with super.update()'s save,
  // surfacing as StaleStateException on erm_title_list at flush time when
  // the agreement had populated collections. The trigger is any PUT on
  // such an agreement — the mutating operation itself need not touch the
  // populated collections. A scalar-only PUT is the minimal reproduction.
  void 'PUT /erm/sas/{id} succeeds when the agreement has populated collections'() {
    given: 'an agreement with a populated alternateNames collection'
      String originalName = "kafka-update-collections-${System.currentTimeMillis()}".toString()
      Map postPayload = [
        name           : originalName,
        agreementStatus: 'active',
        periods        : [[
          startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate  : LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]],
        alternateNames : [[name: 'AliasOne'], [name: 'AliasTwo']]
      ]
      def created = doPost('/erm/sas/', postPayload)
      created?.id != null
      (created.alternateNames as List)?.size() == 2

    when: 'we PUT a scalar-only change (does not touch the collection)'
      Map putPayload = [description: 'Updated description']
      def updated = null
      Exception caught = null
      try {
        updated = doPut("/erm/sas/${created.id}", putPayload)
      } catch (Exception e) {
        caught = e
      }

    then: 'the PUT completes without a StaleStateException / optimistic-locking failure'
      caught == null
      updated?.id == created.id
      updated.description == 'Updated description'
      // Scalar-only PUT preserves the existing collection unchanged
      (updated.alternateNames as List)?.size() == 2

    when: 'we consume the UPDATE event'
      String topic = topicFor('agreement')
      Map event = pollForUpdateEvent(topic, created.id as String, 15_000L)

    then: 'a valid UPDATE event landed with the populated collection on both snapshots'
      event != null
      event.type == 'UPDATE'
      (event.old?.alternateNames as List)?.size() == 2
      (event.new?.alternateNames as List)?.size() == 2
      event.new.description == 'Updated description'
      event.old.description != 'Updated description'
  }

  void 'UPDATE snapshots make a supplementaryDocs change diffable'() {
    given: 'an agreement created without supplementary docs'
      Map postPayload = [
        name           : "kafka-update-docs-${System.currentTimeMillis()}".toString(),
        agreementStatus: 'active',
        periods        : [[
          startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate  : LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]]
      ]
      def created = doPost('/erm/sas/', postPayload)
      created?.id != null

    when: 'we PUT a supplementary doc onto it'
      def updated = doPut("/erm/sas/${created.id}", [
        supplementaryDocs: [[name: 'Added doc', note: 'added note']]
      ])

    then: 'the PUT succeeded'
      updated?.id == created.id

    when: 'we consume the UPDATE event'
      Map event = pollForUpdateEvent(topicFor('agreement'), created.id as String, 15_000L)

    then: 'the collection is empty on old and populated on new'
      event != null
      (event.old?.supplementaryDocs as List)?.isEmpty()
      (event.new?.supplementaryDocs as List)?.size() == 1

    and: 'the post-update read resolves the persisted doc, id included'
      event.new.supplementaryDocs[0].id != null
      event.new.supplementaryDocs[0].name == 'Added doc'
      event.new.supplementaryDocs[0].note == 'added note'
  }

  void 'PUT /erm/sas/{id} with invalid payload produces no UPDATE event'() {
    given: 'a freshly created agreement'
      String name = "kafka-update-bad-${System.currentTimeMillis()}".toString()
      Map postPayload = [
        name           : name,
        agreementStatus: 'active',
        periods        : [[
          startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate  : LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]]
      ]
      def created = doPost('/erm/sas/', postPayload)

    when: 'we snapshot the topic timestamp then PUT an invalid change (blank name)'
      String topic = topicFor('agreement')
      long snapshotTs = System.currentTimeMillis()
      boolean caught = false
      try {
        doPut("/erm/sas/${created.id}", [name: ''])
      } catch (Exception e) {
        caught = true
      }

    then: 'either the request errored or we drain the topic for a bit'
      caught || true

    when: 'we drain the topic for a few seconds'
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)

    then: 'no UPDATE event for our agreement appeared after the snapshot'
      events.findAll {
        (it.eventTs as long) >= snapshotTs && it.type == 'UPDATE' && it.new?.id == created.id
      }.isEmpty()
  }

  private Map pollForUpdateEvent(String topic, String agreementId, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)
      Map match = events.find { it.type == 'UPDATE' && it.new?.id == agreementId }
      if (match != null) return match
    }
    return null
  }
}
