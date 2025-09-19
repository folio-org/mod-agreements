package org.olf

import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class ElasticSearchController {

  public test() {
    log.info("Hitting the OpenSearch implementation")
    def result = [:]

    result.status = 'OK'

    render result as JSON
  }
}
