package org.olf

import grails.gorm.multitenancy.CurrentTenant
import grails.web.databinding.DataBinder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import grails.converters.JSON

import org.springframework.validation.BindingResult
import org.olf.dataimport.internal.InternalPackageImplWithPackageContents
import grails.gorm.transactions.Transactional

@Slf4j
@CurrentTenant
class AdminController implements DataBinder{

  def packageIngestService
  def knowledgeBaseCacheService
  def ermHousekeepingService
  def entitlementLogService
  def fileUploadService
  def kbManagementService
  def kbHarvestService
  def packagePullService

  static allowedMethods = [pullPackage: 'POST']

  public AdminController() {
  }

  /**
   * Expose a load package endpoint so developers can use curl to upload package files in their development systems
   * submit a form with the sinle file upload parameter "package_file".
   */
  public loadPackage() {
    def result = [:]
    log.debug("AdminController::loadPackage");
    // Single file
    def file = request.getFile("package_file")
    if ( file ) {
      def jsonSlurper = new JsonSlurper()
      
      def package_data = new InternalPackageImplWithPackageContents()
      BindingResult br = bindData (package_data, jsonSlurper.parse(file.inputStream))
      if (br?.hasErrors()) {
        br.allErrors.each {
          log.debug "\t${it}"
        }
        return
      }

      result = packageIngestService.upsertPackage(package_data)
    }
    else {
      log.warn("No file")
    }

    render result as JSON
  }

  /**
   *  Temporary helper method which provides a REST endpoint to trigger an update of the package cache from
   *  remote KBs
   */
  public triggerCacheUpdate() {
    knowledgeBaseCacheService.triggerCacheUpdate()
  }

  public triggerSync() {
    kbHarvestService.triggerSync()
    def result= [:]
    result.status = 'OK'
    render result as JSON
  }

  /** Queue a one-off OAI harvest for an existing synchronizing package. */
  public pullPackage() {
    def body
    try {
      body = request.JSON
    } catch (Exception e) {
      render status: 400, contentType: 'application/json', text: ([error: 'Invalid JSON body'] as JSON).toString()
      return
    }
    if (!(body instanceof Map) || !(body.packageId instanceof String) || !body.packageId.trim()) {
      render status: 400, contentType: 'application/json', text: ([error: 'A JSON packageId is required'] as JSON).toString()
      return
    }
    try {
      String jobId = packagePullService.enqueue(body.packageId)
      response.setHeader('Location', "/erm/jobs/${jobId}")
      render status: 202, contentType: 'application/json', text: ([jobId: jobId] as JSON).toString()
    } catch (org.olf.kb.PackagePullException e) {
      render status: e.status, contentType: 'application/json', text: ([error: e.message] as JSON).toString()
    }
  }

  public triggerActivationUpdate() {
    def result = [:]
    knowledgeBaseCacheService.triggerActivationUpdate();
    render result as JSON
  }

  public triggerHousekeeping() {
    log.info("AdminController::triggerHousekeeping()");
    def result = [:]
    ermHousekeepingService.triggerHousekeeping()
    result.status = 'OK'
    log.info("AdminController::triggerHousekeeping() complete: ${result}");
    render result as JSON
  }

  public triggerEntitlementLogUpdate() {
    def result = [:]
    log.debug("AdminController::triggerEntitlementLogUpdate");

    entitlementLogService.triggerUpdate()

    result.status = 'OK'
    render result as JSON
  }

  public triggerEntitlementEholdings() {
    def result = [:]
    // Allow operators to bypass the buffer window with ?force=true; the _timer never passes
    // this flag, so its hourly invocations continue to honour EHOLDINGS_SYNC_BUFFER.
    boolean force = params.boolean('force') ?: false
    log.info("AdminController::triggerEntitlementEholdings (force=${force})")
    kbManagementService.triggerEntitlementEholdingsJob(force)
    result.status = 'OK'
    render result as JSON
  }

  /**
   * Trigger migration of uploaded LOB objects from PostgresDB to configured S3/MinIO
   */
  @Transactional
  public triggerDocMigration() {
    def result = [:]
    log.debug("AdminController::triggerDocMigration");
    fileUploadService.migrateAtMost(0,'LOB','S3'); // n, FROM, TO
    result.status = 'OK'
    render result as JSON
  }
}

