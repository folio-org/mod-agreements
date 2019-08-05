package org.olf.general.jobs
import java.time.Instant
import com.k_int.web.toolkit.refdata.CategoryId
import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.events.EventPublisher
import grails.gorm.MultiTenant
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.multitenancy.Tenants
import grails.util.Holders

@DirtyCheck
abstract class PersistentJob implements MultiTenant<PersistentJob> {
  
  static transients = ['work']
    
  String id
  String name
  
  @CategoryId('PersistentJob.Status') // Workaround for a bug in toolkit creating a category for each extension even when not specified.
  @Defaults(['Queued', 'In progress', 'Ended'])
  RefdataValue status
  
  Instant dateCreated
  Instant started
  Instant ended
  
  @CategoryId('PersistentJob.Result') // Workaround for a bug in toolkit creating a category for each extension even when not specified.
  @Defaults(['Success', 'Partial success', 'Failure', 'Interrupted'])
  RefdataValue result
  
  static mapping = {
    tablePerHierarchy false
                   id generator: 'uuid2', length:36
                 name column:'job_name'
               status column:'job_status_fk'
          dateCreated column:'job_date_created'
              started column:'job_started'
                ended column:'job_ended'
               result column:'job_result_fk'
  }

  static constraints = {
            name (nullable:false, blank:false)
          status (nullable:false)
     dateCreated (nullable:true)
         started (nullable:true)
           ended (nullable:true)
          result (nullable:true)
  }
  
  def afterInsert () {
    // Ugly work around events being raised on multi-tenant GORM entities not finding subscribers
    // from the root context.
    JobRunnerService jrs = Holders.applicationContext.getBean('jobRunnerService')
    jrs.handleNewJob(this.id, Tenants.currentId())
  }
  
  List<LogEntry> getErrorLog() {
    LogEntry.findAllByOriginAndType (this.id, LogEntry.TYPE_ERROR, [sort: 'dateCreated', order: "asc"])
  }
  
  List<LogEntry> getInfoLog() {
    LogEntry.findAllByOriginAndType (this.id, LogEntry.TYPE_INFO, [sort: 'dateCreated', order: "asc"])
  }
  
  List<LogEntry> getFullLog() {
    LogEntry.findAllByOrigin(this.id, [sort: 'dateCreated', order: "asc"])
  }
  
  void begin () {
    this.started = Instant.now()
    this.statusFromString = 'In progress'
    this.save(failOnError: true, flush:true)
  }
  
  void end () {
    this.ended = Instant.now()
    this.statusFromString = 'Ended'
    if (!result) {
      // If errors then set to partial.
      if (getErrorLog()) {
        this.resultFromString = 'Partial success'
      } else {
        this.resultFromString = 'Success'
      }
    }
    this.save( failOnError: true, flush:true )
  }
  
  void fail() {
    this.resultFromString = 'Failure'
    end()
  }
  
  void interrupted() {
    this.resultFromString = 'Interrupted'
    end()
  }
  
  abstract Runnable getWork()
  
  String toString() {
    "${name}"
  }
}
