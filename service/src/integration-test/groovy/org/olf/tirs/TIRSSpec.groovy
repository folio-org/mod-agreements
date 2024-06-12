package org.olf.tirs

// Services
import org.olf.general.jobs.JobRunnerService
import org.olf.KbHarvestService

// Domain classes
import org.olf.BaseSpec
import org.olf.kb.RemoteKB

import org.olf.dataimport.internal.PackageContentImpl
import grails.web.databinding.DataBindingUtils


import com.k_int.okapi.OkapiTenantResolver
import grails.gorm.multitenancy.Tenants

// Testing stuff
import spock.lang.*

import spock.util.concurrent.PollingConditions

// Logging
import groovy.util.logging.Slf4j

@Slf4j
@Stepwise
abstract class TIRSSpec extends BaseSpec {
  // titleInstanceResolverService is injected in baseSpec now
  KbHarvestService kbHarvestService
  JobRunnerService jobRunnerService

  // Place to house any shared TIRS testing methods etc
  @Ignore
  Boolean isWorkSourceTIRS() {
    injectedTIRS() == WORK_SOURCE_TIRS
  }

  @Ignore
  Boolean isIdTIRS() {
    injectedTIRS() == ID_TIRS
  }

  @Ignore
  Boolean isTitleTIRS() {
    injectedTIRS() == TITLE_TIRS
  }

  @Ignore
  protected RemoteKB setUpDebugKb(String xmlPackagePath) {
    Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
      RemoteKB.findByName('DEBUG') ?: (new RemoteKB(
        name:'DEBUG',
        type:'org.olf.kb.adapters.DebugGoKbAdapter',
        uri: xmlPackagePath,
        rectype: RemoteKB.RECTYPE_PACKAGE,
        active:Boolean.TRUE,
        supportsHarvesting:true,
        activationEnabled:false
      ).save(flush: true, failOnError:true))
    }
  }

  // Assumes a clean DB
  @Ignore
  protected void setupAndRunIngestJob(String xmlPackagePath) {
    setUpDebugKb(xmlPackagePath)
    def kbGet = doGet("/erm/kbs");
    assert kbGet.size() == 1
    assert kbGet[0].name == 'DEBUG';
    kbHarvestService.triggerSync()
    // In general this shouldn't be called directly, but this cuts a minute out of waiting for job to run
    jobRunnerService.leaderTick()
    // Run twice since first tick is always ignored
    jobRunnerService.leaderTick()

    def jobsGet = doGet("/erm/jobs", [filters: ['class==org.olf.general.jobs.PackageIngestJob'], sort: ['dateCreated;DESC']]);
    assert jobsGet.size() == 1;
    def jobId = jobsGet[0].id

    def conditions = new PollingConditions(timeout: 300)
    conditions.eventually {
      def jobGet = doGet("/erm/jobs/${jobId}")
      assert jobGet?.status?.value == 'ended'
      assert jobGet?.result?.value == 'success'
    }
  }

  // Helpers to get PackageContentImpl from files and bind them
  @Ignore
  Map citationFromFile(String citation_file_name, String path) {
    String citation_file = "${path}/${citation_file_name}";

    return jsonSlurper.parse(new File(citation_file))
  }

  @Ignore
  PackageContentImpl bindMapToCitation(Map citationMap) {
    PackageContentImpl content = new PackageContentImpl()
    DataBindingUtils.bindObjectToInstance(content, citationMap)

    return content;
  }

  @Ignore
  PackageContentImpl bindMapToCitationFromFile(String citation_file_name, String path) {
    return bindMapToCitation(citationFromFile(citation_file_name, path))
  }
}
