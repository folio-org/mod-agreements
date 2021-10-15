package org.olf.general.jobs

import org.springframework.http.HttpStatus
import org.springframework.transaction.TransactionDefinition

import com.k_int.okapi.OkapiTenantAwareController
import com.k_int.web.toolkit.SimpleLookupService
import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.utils.GormUtils
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import groovy.util.logging.Slf4j


@Slf4j
@CurrentTenant
class PersistentJobController extends OkapiTenantAwareController<PersistentJob> {

  // Read only. Doesn't allow posts etc by default.
  public PersistentJobController() {
    super(PersistentJob, true)
  }

  @Transactional
  def delete() {
    def instance = queryForResource(params.id)

    // Not found.
    if (instance == null) {
      transactionStatus.setRollbackOnly()
      notFound()
      return
    }

    final disallowedStatus = [
      instance.lookupStatus('In progress').id
    ]

    // Return invalid method if the status is disallowed
    if (disallowedStatus.contains(instance.statusId)) {
      render status: HttpStatus.METHOD_NOT_ALLOWED.value()
      return
    }

    deleteResource instance

    render status: HttpStatus.NO_CONTENT
  }

  @Transactional
  def save () {
    final Class type = params.type ? Class.forName("org.olf.general.jobs.${GrailsNameUtils.getClassName(params.type as String)}Job") : null

    if(!(type && PersistentJob.isAssignableFrom(type))) {
      return render (status: HttpStatus.NOT_FOUND)
    }

    // Lookup the default "queued" value here first as session flushes later are causing issues.
    final RefdataValue queuedStatus = PersistentJob.lookupStatus('queued')

    final PersistentJob instance = type.newInstance()
    bindData instance, getObjectToBind()
    instance.status = queuedStatus
    instance.validate()
    if (instance.hasErrors()) {
      transactionStatus.setRollbackOnly()
      respond instance.errors, view:'create' // STATUS CODE 422
      return
    }

    saveResource instance
    respond instance
  }

  def listTyped () {
    try {
      final Class type = params.type ? Class.forName("org.olf.general.jobs.${GrailsNameUtils.getClassName(params.type)}Job") : null

      if(!(type && PersistentJob.isAssignableFrom(type))) {
        return render (status: HttpStatus.NOT_FOUND)
      }

      // Do the lookup
      respond doTheLookup {
        eq 'class', type
      }
    } catch (ClassNotFoundException cnf) {
      return render (status: HttpStatus.NOT_FOUND)
    }
  }

  def fullLog( String persistentJobId ) {
    respond doTheLookup (LogEntry, {
      eq 'origin', persistentJobId

      order 'dateCreated', 'asc'
    })
  }

  def fullLogStream( String persistentJobId ) {
    GormUtils.withNewReadOnlyTransaction {
      respond doChunkedStreamingLookup (LogEntry, 250, {
        eq 'origin', persistentJobId
  
        order 'dateCreated', 'asc'
      })
    }
  }
  
  def infoLog( String persistentJobId ) {
    respond doTheLookup (LogEntry, {
      eq 'origin', persistentJobId
      eq 'type', LogEntry.TYPE_INFO

      order 'dateCreated', 'asc'
    })
  }
  
  def infoLogStream ( String persistentJobId ) {
    
    GormUtils.withNewReadOnlyTransaction {
      respond doChunkedStreamingLookup(LogEntry, 250, {
        
        eq 'origin', persistentJobId
        eq 'type', LogEntry.TYPE_INFO
        order 'dateCreated', 'asc'
      })
    }
  }

  def errorLog( String persistentJobId ) {
    respond doTheLookup (LogEntry, {
      eq 'origin', persistentJobId
      eq 'type', LogEntry.TYPE_ERROR
      
      order 'dateCreated', 'asc'
    })
  }

  def errorLogStream( String persistentJobId ) {
    
    GormUtils.withNewReadOnlyTransaction {
      respond doChunkedStreamingLookup (LogEntry, 250, {
        eq 'origin', persistentJobId
        eq 'type', LogEntry.TYPE_ERROR
  
        order 'dateCreated', 'asc'
      })
    }
  }

  @Transactional(readOnly=true)
  def downloadFileObject(String persistentJobId) {

    ComparisonJob instance = ComparisonJob.read(persistentJobId)

    // Not found.
    if (instance == null) {
      notFound()
      return
    }

    // Return invalid method if the status is disallowed
    if (instance.statusId != instance.lookupStatus('Ended').id) {
      render status: HttpStatus.METHOD_NOT_ALLOWED.value()
      return
    }


    render file: instance.fileContents.binaryStream, contentType: 'text/json', fileName: "job-${persistentJobId}.json"
  }
}
