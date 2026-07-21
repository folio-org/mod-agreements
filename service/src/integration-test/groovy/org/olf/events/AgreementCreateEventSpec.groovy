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
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 3_000L)

    then: 'no event newer than our snapshot slipped onto the topic'
      events.findAll { (it.eventTs as long) >= snapshotTs }.isEmpty()
  }

  private Map pollForEventByAgreementId(String topic, String agreementId, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 2_000L)
      Map match = events.find { it.new?.id == agreementId }
      if (match != null) return match
    }
    return null
  }
}
