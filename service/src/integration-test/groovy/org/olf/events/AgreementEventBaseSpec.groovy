package org.olf.events

import java.time.Duration

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

import org.olf.BaseSpec

import groovy.util.logging.Slf4j
import spock.lang.Shared

@Slf4j
abstract class AgreementEventBaseSpec extends BaseSpec {

  @Shared
  String kafkaBootstrapServers =
    "${System.getenv('KAFKA_HOST') ?: 'localhost'}:${System.getenv('KAFKA_PORT') ?: '9092'}".toString()

  String topicFor(String entity) {
    String envPrefix = System.getenv('ENV') ?: 'folio'
    String tenantCollection = System.getenv('KAFKA_TENANT_COLLECTION') ?: 'ALL'
    String tenantSegment = (tenantCollection == 'ALL') ? 'ALL' : tenantId
    "${envPrefix}.${tenantSegment}.agreements.${entity}".toString()
  }

  List<Map> pollForEvents(String topic, int minRecords = 1, long timeoutMs = 10_000L) {
    Properties props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-${UUID.randomUUID()}".toString())
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, 'earliest')
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, 'false')
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.name)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.name)

    List<Map> events = []
    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)
    try {
      consumer.subscribe([topic])
      long deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline && events.size() < minRecords) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500))
        for (ConsumerRecord<String, String> r : records) {
          log.debug("Got event on {} key={}: {}", topic, r.key(), r.value())
          events << (jsonSlurper.parseText(r.value()) as Map)
        }
      }
    } finally {
      consumer.close(Duration.ofSeconds(5))
    }
    return events
  }
}