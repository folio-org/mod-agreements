package org.olf.general.events

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
 */
@Slf4j
class EventPublisherService {

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

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        void afterCommit() {
          sendToKafka(topic, key, payload)
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

  private void sendToKafka(String topic, String key, String payload) {
    try {
      kafkaTemplate.send(topic, key, payload)
    } catch (Exception e) {
      log.error("Failed to publish DomainEvent to topic ${topic}", e)
    }
  }
}