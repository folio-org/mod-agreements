package org.olf.general.jobs

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants

import groovy.json.JsonSlurper

import java.time.Instant


/*
 If this job contains a payload, we use that to rematch certain TIs.
 Else we rely on the "since" variable to rematch on all TIs which have "changed" (for some definition of changed) since the timestamp
 If neither exist, error out
 */
class ResourceRematchJob extends PersistentJob implements MultiTenant<ResourceRematchJob>{
  Instant since

  static mapping = {
    since column: 'since'
  }

  final Closure getWork() {
    final JsonSlurper js = new JsonSlurper()

    final Closure theWork = { final String eventId, final String tenantId ->
      log.info "Running Resource Rematch Job"
      PersistentJob.withTransaction {
        // We should ensure the job is read into the current session.
        // This closure will probably execute in a future session
        // and we need to reread the event in.
        final PersistentJob job = PersistentJob.read(eventId)
        if (job.fileUpload) {
          kbManagementService.rematchResourcesForTIs(js.parse( fileUploadService.getInputStreamFor(job.fileUpload.fileObject) ))
        } else if (job.since) {
          /*
           * IMPORTANT this automatic rematch process is extremely non-performant,
           * and also it will run on ALL tis after first creation, which is not ideal.
           * For lotus release this will simply be disabled, returning immediately.
           * SEE ALSO KbManagementService triggerRematch method
           */
          
          //kbManagementService.runRematchProcess(since)
        } else {
          throw new RuntimeException("No TIs specified through payload or timestamp.")
        }
      }
    }.curry( this.id )
    
    theWork
  }

  static constraints = {
    since(nullable:true);
	}

  void beforeValidate() {
    if (!this.name) {
      // Set the name from the instant
      this.name = "Resource rematch job ${Instant.now()}"
    }
  }
}
