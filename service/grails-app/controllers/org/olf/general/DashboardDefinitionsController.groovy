package org.olf.general

import org.grails.io.support.PathMatchingResourcePatternResolver
import org.grails.io.support.Resource

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class DashboardDefinitionsController  {
  PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver()
  static String definitionsCache;

  public getDefinitions() {
    log.debug("DashboardDefinitionsController::getDefinitions")
    if (!definitionsCache) {
      Resource[] widgetDefs = resolver.getResources("classpath:sample_data/widgetDefinitions/*")
      definitionsCache = "[${widgetDefs.collect { new String( it.getInputStream().readAllBytes()) }.join(',')}]"
    }
    render(text: definitionsCache, contentType: 'application/json')
  }
}

