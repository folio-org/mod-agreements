package org.olf.general.events

import java.nio.charset.StandardCharsets

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import javax.servlet.http.HttpServletRequest
import org.apache.kafka.clients.producer.ProducerRecord
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestContextHolder

/**
 * Publishes {@link DomainEvent} messages to Kafka after the current transaction
 * commits. If no transaction is active the publish is SKIPPED with a WARN —
 * emitting without commit guarantee risks phantom events. Kafka failures are
 * logged and swallowed; they must never fail the originating REST request.
 *
 * Assumes {@code PROPAGATION_REQUIRED} through the caller's tx chain — the
 * sync fires on whichever tx is currently active, so a nested
 * {@code REQUIRES_NEW} would publish before the outer tx commits and risk a
 * phantom event on outer rollback.
 *
 * Partition key is the entity id so per-entity CREATE → UPDATE → DELETE order
 * is preserved on the same partition.
 *
 * The tenant, user id and request id headers of the originating request are
 * copied to the Kafka record headers, so consumers know who made the change and
 * which request it belongs to. Only these are copied: the request also carries
 * credentials (the user's token, the sidecar's signature) that must not leave
 * it. Events published outside a request (background jobs) carry none.
 */
@Slf4j
class EventPublisherService {

  private static final List<String> FORWARDED_HEADERS = ['x-okapi-tenant', 'x-okapi-user-id', 'x-okapi-request-id']

  @Autowired
  KafkaTemplate<String, String> kafkaTemplate

  @Autowired
  ObjectMapper objectMapper

  void publishAfterCommit(String topic, DomainEvent event) {
    if (topic == null || event == null) {
      log.warn("publishAfterCommit called with topic={} event={} — ignoring", topic, event)
      return
    }

    String key = partitionKey(event)
    String payload
    try {
      payload = objectMapper.writeValueAsString(event)
    } catch (Exception e) {
      log.error("Failed to serialize DomainEvent for topic ${topic}", e)
      return
    }

    Map<String, String> headers = okapiHeaders()

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        void afterCommit() {
          sendToKafka(topic, key, payload, headers)
        }
      })
    } else {
      // No active tx — skip rather than publish, to avoid phantom events on later rollback.
      log.warn("publishAfterCommit called with no active transaction — skipping publish to {}", topic)
    }
  }

  private static String partitionKey(DomainEvent event) {
    Object payload = event.newValue ?: event.oldValue
    if (payload instanceof Map) {
      Object id = ((Map) payload).get('id')
      if (id != null) return id.toString()
    }
    return event.eventId?.toString()
  }

  private Map<String, String> okapiHeaders() {
    def attributes = RequestContextHolder.getRequestAttributes()
    if (!(attributes instanceof GrailsWebRequest)) return [:]
    HttpServletRequest request = ((GrailsWebRequest) attributes).currentRequest
    Map<String, String> headers = [:]
    for (String name : FORWARDED_HEADERS) {
      String value = request.getHeader(name)
      if (value != null) {
        headers[name] = value
      }
    }
    return headers
  }

  private void sendToKafka(String topic, String key, String payload, Map<String, String> headers) {
    try {
      ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload)
      headers.each { String name, String value ->
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8))
      }
      kafkaTemplate.send(record)
    } catch (Exception e) {
      log.error("Failed to publish DomainEvent to topic ${topic}", e)
    }
  }
}