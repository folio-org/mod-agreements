package org.olf.general

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.olf.kb.ErmResource
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.MatchKey
import org.olf.kb.TitleInstance
import org.olf.kb.CoverageStatement

import org.olf.EntitlementService
import org.olf.ErmResourceService

import grails.gorm.transactions.Transactional

import org.springframework.context.ApplicationListener
import org.springframework.context.ApplicationEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent


/**
 * This service is home to the code that listens for various events, and then triggers the responses
 */
@Slf4j
@CompileStatic
public class EventListenerService implements ApplicationListener {

  EntitlementService entitlementService
  ErmResourceService ermResourceService

  void afterUpdate(PostUpdateEvent event) {
    if (event.entityObject instanceof ErmResource) {
      ErmResource res = (ErmResource) event.entityObject
      entitlementService.handleErmResourceChange(res)
    }

    if (event.entityObject instanceof IdentifierOccurrence) {
      IdentifierOccurrence io = (IdentifierOccurrence) event.entityObject
      // Identifier has changed, add the TI to the queue.
      ermResourceService.addTiToQueue(io.title)
    }

    /* Match keys are technically not updated currently.
     * We treat key/value as a static pair and insert delete accordingly.
     * This line is simply for futureproofing/consistency
     */
    if (event.entityObject instanceof MatchKey) {
      MatchKey mk = (MatchKey) event.entityObject
      ermResourceService.addTiToQueue(mk.resource)
    }
  }

  void afterInsert(PostInsertEvent event) {
    if (event.entityObject instanceof ErmResource) {
      ErmResource res = (ErmResource) event.entityObject
      entitlementService.handleErmResourceChange(res)
    }

    if (event.entityObject instanceof MatchKey) {
      MatchKey mk = (MatchKey) event.entityObject
      ermResourceService.addTiToQueue(mk.resource)
    }
  }

  void afterDelete(PostDeleteEvent event) {
    if (event.entityObject instanceof ErmResource) {
      ErmResource res = (ErmResource) event.entityObject
      entitlementService.handleErmResourceChange(res)
    }

    if (event.entityObject instanceof MatchKey) {
      MatchKey mk = (MatchKey) event.entityObject
      ermResourceService.addTiToQueue(mk.resource)
    }
  }

  public void onApplicationEvent(org.springframework.context.ApplicationEvent event){
    if ( event instanceof AbstractPersistenceEvent ) {
      if ( event instanceof PostUpdateEvent ) {
        afterUpdate(event);
      }
      else if ( event instanceof PostInsertEvent ) {
        afterInsert(event);
      }
      else if ( event instanceof PostDeleteEvent ) {
        afterDelete(event);
      }
      else {
        //log.debug("No special handling for appliaction event of class ${event}");
      }
    }
    else {
      //log.debug("Event is not a persistence event: ${event}");
    }
  }
}

