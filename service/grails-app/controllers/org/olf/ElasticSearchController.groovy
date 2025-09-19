package org.olf

import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.InfoResponse

import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@CurrentTenant
class ElasticSearchController {

  @Autowired // Can we do this in a shared interface in java instead -- to keep ES and OS integrations working both for grails apps and MN ones?
  ElasticsearchClient esClient;

  public test() {
    log.info("Hitting the OpenSearch implementation")
    def result = [:]

    InfoResponse resp = esClient.info();

    log.debug("WHAT IS INFO: ${resp}")

    result.status = 'OK'

    render result as JSON
  }
}
