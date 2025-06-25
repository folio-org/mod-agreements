package org.olf.general.jobs

import grails.converters.JSON
import grails.gorm.MultiTenant
import groovy.json.JsonSlurper
import org.olf.general.ResourceDeletionJobType
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg

class ResourceDeletionJob extends PersistentJob implements MultiTenant<ResourceDeletionJob>{
  String packageIds // This could just store the JSON that would go into the method as a param
  ResourceDeletionJobType deletionJobType; // Type PackageDeletionJob

  final Closure getWork() {
    final Closure theWork = { final String jobId, final String tenantId ->
      log.info "Attempting to run job type: ${deletionJobType}"

      List<String> idList = new JsonSlurper().parseText(packageIds) as List<String>

      switch (deletionJobType) {

        case ResourceDeletionJobType.PackageDeletionJob:
          log.info "Executing PackageDeletionJob for IDs: ${idList}"
          ermResourceService.deleteResourcesPkg(idList)
          break

        case ResourceDeletionJobType.PciDeletionJob:
          log.info "Executing PciDeletionJob for IDs: ${idList}"
          ermResourceService.deleteResources(idList, PackageContentItem)
          break

        default:
          log.error "Unknown or unhandled ResourceDeletionJobType: ${deletionJobType}. Aborting job."
          this.status = Job.Status.FAILED
          this.result = [ message: "Unknown job type: ${deletionJobType}" ]
          this.save(flush: true)
          break
      }
    }.curry( this.id )
    theWork
  }

  static constraints = {
    packageIds     nullable:false
    deletionJobType nullable: false
  }

  static mapping = {
    table 'resource_deletion_job'
    version false
    packageIds    column: 'package_ids', type: 'text'
    deletionJobType column: 'deletion_job_type'
  }
}
