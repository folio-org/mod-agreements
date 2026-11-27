  package org.olf.general.events

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Resolves a Kafka topic name from the running tenant + a per-entity suffix.
 *
 *   {env}.{tenantCollection}.agreements.{entity}
 *
 * {@code tenantCollection} is the literal {@code ALL} when
 * {@code KAFKA_TENANT_COLLECTION=ALL} (default, matching folio-kafka-wrapper
 * and mod-inventory-storage) — all tenants share one topic per entity, and
 * consumers route by the {@code tenant} field in the event envelope. Any other
 * value falls back to per-tenant topics using the current tenant id.
 */
@CompileStatic
@Component
class TopicNameResolver {

  private static final String MODULE_SHORT_NAME = 'agreements'
  private static final String ALL = 'ALL'

  private final TenantContext tenantContext
  private final String env
  private final String tenantCollection

  @Autowired
  TopicNameResolver(TenantContext tenantContext) {
    this.tenantContext = tenantContext
    this.env = (System.getenv('ENV') ?: 'folio')
    this.tenantCollection = (System.getenv('KAFKA_TENANT_COLLECTION') ?: ALL)
  }

  String topicFor(String entity) {
    String tenantSegment = (tenantCollection == ALL) ? ALL : tenantContext.currentTenant()
    "${env}.${tenantSegment}.${MODULE_SHORT_NAME}.${entity}".toString()
  }
}