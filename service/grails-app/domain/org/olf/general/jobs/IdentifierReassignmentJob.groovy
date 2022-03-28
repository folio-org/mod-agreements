package org.olf.general.jobs

import grails.gorm.MultiTenant
import groovy.json.JsonSlurper

import java.time.Instant

/*
  This Job will accept a JSON File containing information about identifiers
  to switch from one TI to another
 */
class IdentifierReassignmentJob extends PersistentJob implements MultiTenant<IdentifierReassignmentJob> {
  private final JsonSlurper js = new JsonSlurper()
  final Closure getWork() {
    
    final Closure theWork = { final String eventId, final String tenantId ->
    
      log.info "Running Identifier Reassignment Job"
      PersistentJob.withTransaction {
        // We should ensure the job is read into the current session.
        // This closure will probably execute in a future session
        // and we need to reread the event in.
        final PersistentJob job = PersistentJob.read(eventId)
        if (job.fileUpload) {
          identifierService.reassignFromFile(js.parse( fileUploadService.getInputStreamFor(job.fileUpload.fileObject) ))
        } else {
          log.error "No reassignment instructions file attached to the Job."
        }
      }
    }.curry( this.id )
    
    theWork
  }
  
  void beforeValidate() {
    if (!this.name) {
      // Set the name from the instant
      this.name = "Identifier reassignment ${Instant.now()}"
    }
  }
  
  static constraints = {
    fileUpload (nullable:true)
  }
}
