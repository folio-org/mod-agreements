package org.olf.general.pushKB

import com.k_int.okapi.OkapiTenantAwareController

import com.k_int.web.toolkit.utils.GormUtils
import org.olf.general.jobs.LogEntry

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class PushKBChunkController extends OkapiTenantAwareController<PushKBChunk>  {
  PushKBChunkController() {
    super(PushKBChunk)
  }

  def fullLog( String pushKBChunkId ) {
    respond doTheLookup (LogEntry, {
      eq 'origin', pushKBChunkId

      order 'dateCreated', 'asc'
    })
  }

  def fullLogStream( String pushKBChunkId ) {
    GormUtils.withNewReadOnlyTransaction {
      respond doChunkedStreamingLookup (LogEntry, 250, {
        eq 'origin', pushKBChunkId
  
        order 'dateCreated', 'asc'
      })
    }
  }
  
  def infoLog( String pushKBChunkId ) {
    respond doTheLookup (LogEntry, {
      eq 'origin', pushKBChunkId
      eq 'type', LogEntry.TYPE_INFO

      order 'dateCreated', 'asc'
    })
  }
  
  def infoLogStream ( String pushKBChunkId ) {
    
    GormUtils.withNewReadOnlyTransaction {
      respond doChunkedStreamingLookup(LogEntry, 250, {
        
        eq 'origin', pushKBChunkId
        eq 'type', LogEntry.TYPE_INFO
        order 'dateCreated', 'asc'
      })
    }
  }

  def errorLog( String pushKBChunkId ) {
    respond doTheLookup (LogEntry, {
      eq 'origin', pushKBChunkId
      eq 'type', LogEntry.TYPE_ERROR
      
      order 'dateCreated', 'asc'
    })
  }

  def errorLogStream( String pushKBChunkId ) {
    
    GormUtils.withNewReadOnlyTransaction {
      respond doChunkedStreamingLookup (LogEntry, 250, {
        eq 'origin', pushKBChunkId
        eq 'type', LogEntry.TYPE_ERROR
  
        order 'dateCreated', 'asc'
      })
    }
  }
}

