package org.olf.general.events

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.CompileStatic

/**
 * Flat domain-event envelope: { eventId, eventTs, tenant, type, old, new }.
 * NON_NULL omits absent fields — consumers switch on {@code type} to know which
 * of {@code old} / {@code new} are present.
 *
 * Fields are named {@code oldValue} / {@code newValue} because {@code old} /
 * {@code new} are Groovy reserved words; {@code @JsonProperty} getters put the
 * correct names on the wire.
 */
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
class DomainEvent<T> {

  UUID eventId
  long eventTs
  String tenant
  DomainEventType type

  T oldValue
  T newValue

  @JsonProperty('old')
  T getOldValue() { oldValue }

  @JsonProperty('new')
  T getNewValue() { newValue }

  static <T> DomainEvent<T> createEvent(T newValue, String tenant) {
    new DomainEvent<T>(
      eventId : UUID.randomUUID(),
      eventTs : System.currentTimeMillis(),
      tenant  : tenant,
      type    : DomainEventType.CREATE,
      newValue: newValue
    )
  }

  static <T> DomainEvent<T> updateEvent(T oldValue, T newValue, String tenant) {
    new DomainEvent<T>(
      eventId : UUID.randomUUID(),
      eventTs : System.currentTimeMillis(),
      tenant  : tenant,
      type    : DomainEventType.UPDATE,
      oldValue: oldValue,
      newValue: newValue
    )
  }

  static <T> DomainEvent<T> deleteEvent(T oldValue, String tenant) {
    new DomainEvent<T>(
      eventId : UUID.randomUUID(),
      eventTs : System.currentTimeMillis(),
      tenant  : tenant,
      type    : DomainEventType.DELETE,
      oldValue: oldValue
    )
  }
}