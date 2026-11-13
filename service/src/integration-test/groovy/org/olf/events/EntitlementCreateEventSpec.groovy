package org.olf.events

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Stepwise

import org.olf.erm.Entitlement
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance

@Slf4j
@Integration
@Stepwise
class EntitlementCreateEventSpec extends AgreementEventBaseSpec {

  @Shared
  String pciId

  @Shared
  String pkgId

  @Shared
  String ptiId

  @Shared
  String agreementId

  @Shared
  String topic

  void 'Load the package fixture used for internal agreement lines'() {
    when: 'a package is imported and one resource of each linkable class resolved'
      Map result = importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
      withTenant {
        pciId = PackageContentItem.executeQuery('SELECT pci.id FROM PackageContentItem pci')[0]
        pkgId = Pkg.executeQuery('SELECT p.id FROM Pkg p')[0]
        ptiId = PlatformTitleInstance.executeQuery('SELECT pti.id FROM PlatformTitleInstance pti')[0]
      }
      topic = topicFor('entitlement')

    and: 'an agreement to hang the lines off'
      agreementId = createAgreement("kafka-ent-create-owner-${System.currentTimeMillis()}").id

    then: 'we have something to entitle against each resource class'
      result.packageImported == true
      pciId != null
      pkgId != null
      ptiId != null
      agreementId != null
  }

  void 'POST /erm/entitlements emits a CREATE event carrying the new snapshot and no old field'() {
    given: 'a valid internal line payload'
      Map payload = [
        owner     : [id: agreementId],
        resource  : [id: pciId],
        activeFrom: today.toString(),
        activeTo  : tomorrow.toString(),
        note      : 'created for the CREATE event spec'
      ]

    when: 'we POST it'
      Map created = doPost('/erm/entitlements', payload) as Map
      Map event = pollForCreateEvent(topic, created.id as String, 15_000L)

    then: 'the line exists'
      created.id != null

    and: 'a CREATE envelope landed for it'
      event != null
      event.type == 'CREATE'
      event.tenant == tenantId
      event.eventId != null
      event.eventTs != null

    and: 'only new is present — old is omitted by NON_NULL'
      !event.containsKey('old')
      event.new != null

    and: 'the snapshot identifies the line and its parent agreement'
      event.new.id == created.id
      event.new.owner?.id == agreementId
      event.new.activeFrom == today.toString()
      event.new.activeTo == tomorrow.toString()
      event.new.note == 'created for the CREATE event spec'
      event.new.suppressFromDiscovery == false

    and: 'the resource reference carries the id and the concrete subclass'
      event.new.resource?.id == pciId
      // NB bracket access — map.class would return the Map's own Class
      event.new.resource['class'] == 'PackageContentItem'
      event.new.resource.containsKey('suppressFromDiscovery')

    and: 'timestamps use the REST GET representation'
      event.new.dateCreated ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z/
  }

  void 'the resource discriminator distinguishes each linkable subclass'() {
    given: 'one line against a Pkg and one against a PlatformTitleInstance'
      Map pkgLine = doPost('/erm/entitlements', [
        owner   : [id: agreementId],
        resource: [id: pkgId]
      ]) as Map
      Map ptiLine = doPost('/erm/entitlements', [
        owner   : [id: agreementId],
        resource: [id: ptiId]
      ]) as Map

    when: 'both events land'
      Map pkgEvent = pollForCreateEvent(topic, pkgLine.id as String, 15_000L)
      Map ptiEvent = pollForCreateEvent(topic, ptiLine.id as String, 15_000L)

    then: 'a consumer can tell the subtypes apart without calling back'
      pkgEvent != null
      pkgEvent.new.resource?.id == pkgId
      pkgEvent.new.resource['class'] == 'Pkg'

      ptiEvent != null
      ptiEvent.new.resource?.id == ptiId
      ptiEvent.new.resource['class'] == 'PlatformTitleInstance'
  }

  // Guards the null-resource branch of the snapshot builder.
  void 'a detached line reports a null resource rather than failing'() {
    given: 'a detached line — no resource, description mandatory'
      Map payload = [
        owner      : [id: agreementId],
        type       : 'detached',
        description: 'detached line for the CREATE event spec'
      ]

    when: 'we POST it'
      Map created = doPost('/erm/entitlements', payload) as Map
      Map event = pollForCreateEvent(topic, created.id as String, 15_000L)

    then: 'the request succeeded and payload construction did not blow up'
      created.id != null
      event != null

    and: 'resource is null rather than missing'
      event.new.type == 'detached'
      event.new.description == 'detached line for the CREATE event spec'
      event.new.containsKey('resource')
      event.new.resource == null
  }

  void 'the snapshot carries the hasMany collections'() {
    given: 'a line with coverage, tags and poLines populated'
      String poLineId = UUID.randomUUID().toString()
      Map created = doPost('/erm/entitlements', [
        owner     : [id: agreementId],
        resource  : [id: pciId],
        activeFrom: today.toString(),
        coverage  : [[startDate: today.toString(), endDate: tomorrow.toString()]],
        tags      : ['kafka-create-tag'],
        poLines   : [[poLineId: poLineId]]
      ]) as Map

    and: 'the collections really were persisted — otherwise this test proves nothing'
      Map persisted = doGet("/erm/entitlements/${created.id}") as Map
      (persisted.coverage as List)?.size() == 1
      (persisted.tags as List)?.size() == 1
      (persisted.poLines as List)?.size() == 1

    when: 'the event lands'
      Map event = pollForCreateEvent(topic, created.id as String, 15_000L)

    then: 'each collection travels as a nested list'
      event != null
      (event.new.coverage as List)?.size() == 1
      (event.new.tags as List)?.size() == 1
      (event.new.poLines as List)?.size() == 1
      event.new.docs instanceof List

    and: 'the nested values are the ones we posted'
      // NB values only, never child ids — at PostInsert time the parent row is
      // in but cascaded children may not be, so their ids can still be null.
      event.new.coverage[0].startDate == today.toString()
      event.new.coverage[0].endDate == tomorrow.toString()
      event.new.tags[0].value == 'kafka-create-tag'

    and: 'the mod-orders reference travels as a bare UUID with no lookup'
      event.new.poLines[0].poLineId == poLineId
  }

  void 'no event is published when the POST fails validation'() {
    given: 'we snapshot the topic timestamp'
      long snapshotTs = System.currentTimeMillis()

    when: 'we POST an internal line with no resource'
      boolean caught = false
      try {
        doPost('/erm/entitlements', [owner: [id: agreementId], type: 'internal'])
      } catch (Exception e) {
        caught = true
      }

    then: 'the request was rejected'
      caught

    when: 'we drain the topic for a few seconds'
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)

    then: 'no CREATE event newer than our snapshot slipped onto the topic'
      // Scoped to this spec's tenant: with KAFKA_TENANT_COLLECTION=ALL every
      // spec shares folio.ALL.agreements.entitlement, and integrationTest runs
      // them in parallel forks — an unscoped filter catches siblings' events.
      events.findAll {
        it.tenant == tenantId && (it.eventTs as long) >= snapshotTs && it.type == 'CREATE'
      }.isEmpty()
  }

  private Map createAgreement(String agreementName) {
    return doPost('/erm/sas', [
      name           : agreementName,
      agreementStatus: 'active',
      periods        : [[startDate: today.toString(), endDate: tomorrow.toString()]]
    ]) as Map
  }

  private Map pollForCreateEvent(String topic, String entitlementId, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      List<Map> events = pollForEvents(topic, Integer.MAX_VALUE, 6_000L)
      Map match = events.find { it.type == 'CREATE' && it.new?.id == entitlementId }
      if (match != null) return match
    }
    return null
  }
}