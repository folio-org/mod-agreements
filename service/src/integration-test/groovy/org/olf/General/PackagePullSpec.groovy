package org.olf.General

import com.sun.net.httpserver.HttpServer
import grails.testing.mixin.integration.Integration
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.olf.BaseSpec
import org.olf.PackageSyncService
import org.olf.PackagePullService
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.IdentifierOccurrence
import org.olf.general.jobs.JobRunnerService
import org.olf.general.jobs.PackageTriggerResyncJob
import org.olf.general.jobs.PackagePullJob
import org.olf.kb.PackageContentItem
import org.olf.kb.PackagePullException
import org.olf.kb.Pkg
import org.olf.kb.RemoteKB
import org.olf.kb.adapters.GOKbOAIAdapter
import org.olf.kb.metadata.PackageIngressMetadata
import org.olf.kb.metadata.ResourceIngressType
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Integration
@Stepwise
class PackagePullSpec extends BaseSpec {
  PackageSyncService packageSyncService
  PackagePullService packagePullService
  JobRunnerService jobRunnerService

  @Shared HttpServer oaiServer
  @Shared AtomicReference<String> xml = new AtomicReference<>()
  @Shared AtomicReference<Closure> onRequest = new AtomicReference<>()
  @Shared AtomicInteger httpStatus = new AtomicInteger(200)
  @Shared List<String> requests = new CopyOnWriteArrayList<>()
  @Shared String fixture
  @Shared String packageId
  @Shared String sourceId
  @Shared Long lastCheck = System.currentTimeMillis() + 86400000L
  @Shared String cursor = '2026-01-03T00:00:00Z'
  static final String GOKB_UUID = '460f55bf-d1b1-45bb-a7be-54aa313535fe'

  def setupSpec() {
    fixture = new File('src/integration-test/resources/pullPackage/record.xml').text
    xml.set(fixture)
    oaiServer = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
    oaiServer.createContext('/oai/packages') { exchange ->
      requests.add(exchange.requestURI.rawQuery)
      onRequest.getAndSet(null)?.call()
      byte[] body = xml.get().getBytes('UTF-8')
      exchange.responseHeaders.set('Content-Type', 'text/xml; charset=UTF-8')
      exchange.sendResponseHeaders(httpStatus.get(), body.length)
      exchange.responseBody.withCloseable { it.write(body) }
      exchange.close()
    }
    oaiServer.start()
  }

  def cleanupSpec() {
    oaiServer?.stop(0)
  }

  void 'Create a local harvested package with a GOKB source'() {
    when:
      withTenantNewTransaction {
        // No test should contact an external KB, including scheduled harvests.
        RemoteKB.executeUpdate('update RemoteKB set active = false')
        RemoteKB source = new RemoteKB(
          name: 'Pull test source', type: GOKbOAIAdapter.name,
          uri: "http://127.0.0.1:${oaiServer.address.port}/oai/",
          active: true, rectype: RemoteKB.RECTYPE_PACKAGE, cursor: cursor,
          lastCheck: lastCheck, syncStatus: 'idle'
        ).save(failOnError: true, flush: true)
        sourceId = source.id
        Pkg pkg = new Pkg(name: 'Before pull'.padRight(255, 'x'), source: 'GOKb', reference: GOKB_UUID,
          syncContentsFromSource: true).save(failOnError: true, flush: true)
        packageId = pkg.id
        IdentifierNamespace ns = IdentifierNamespace.findByValue('gokb_uuid') ?:
          new IdentifierNamespace(value: 'gokb_uuid').save(failOnError: true)
        Identifier identifier = new Identifier(ns: ns, value: GOKB_UUID).save(failOnError: true)
        new IdentifierOccurrence(resource: pkg, identifier: identifier,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')).save(failOnError: true, flush: true)
        new PackageIngressMetadata(resource: pkg, ingressType: ResourceIngressType.HARVEST,
          ingressId: sourceId).save(failOnError: true, flush: true)
      }
    then:
      packageId != null
  }

  void 'Enabling a paused harvested package automatically imports its contents'() {
    given:
      setPaused(true)
      int before = requests.size()
      List previousPulls = pullJobIds()
    when:
      Map response = requestJson('POST', '/erm/packages/controlSync',
        [packageIds: [packageId], syncState: 'SYNCHRONIZING'], currentTenant)
    then:
      response.status == 200
      response.body == [packagesUpdated: 1, packagesSkipped: 0, success: true]
      new PollingConditions(timeout: 60, delay: 0.5).eventually {
        // Run both the existing resync job and the pull it queues.
        jobRunnerService.droneTick(jobRunnerService.appFederationService.instanceId)
        List created = pullJobIds() - previousPulls
        assert created.size() == 1
        assert jobState(created[0]) == [status: 'ended', result: 'success']
      }
      requests.size() == before + 1
      requests.last().split('&').toList().toSet() == [
        'verb=GetRecord', 'metadataPrefix=gokb', "identifier=${GOKB_UUID}".toString()
      ].toSet()
      assertImported(true)
      assertSourceUnchanged()

    when: 'The UI sends the same enabled status again'
      int resyncs = resyncJobCount()
      List pulls = pullJobIds()
      Map unchanged = requestJson('POST', '/erm/packages/controlSync',
        [packageIds: [packageId], syncState: 'SYNCHRONIZING'], currentTenant)
    then:
      unchanged.status == 200
      unchanged.body == [packagesUpdated: 0, packagesSkipped: 1, success: true]
      resyncJobCount() == resyncs
      pullJobIds() == pulls

    when: 'The package is paused through the same endpoint'
      Map paused = requestJson('POST', '/erm/packages/controlSync',
        [packageIds: [packageId], syncState: 'PAUSED'], currentTenant)
    then:
      paused.status == 200
      paused.body == [packagesUpdated: 1, packagesSkipped: 0, success: true]
      resyncJobCount() == resyncs
      pullJobIds() == pulls
      requests.size() == before + 1
    cleanup:
      setPaused(false)
  }

  void 'Automatic resync skips a package paused again before it runs'() {
    given:
      setPaused(true)
      List before = pullJobIds()
      int requestCount = requests.size()
    when:
      withTenant { packageSyncService.resyncPackage(packageId) }
    then:
      pullJobIds() == before
      requests.size() == requestCount
      assertSourceUnchanged()
    cleanup:
      setPaused(false)
  }

  void 'Automatic resync reuses a pending manual pull'() {
    given:
      String jobId = heldJob()
      List before = pullJobIds()
      int requestCount = requests.size()
    when:
      withTenant { packageSyncService.resyncPackage(packageId) }
    then:
      pullJobIds() == before
      jobState(jobId).status == 'in_progress'
      requests.size() == requestCount
    cleanup:
      endHeldJob(jobId)
  }

  @Unroll
  void 'Pull imports contents without changing the cursor, with sync flag #syncFlag'() {
    given:
      withTenantNewTransaction {
        Pkg pkg = Pkg.get(packageId)
        pkg.syncContentsFromSource = syncFlag
        pkg.save(failOnError: true, flush: true)
      }
      int before = requests.size()
    when:
      Map response = postPull([packageId: packageId])
      // Exercise the real runner without waiting for its scheduled federation ticks.
      jobRunnerService.droneTick(jobRunnerService.appFederationService.instanceId)
      jobRunnerService.droneTick(jobRunnerService.appFederationService.instanceId)
    then:
      response.status == 202
      response.body.jobId
      response.location == "/erm/jobs/${response.body.jobId}"
      new PollingConditions(timeout: 60).eventually {
        Map state = jobState(response.body.jobId)
        assert state.status == 'ended'
        assert state.result == 'success'
      }
      Map details = doGet("/erm/jobs/${response.body.jobId}")
      details.status.value == 'ended'
      details.result.value == 'success'
      requests.size() == before + 1
      requests.last().split('&').toList().toSet() == [
        'verb=GetRecord', 'metadataPrefix=gokb', "identifier=${GOKB_UUID}".toString()
      ].toSet()
      assertImported(syncFlag)
      assertSourceUnchanged()
    where:
      syncFlag << [true, null]
  }

  @Unroll
  void 'Invalid request #body returns #status'() {
    when:
      Map response = postPull(body)
    then:
      response.status == status
      response.body.error
    where:
      body                                             | status
      [:]                                              | 400
      [packageId: 123]                                  | 400
      [packageId: ' ']                                  | 400
      [packageId: '00000000-0000-0000-0000-000000000000'] | 404
  }

  void 'Paused packages are rejected without an OAI request'() {
    given:
      setPaused(true)
      int before = requests.size()
    when:
      Map response = postPull([packageId: packageId])
    then:
      response.status == 409
      response.body.error.contains('paused')
      requests.size() == before
    cleanup:
      setPaused(false)
  }

  void 'PushKB mode is rejected'() {
    given:
      def original = kbManagementBean.ingressType
      kbManagementBean.ingressType = ResourceIngressType.PUSHKB
    when:
      Map response = postPull([packageId: packageId])
    then:
      response.status == 409
      response.body.error.contains('Harvest ingress mode')
    cleanup:
      kbManagementBean.ingressType = original
  }

  @Unroll
  void 'Unsupported source or package metadata is rejected: #change'() {
    given:
      withTenantNewTransaction {
        if (change == 'adapter') {
          RemoteKB.get(sourceId).type = 'org.olf.kb.adapters.GenericRemoteKBAdapter'
        } else {
          PackageIngressMetadata.findByResource(Pkg.get(packageId)).ingressType = ResourceIngressType.PUSHKB
        }
      }
    when:
      Map response = postPull([packageId: packageId])
    then:
      response.status == 409
    cleanup:
      withTenantNewTransaction {
        RemoteKB.get(sourceId).type = GOKbOAIAdapter.name
        PackageIngressMetadata.findByResource(Pkg.get(packageId)).ingressType = ResourceIngressType.HARVEST
      }
    where:
      change << ['adapter', 'ingress']
  }

  void 'A missing approved GOKB identifier is rejected'() {
    given:
      withTenantNewTransaction {
        IdentifierOccurrence.findByResourceAndIdentifier(Pkg.get(packageId),
          Identifier.findByNsAndValue(IdentifierNamespace.findByValue('gokb_uuid'), GOKB_UUID))
          .status = IdentifierOccurrence.lookupOrCreateStatus('error')
      }
    when:
      Map response = postPull([packageId: packageId])
    then:
      response.status == 409
      response.body.error.contains('gokb_uuid')
    cleanup:
      withTenantNewTransaction {
        IdentifierOccurrence.findByResourceAndIdentifier(Pkg.get(packageId),
          Identifier.findByNsAndValue(IdentifierNamespace.findByValue('gokb_uuid'), GOKB_UUID))
          .status = IdentifierOccurrence.lookupOrCreateStatus('approved')
      }
  }

  void 'A package ID from another tenant cannot be pulled'() {
    given:
      String otherTenant = 'pulltestother' + UUID.randomUUID().toString().substring(0, 8)
      Map created = requestJson('POST', '/_/tenant', [parameters: [[key: 'loadReference', value: true]]], otherTenant)
      assert created.status < 300
    when:
      Map response = requestJson('POST', '/erm/admin/pullPackage', [packageId: packageId], otherTenant)
    then:
      response.status == 404
    cleanup:
      requestJson('DELETE', '/_/tenant', null, otherTenant)
  }

  void 'GET cannot trigger a pull'() {
    when:
      Map response = requestJson('GET', '/erm/admin/pullPackage', null, currentTenant)
    then:
      response.status == 405
  }

  void 'Pausing during download prevents ingestion'() {
    given:
      String jobId = heldJob()
      xml.set(fixture.replace('OAI pull test package', 'Must not be imported'))
      onRequest.set({ setPaused(true) })
    when:
      withTenant { packagePullService.pull(jobId) }
    then:
      PackagePullException e = thrown()
      e.message.contains('paused')
      assertImported(false)
      assertSourceUnchanged()
    cleanup:
      onRequest.set(null)
      xml.set(fixture)
      setPaused(false)
      endHeldJob(jobId)
  }

  void 'Duplicate pulls are rejected'() {
    given:
      String jobId = heldJob()
    when:
      Map response = postPull([packageId: packageId])
    then:
      response.status == 409
      response.body.error.contains('already queued or running')
    cleanup:
      endHeldJob(jobId)
  }

  void 'Pausing after queuing prevents retrieval'() {
    given:
      String jobId = heldJob()
      setPaused(true)
      int before = requests.size()
    when:
      withTenant { packagePullService.pull(jobId) }
    then:
      PackagePullException e = thrown()
      e.message.contains('paused')
      requests.size() == before
      assertSourceUnchanged()
    cleanup:
      setPaused(false)
      endHeldJob(jobId)
  }

  @Unroll
  void 'A busy source defers the pull and retries with pause state #pauseBeforeRetry'() {
    given:
      withTenantNewTransaction { RemoteKB.get(sourceId).syncStatus = 'in-process' }
      int before = requests.size()
      List previousPulls = pullJobIds()
    when:
      Map response = postPull([packageId: packageId])
      jobRunnerService.droneTick(jobRunnerService.appFederationService.instanceId)
      jobRunnerService.droneTick(jobRunnerService.appFederationService.instanceId)
    then:
      response.status == 202
      new PollingConditions(timeout: 30).eventually {
        assertDeferred(response.body.jobId)
      }
      requests.size() == before
      sourceStatus() == 'in-process'
      pullJobIds() - previousPulls == [response.body.jobId]
      postPull([packageId: packageId]).status == 409

    when: 'The regular harvest finishes before the next runner tick'
      setPaused(pauseBeforeRetry)
      withTenantNewTransaction { RemoteKB.get(sourceId).syncStatus = 'idle' }
      jobRunnerService.droneTick(jobRunnerService.appFederationService.instanceId)
    then:
      new PollingConditions(timeout: 30).eventually {
        assert jobState(response.body.jobId) == [status: 'ended',
          result: pauseBeforeRetry ? 'failure' : 'success']
      }
      requests.size() == before + (pauseBeforeRetry ? 0 : 1)
      pullJobIds() - previousPulls == [response.body.jobId]
      assertImported(!pauseBeforeRetry)
      assertSourceUnchanged()
    cleanup:
      setPaused(false)
      withTenantNewTransaction { RemoteKB.get(sourceId).syncStatus = 'idle' }
    where:
      pauseBeforeRetry << [false, true]
  }

  void 'Upstream failures are reported by the background job'() {
    given:
      httpStatus.set(503)
    when:
      Map response = postPull([packageId: packageId])
      jobRunnerService.droneTick(jobRunnerService.appFederationService.instanceId)
    then:
      response.status == 202
      new PollingConditions(timeout: 30).eventually {
        Map state = jobState(response.body.jobId)
        assert state.status == 'ended'
        assert state.result == 'failure'
      }
      assertSourceUnchanged()
      assertImported(true)
    cleanup:
      httpStatus.set(200)
  }

  void 'Interrupted jobs release only their own claimed source'() {
    given:
      String jobId = heldJob()
      withTenantNewTransaction {
        RemoteKB.get(sourceId).syncStatus = 'in-process'
      }
    when: 'The job has not claimed the source'
      withTenant { packagePullService.releaseSource(jobId) }
    then:
      sourceStatus() == 'in-process'
    when: 'The interrupted job owns the source claim'
      withTenantNewTransaction { PackagePullJob.get(jobId).sourceClaimed = true }
      withTenant { packagePullService.releaseSource(jobId) }
    then:
      assertSourceUnchanged()
    cleanup:
      withTenantNewTransaction { RemoteKB.get(sourceId).syncStatus = 'idle' }
      endHeldJob(jobId)
  }

  @Unroll
  void 'Bulk ListRecords retains the shared eligibility rule: #listStatus / #editStatus'() {
    given:
      String page = fixture.replace('GetRecord', 'ListRecords')
        .replace('<listStatus>Checked</listStatus>', "<listStatus>${listStatus}</listStatus>")
        .replace('<editStatus>Approved</editStatus>', "<editStatus>${editStatus}</editStatus>")
      def transformed
    when:
      withTenant {
        transformed = new GOKbOAIAdapter().transformPackagePage(
          new groovy.xml.XmlSlurper().parseText(page), packagePullService.knowledgeBaseCacheService, false
        )
      }
    then:
      (transformed.v1[0].v1 != null) == accepted
      transformed.v1[0].v2 == '2026-01-02T00:00:00Z'
    where:
      listStatus    | editStatus | accepted
      'Checked'     | 'Approved' | true
      'In Progress' | 'Approved' | false
      'Checked'     | 'Rejected' | false
  }

  @Unroll
  void 'Upstream #scenario causes job failure and releases the source'() {
    given:
      String jobId = heldJob()
      xml.set(responseXml)
      httpStatus.set(status)
    when:
      withTenant { packagePullService.pull(jobId) }
    then:
      IllegalStateException e = thrown()
      e.message.contains(message)
      assertSourceUnchanged()
      assertImported(true)
    cleanup:
      xml.set(fixture)
      httpStatus.set(200)
      endHeldJob(jobId)
    where:
      scenario        | responseXml                                                                                                  | status | message
      'HTTP error'    | '<error/>'                                                                                                   | 503    | 'HTTP 503'
      'OAI error'     | '<OAI-PMH><error code="idDoesNotExist">Missing</error></OAI-PMH>'                                               | 200    | 'idDoesNotExist'
      'invalid XML root' | '<html/>' | 200 | 'Invalid OAI GetRecord response'
      'empty record'  | '<OAI-PMH><GetRecord/></OAI-PMH>'                                                                              | 200    | 'exactly one record'
      'wrong package' | fixture.replace(GOKB_UUID, '00000000-0000-0000-0000-000000000000')                                             | 200    | 'different package UUID'
      'unchecked'     | fixture.replace('<listStatus>Checked</listStatus>', '<listStatus>In Progress</listStatus>')                    | 200    | 'listStatus'
      'rejected'      | fixture.replace('<editStatus>Approved</editStatus>', '<editStatus>Rejected</editStatus>')                      | 200    | 'editStatus'
      'deleted'       | fixture.replace('<header>', '<header status="deleted">')                                                     | 200    | 'deleted'
  }

  private Map postPull(Object body) {
    requestJson('POST', '/erm/admin/pullPackage', body, currentTenant)
  }

  private Map requestJson(String method, String path, Object body, String tenant) {
    HttpURLConnection connection = new URL(new URL(baseUrl.toString()), path).openConnection()
    connection.requestMethod = method
    connection.setRequestProperty('Content-Type', 'application/json')
    allHeaders.each { key, value -> connection.setRequestProperty(key, value.toString()) }
    connection.setRequestProperty('X-Okapi-Tenant', tenant)
    if (body != null) {
      connection.doOutput = true
      connection.outputStream.withCloseable { it.write(JsonOutput.toJson(body).getBytes('UTF-8')) }
    }
    int status = connection.responseCode
    def stream = status < 400 ? connection.inputStream : connection.errorStream
    String text = stream?.getText('UTF-8')
    Map result = [status: status, location: connection.getHeaderField('Location'),
                  body: text && connection.contentType?.contains('json') ? new JsonSlurper().parseText(text) : text]
    connection.disconnect()
    return result
  }

  private List pullJobIds() {
    withTenantNewTransaction {
      PackagePullJob.executeQuery('select j.id from PackagePullJob j where j.packageId = :id order by j.id',
        [id: packageId])
    }
  }

  private int resyncJobCount() {
    withTenantNewTransaction { PackageTriggerResyncJob.countByPackageId(packageId) }
  }

  private void assertDeferred(String id) {
    withTenantNewTransaction {
      PackagePullJob job = PackagePullJob.get(id)
      assert job.status.value == 'queued'
      assert job.runnerId == null
      assert job.started == null
      assert job.ended == null
      assert job.result == null
      assert !job.sourceClaimed
      assert job.errorLogCount == 0
      assert job.infoLog.any { it.message.contains('Job deferred:') }
    }
  }

  private Map jobState(String id) {
    withTenantNewTransaction {
      PackagePullJob job = PackagePullJob.get(id)
      return [status: job.status.value, result: job.result?.value]
    }
  }

  private void assertImported(Boolean syncFlag) {
    withTenantNewTransaction {
      Pkg pkg = Pkg.get(packageId)
      assert pkg.name == 'OAI pull test package'
      assert pkg.syncContentsFromSource == syncFlag
      assert PackageContentItem.countByPkg(pkg) == 1
      assert PackageContentItem.findByPkg(pkg).pti.titleInstance.name == 'OAI pull test title'
      assert Pkg.countBySourceAndReference('GOKb', GOKB_UUID) == 1
    }
  }

  private void assertSourceUnchanged() {
    withTenantNewTransaction {
      RemoteKB source = RemoteKB.get(sourceId)
      assert source.cursor == cursor
      assert source.lastCheck == lastCheck
      assert source.syncStatus == 'idle'
    }
  }

  private String sourceStatus() {
    withTenantNewTransaction { RemoteKB.get(sourceId).syncStatus }
  }

  private void setPaused(boolean paused) {
    withTenantNewTransaction {
      Pkg.get(packageId).syncContentsFromSource = !paused
    }
  }

  private String heldJob() {
    withTenantNewTransaction {
      PackagePullJob job = new PackagePullJob(name: 'Controlled pull test', packageId: packageId,
        remoteKbId: sourceId, runnerId: jobRunnerService.appFederationService.instanceId)
      job.setStatusFromString('In progress')
      job.save(failOnError: true, flush: true)
      return job.id
    }
  }

  private void endHeldJob(String id) {
    if (id) withTenantNewTransaction {
      PackagePullJob job = PackagePullJob.get(id)
      job.setStatusFromString('Ended')
      job.save(failOnError: true, flush: true)
    }
  }
}
