package org.olf.general.pushKB
import java.time.Instant

import org.olf.general.jobs.LogEntry

import grails.gorm.MultiTenant
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.multitenancy.Tenants
import grails.util.Holders

import javax.persistence.Transient

/*
 * External pushKB service is going to "push" in chunks of data at a time
 * to be handled synchronously as a HTTP call. This means there will no longer be a Job
 * or JobContext, so instead we set up a PushKBChunk class to house the LogContainer
 * needed to store and view logging for the ERM side of the ingest.
 *
 * These belong to a PushKBSession, which groups the chunks together under a common name
 */

public class PushKBChunk implements MultiTenant<PushKBChunk> {
  String id
  /* An identifier we can use to understand when a chunk is a
   * repeat of another failed chunk. If no chunkId is provided we can assume its
   * always a new chunk.
   */
  String chunkId

  Instant dateCreated
  Instant lastUpdated

  static belongsTo = [session: PushKBSession]

  static mapping = {
            table name: 'push_kb_chunk'
             id column:'pkbc_id', generator: 'uuid2', length:36
        version column:'pkbc_version'
        session column:'pkbc_session_fk'
        chunkId column:'pkbc_chunk_id'
    dateCreated column:'pkbc_date_created'
    lastUpdated column:'pkbc_last_updated'
  }

  @Transient
  long getErrorLogCount() {
    Tenants.withCurrent {
      LogEntry.countByOriginAndType (this.id, LogEntry.TYPE_ERROR)
    }
  }

  @Transient
  List<LogEntry> getErrorLog() {
    Tenants.withCurrent {
      LogEntry.findAllByOriginAndType (this.id, LogEntry.TYPE_ERROR, [sort: 'dateCreated', order: "asc"])
    }
  }

  @Transient
  long getInfoLogCount() {
    Tenants.withCurrent {
      LogEntry.countByOriginAndType (this.id, LogEntry.TYPE_INFO)
    }
  }
  
  @Transient
  List<LogEntry> getInfoLog() {
    Tenants.withCurrent {
      LogEntry.findAllByOriginAndType (this.id, LogEntry.TYPE_INFO, [sort: 'dateCreated', order: "asc"])
    }
  }

  @Transient
  long getFullLogCount() {
    Tenants.withCurrent {
      LogEntry.countByOrigin (this.id)
    }
  }

  @Transient
  List<LogEntry> getFullLog() {
    Tenants.withCurrent {
      LogEntry.findAllByOrigin(this.id, [sort: 'dateCreated', order: "asc"])
    }
  }
}