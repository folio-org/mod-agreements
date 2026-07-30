package org.olf.events

import java.time.LocalDate

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Stepwise

import org.olf.erm.Entitlement
import org.olf.general.events.EntitlementEventService
import org.olf.kb.PackageContentItem

@Slf4j
@Integration
@Stepwise
class EntitlementDeleteEventSpec extends AgreementEventBaseSpec {

  @Inject
  EntitlementEventService entitlementEventService

  @Shared
  String pciId

  @Shared
  String topic

  void 'Load the package fixture used for internal agreement lines'() {
    when: 'a package is imported and a PackageContentItem resolved'
      Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
      pciId = withTenant {
        PackageContentItem.executeQuery('SELECT pci.id FROM PackageContentItem pci')[0]
      }
      topic = topicFor('entitlement')

    then: 'we have something to entitle'
      result.packageImported == true
      pciId != null
  }

  void 'PUT /erm/sas/{id} removing a line emits a DELETE event carrying the old projection and no new field'() {
    given: 'an agreement with one internal line'
      String agreementName = "kafka-ent-delete-${System.currentTimeMillis()}".toString()
      LocalDate activeFrom = today
      Map agreement = createAgreementWithLine(agreementName, [
        resource  : [id: pciId],
        activeFrom: activeFrom.toString(),
        activeTo  : tomorrow.toString()
      ])
      String entitlementId = firstEntitlementIdFor(agreement.id as String)

    when: 'the line is removed via the parent agreement'
      doPut("/erm/sas/${agreement.id}", [
        id   : agreement.id,
        items: [[id: entitlementId, _delete: true]]
      ])
      Map event = pollForDeleteEvent(topic, entitlementId, 15_000L)

    then: 'the row really was orphan-deleted'
      withTenant { Entitlement.get(entitlementId) } == null

    and: 'a DELETE envelope landed for this entitlement'
      event != null
      event.type == 'DELETE'
      event.tenant == tenantId
      event.eventId != null
      event.eventTs != null

    and: 'only old is present — new is omitted by NON_NULL'
      event.old != null
      !event.containsKey('new')

    and: 'the projection identifies the removed line and its parent agreement'
      event.old.id == entitlementId
      event.old.owner?.id == agreement.id
      event.old.activeFrom == activeFrom.toString()
      event.old.activeTo == tomorrow.toString()

    and: 'the resource reference carries the id and the concrete subclass'
      event.old.resource?.id == pciId
      // NB bracket access — map.class would return the Map's own Class
      event.old.resource['class'] == 'PackageContentItem'
  }

  void 'the projection excludes hasMany collections'() {
    given: 'an agreement line with coverage, tags, poLines and docs populated'
      String agreementName = "kafka-ent-delete-collections-${System.currentTimeMillis()}".toString()
      Map agreement = createAgreementWithLine(agreementName, [
        resource  : [id: pciId],
        activeFrom: today.toString(),
        coverage  : [[startDate: today.toString(), endDate: tomorrow.toString()]],
        tags      : ['kafka-delete-tag'],
        poLines   : [[poLineId: UUID.randomUUID().toString()]],
        docs      : [[name: 'delete-event-doc', note: 'should never reach the payload']]
      ])
      String entitlementId = firstEntitlementIdFor(agreement.id as String)

    and: 'the collections really were persisted — otherwise this test proves nothing'
      Map persisted = doGet("/erm/entitlements/${entitlementId}") as Map
      (persisted.coverage as List)?.size() == 1
      (persisted.tags as List)?.size() == 1
      (persisted.poLines as List)?.size() == 1
      (persisted.docs as List)?.size() == 1

    when: 'the line is deleted'
      doDelete("/erm/entitlements/${entitlementId}")
      Map event = pollForDeleteEvent(topic, entitlementId, 15_000L)

    then: 'the sub-collections are absent from the payload entirely'
      event != null
      !event.old.containsKey('coverage')
      !event.old.containsKey('poLines')
      !event.old.containsKey('tags')
      !event.old.containsKey('docs')

    and: 'the defined projection fields are all still there'
      event.old.keySet() as Set == [
        'id', 'owner', 'type', 'resource', 'resourceName', 'activeFrom', 'activeTo',
        'enabled', 'suppressFromDiscovery', 'authority', 'reference', 'dateCreated', 'lastUpdated'
      ] as Set
  }


  void 'no event is published when the transaction rolls back'() {
    given: 'an existing agreement line'
      String agreementName = "kafka-ent-delete-rollback-${System.currentTimeMillis()}".toString()
      Map agreement = createAgreementWithLine(agreementName, [
        resource  : [id: pciId],
        activeFrom: today.toString()
      ])
      String entitlementId = firstEntitlementIdFor(agreement.id as String)

    when: 'a delete event is enqueued in a transaction that then rolls back'
      long snapshotTs = System.currentTimeMillis()
      withTenant {
        Entitlement.withNewTransaction { status ->
          Map<String, Object> projection = entitlementEventService.captureProjection(
            Entitlement.get(entitlementId))
          assert projection != null
          entitlementEventService.publishDelete(projection)
          status.setRollbackOnly()
        }
      }
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 3_000L)

    then: 'afterCommit never fired, so nothing reached the topic'
      events.findAll {
        (it.eventTs as long) >= snapshotTs && it.type == 'DELETE' && it.old?.id == entitlementId
      }.isEmpty()

    and: 'the row is untouched'
      withTenant { Entitlement.get(entitlementId) } != null
  }

  // Guards the null-resource branch of the projection.
  void 'an external line deletes cleanly and reports a null resource'() {
    given: 'an agreement with one external line'
      String agreementName = "kafka-ent-delete-external-${System.currentTimeMillis()}".toString()
      String reference = '26929514-237c-11ed-861d-0242ac120002:26929514-237c-11ed-861d-0242ac120001'
      Map agreement = createAgreementWithLine(agreementName, [
        type        : 'external',
        authority   : Entitlement.GOKB_RESOURCE_AUTHORITY,
        reference   : reference,
        resourceName: 'test resource',
        description : 'external line for delete event'
      ])
      String entitlementId = firstEntitlementIdFor(agreement.id as String)

    when: 'the line is deleted'
      doDelete("/erm/entitlements/${entitlementId}")
      Map event = pollForDeleteEvent(topic, entitlementId, 15_000L)

    then: 'the external identifiers travel and resource is null rather than missing'
      event != null
      event.old.id == entitlementId
      event.old.type == 'external'
      event.old.authority == Entitlement.GOKB_RESOURCE_AUTHORITY
      event.old.reference == reference
      event.old.resourceName == 'test resource'
      event.old.containsKey('resource')
      event.old.resource == null
  }

  private Map createAgreementWithLine(String agreementName, Map item) {
    Map payload = [
      name           : agreementName,
      agreementStatus: 'active',
      periods        : [[startDate: today.toString(), endDate: tomorrow.toString()]],
      items          : [item]
    ]
    return doPost('/erm/sas', payload) as Map
  }

  private String firstEntitlementIdFor(String agreementId) {
    withTenant {
      Entitlement.executeQuery(
        'SELECT ent.id FROM Entitlement ent WHERE ent.owner.id = :agreementId',
        [agreementId: agreementId]
      )[0]
    }
  }

  private Map pollForDeleteEvent(String topic, String entitlementId, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 2_000L)
      Map match = events.find { it.type == 'DELETE' && it.old?.id == entitlementId }
      if (match != null) return match
    }
    return null
  }
}