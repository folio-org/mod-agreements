package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class PushKBController {
  PushKBService pushKBService
  /*
   * Accept a list of packages of the form packageSchema -- but ignore ALL contentItems
   * (those will be handled later)
   * 
   * This will be a synchronous process. It will only return 200 OK when the process has finished
   * At that point the caller can POST to the endpoint again with the next set of packages
   */
  public pushPkg() {
    log.debug("PushKBController::pushPkg")

    final bindObj = request.JSON as Map
    Map pushPkgResult = pushKBService.pushPackages(bindObj.records)
    if (pushPkgResult.success == false) {
      String messageString = pushPkgResult?.errorMessage ?: 'Something went wrong'
      respond ([message: messageString, statusCode: 500])
    } else {
      respond ([message: "pushPkg successful: ${pushPkgResult}", statusCode: 200])
    }
  }

  public pushPci() {
    log.debug("PushKBController::pushPci")

    final bindObj = request.JSON as Map
    Map pushPCIResult = pushKBService.pushPCIs(bindObj.records)
    if (pushPCIResult.success == false) {
      String messageString = pushPCIResult?.errorMessage ?: 'Something went wrong'
      respond ([message: messageString, statusCode: 500])
    } else {
      respond ([message: "pushPci successful: ${pushPCIResult}", statusCode: 200])
    }
  }
}

