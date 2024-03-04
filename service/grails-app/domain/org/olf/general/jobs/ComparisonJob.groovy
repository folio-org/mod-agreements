package org.olf.general.jobs

import java.sql.Blob

import javax.persistence.Lob

import org.hibernate.engine.jdbc.BlobProxy
import org.olf.ComparisonService
import org.olf.erm.ComparisonPoint

import grails.gorm.MultiTenant

class ComparisonJob extends PersistentJob implements MultiTenant<ComparisonJob>{

  @Lob
  Blob fileContents

  static mapping = {
     fileContents column: 'cj_file_contents', lazy: true
  }
  
  Set<ComparisonPoint> comparisonPoints = []
  
  static hasMany = [
    comparisonPoints: ComparisonPoint
  ]
	
	@Override
	public Type getType() {
		return Type.TASK
	}
  
  final Closure getWork() {
    final Closure theWork = { final String jobId, final String tenantId ->
      log.info "Run the comparison"
      ComparisonJob.withTransaction {
        
        ComparisonJob job = ComparisonJob.get(jobId)
        File out = File.createTempFile(jobId, tenantId)
        out.withOutputStream { OutputStream os ->
          (comparisonService as ComparisonService).compare(os, job.comparisonPoints as ComparisonPoint[])
        }

        // Saevd to temp file get the length.
        final long fs = out.length()
        log.debug "File size is ${fs}"
        
        // Write to blob.
        out.withInputStream { InputStream is ->
          job.fileContents = BlobProxy.generateProxy(is, fs)
          job.save(flush:true, failOnError: true)
        }
      }
    }.curry( this.id )
    theWork
  }
  
  static constraints = {
    comparisonPoints (minSize: 2, nullable: false)
    fileContents nullable: true, bindable: false
  }
}
