package org.olf

import grails.gorm.multitenancy.CurrentTenant
import grails.web.databinding.DataBinder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import grails.converters.JSON
import org.olf.kb.RemoteKB
import org.springframework.validation.BindingResult
import org.olf.dataimport.internal.InternalPackageImpl
import org.olf.kb.KBCacheUpdater

@Slf4j
@CurrentTenant
class AdminController implements DataBinder{

  def packageIngestService
  def knowledgeBaseCacheService
  def ermHousekeepingService
  def entitlementLogService

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
      
      def package_data = new InternalPackageImpl()
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

  public pullPackage() {
    def result = [:]
    RemoteKB rkb = RemoteKB.findByName(params.kb)

    if ( rkb ) {
      log.debug("Located KB record -- ${rkb}");
      try {
        def import_params = [:]
        import_params << params
        import_params.principal = rkb.principal
        import_params.credentials = rkb.credentials
        Class cls = Class.forName(rkb.type)
        KBCacheUpdater cache_updater = cls.newInstance();
        log.debug("Import package: ${import_params}");
        result = cache_updater.importPackage(import_params, knowledgeBaseCacheService);
      }
      catch ( Exception e ) {
        log.error("Problem pulling package from ${params.kb}",e);
      }
    }

    render result as JSON
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
}

