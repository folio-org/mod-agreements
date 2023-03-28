package org.olf.general.pushKB
import java.time.Instant

import grails.gorm.MultiTenant
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.multitenancy.Tenants
import grails.util.Holders

/*
 * External pushKB service is going to "push" in chunks of data at a time
 * to be handled synchronously as a HTTP call. This means there will no longer be a Job
 * or JobContext, so instead we set up a PushKBChunk class to house the LogContainer
 * needed to store and view logging for the ERM side of the ingest.
 *
 */

public class PushKBSession implements MultiTenant<PushKBSession> {
  String id
  
  /* A unique identifier given to the session by the external process,
   * used for grouping.
   * TODO ensure that if no sessionId is provided, that we assume the chunk
   * is in its own session.
   */
  String sessionId

  Instant dateCreated
  Instant lastUpdated

  Set<PushKBChunk> chunks = []
  static hasMany = [
    chunks: PushKBChunk,
  ]

  static mappedBy = [
    chunks: 'session',
  ]

    static mapping = {
            table name: 'push_kb_session'
             id column:'pkbs_id', generator: 'uuid2', length:36
        version column:'pkbs_version'
      sessionId column:'pkbs_session_id'
    dateCreated column:'pkbs_date_created'
    lastUpdated column:'pkbs_last_updated'
        chunks cascade: 'all-delete-orphan'
  }

}