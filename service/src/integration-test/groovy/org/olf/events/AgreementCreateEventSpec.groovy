package org.olf.events

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import spock.lang.Stepwise

@Slf4j
@Integration
@Stepwise
class AgreementCreateEventSpec extends AgreementEventBaseSpec {

  void 'POST /erm/sas emits CREATE event carrying full new snapshot and no old field'() {
    given: 'a valid agreement payload'
      String agreementName = "kafka-create-${System.currentTimeMillis()}".toString()
      Map payload = [
        name           : agreementName,
        agreementStatus: 'active',
        periods        : [[
          startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate  : LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]]
      ]

    when: 'we POST to /erm/sas'
      def response = doPost('/erm/sas/', payload)

    then: 'the agreement was created'
      response != null
      response.id != null

    when: 'we consume from the tenant-scoped topic'
      String topic = topicFor('agreement')
      Map event = pollForEventByAgreementId(topic, response.id as String, 15_000L)

    then: 'a CREATE envelope for this agreement landed with only the new snapshot'
      event != null
      event.type == 'CREATE'
      event.tenant == tenantId
      event.eventId != null
      event.eventTs != null
      !event.containsKey('old')
      event.new != null
      event.new.id == response.id
      event.new.name == agreementName
      event.new.agreementStatus?.value == 'active'
      event.new.items instanceof List
      event.new.linkedLicenses instanceof List
      event.new.periods instanceof List
      event.new.periods.size() == 1
      event.new.dateCreated ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z/
      event.new.lastUpdated ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z/
      event.new.periods[0].startDate ==~ /\d{4}-\d{2}-\d{2}/
  }

  void 'CREATE snapshot carries doc collections, relationships and attachedLicenceId'() {
    given: 'a relationship type and an existing agreement to relate to'
      List relationshipTypes = doGet('/erm/refdata/AgreementRelationship/type')
      String relationshipType = relationshipTypes[0].value
      def related = doPost('/erm/sas/', [
        name           : "kafka-create-related-${System.currentTimeMillis()}".toString(),
        agreementStatus: 'active',
        periods        : [[startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)]]
      ])

    expect: 'the far-side agreement exists'
      relationshipType != null
      related?.id != null

    when: 'we POST an agreement carrying docs, a relationship and a licence reference'
      String licenceId = UUID.randomUUID().toString()
      def response = doPost('/erm/sas/', [
        name                : "kafka-create-docs-${System.currentTimeMillis()}".toString(),
        agreementStatus     : 'active',
        attachedLicenceId   : licenceId,
        periods             : [[startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)]],
        supplementaryDocs   : [[name: 'Supp doc', note: 'supp note']],
        externalLicenseDocs : [[name: 'Ext licence doc', url: 'http://example.org/licence']],
        outwardRelationships: [[type: relationshipType, inward: related.id]]
      ])

    then: 'the agreement was created'
      response?.id != null

    when: 'we consume the CREATE event for it'
      Map event = pollForEventByAgreementId(topicFor('agreement'), response.id as String, 15_000L)

    then: 'all three doc collections are present'
      event != null
      event.new.docs instanceof List
      event.new.supplementaryDocs instanceof List
      event.new.externalLicenseDocs instanceof List

    and: 'doc metadata is carried but file content is not'
      event.new.supplementaryDocs.find { it.name == 'Supp doc' }?.note == 'supp note'
      event.new.externalLicenseDocs.find { it.name == 'Ext licence doc' }?.url == 'http://example.org/licence'
      event.new.supplementaryDocs.every { !it.containsKey('fileUpload') }

    and: 'both relationship collections are present, far side as an ID-ref only'
      event.new.inwardRelationships instanceof List
      event.new.outwardRelationships instanceof List
      event.new.outwardRelationships.size() == 1
      event.new.outwardRelationships[0].type?.value == relationshipType
      event.new.outwardRelationships[0].inward?.id == related.id
      (event.new.outwardRelationships[0].inward as Map).keySet() == ['id'] as Set

    and: 'attachedLicenceId reaches the snapshot'
      event.new.attachedLicenceId == licenceId
  }

  void 'POST /erm/sas with missing name produces no event for that request'() {
    given: 'we snapshot the current highest eventTs on the topic'
      String topic = topicFor('agreement')
      long snapshotTs = System.currentTimeMillis()

    when: 'we POST an invalid payload (no name)'
      Map badPayload = [
        agreementStatus: 'active',
        periods        : [[
          startDate: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]]
      ]
      boolean caught = false
      try {
        doPost('/erm/sas/', badPayload)
      } catch (Exception e) {
        caught = true
      }

    then: 'the request failed'
      caught

    when: 'we drain the topic for a few seconds'
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)

    then: 'no event newer than our snapshot slipped onto the topic'
      // Scoped to this spec's tenant: with KAFKA_TENANT_COLLECTION=ALL every
      // spec shares folio.ALL.agreements.agreement, and integrationTest runs
      // them in parallel forks — an unscoped filter catches siblings' events.
      events.findAll { it.tenant == tenantId && (it.eventTs as long) >= snapshotTs }.isEmpty()
  }

  private Map pollForEventByAgreementId(String topic, String agreementId, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)
      Map match = events.find { it.new?.id == agreementId }
      if (match != null) return match
    }
    return null
  }
}
