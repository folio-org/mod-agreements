package org.olf.general.jobs

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import javax.annotation.PostConstruct

import org.hibernate.SessionFactory
import org.olf.ComparisonService
import org.olf.CoverageService
import org.olf.DocumentAttachmentService
import org.olf.ImportService
import org.olf.IdentifierService
import org.olf.MatchKeyService
import org.olf.KbHarvestService
import org.olf.KbManagementService

import com.k_int.okapi.OkapiTenantAdminService
import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.events.EventPublisher
import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import services.k_int.core.AppFederationService
import services.k_int.core.FederationLockDataService
import services.k_int.core.FolioLockService

import com.k_int.web.toolkit.files.FileUploadService

@Slf4j
//@CompileStatic
class JobRunnerService implements EventPublisher {
  
  // Any auto injected beans here can be accessed within the `work` runnable
  // of the job itself.
  OkapiTenantAdminService okapiTenantAdminService
  KbHarvestService kbHarvestService
  KbManagementService kbManagementService
  CoverageService coverageService
  DocumentAttachmentService documentAttachmentService
  ImportService importService
  ComparisonService comparisonService
  IdentifierService identifierService
  MatchKeyService matchKeyService
  SessionFactory sessionFactory

  // Access to the inputStream of FileObjects is now via this service instead of directly
  // to the LOB attached to the FileObject. Inject this here so it is available to the work
  // closure in ./grails-app/domain/org/olf/general/jobs/KbartImportJob.groovy and ./grails-app/domain/org/olf/general/jobs/PackageImportJob.groovy
  FileUploadService fileUploadService
  
  final int CONCURRENT_JOBS_GLOBAL = 2 // We need to be careful to not completely tie up all our resource
//  final int CONCURRENT_JOBS_TENANT = 1
  private ThreadPoolExecutor executorSvc
  
  @PostConstruct
  void init() {
    // Set up the Executor
    
    // SO: This is not ideal. We don't want to limit jobs globally to 1 ideally. It should be 
    // 1 per tenant, but that will involve implementing custom handling for the queue and executor.
    // While we only have 1 tenant, this will suffice.
    executorSvc = new ThreadPoolExecutor(
      CONCURRENT_JOBS_GLOBAL, // Core pool Idle threads.
      CONCURRENT_JOBS_GLOBAL, // Treads max.
      1000, // Millisecond wait.
      TimeUnit.MILLISECONDS, // Makes the above wait time in 'seconds'
      new LinkedBlockingQueue<Runnable>() // Blocking queue
    )
//    executorSvc = Executors.newFixedThreadPool(1)
    
    // Get the list of jobs from all tenants that were interrupted by app termination and
    // set their states appropriately.
    
    // Raise an event to say we are ready.
    notify('jobs:job_runner_ready')
  }
  
//  void setInterruptedJobsState() {
//    log.debug "Checking for interrupted jobs"
//    RefdataValue inProgress = PersistentJob.lookupStatus('In progress')
//    PersistentJob.findAllByStatus(inProgress).each { PersistentJob j ->
//      j.interrupted()
//    }
//  }
  
//  private Map<Instant, List<String>> initializeTenantJobs( final String tenant_schema_id ) {
//    
//    log.debug "initializeTenantJobs for ${tenant_schema_id}"
//    final Map<Instant, List<String>> queue_order = new TreeMap<Instant, List<String>>()  
//    Tenants.withId(tenant_schema_id) {
//      setInterruptedJobsState()
//      
//      // Now load all queued jobs.
//      RefdataValue queued = PersistentJob.lookupStatus('Queued')
//      PersistentJob.findAllByStatus(queued).each { PersistentJob j ->
//        queue_order.put(j.dateCreated, [j.id, tenant_schema_id])
//      }
//    }
//    
//    queue_order
//  }
  
//  private final CopyOnWriteArrayList<String> deferredTenants = new CopyOnWriteArrayList<String>()
//  
//  private void enqueueJobMap (final Map<Instant, List<String>> queue_order) {
//    
//    // Enqueue each job.
//    (queue_order.keySet() as List).reverse().each { Instant created ->
//      List<String> ids = queue_order[created]
//      enqueueJob(ids[0], ids[1])
//    }
//  }
  
//  void populateJobQueue() {
//    log.debug "Populating intial job queue"
//    final Map<Instant, List<String>> queue_order = new TreeMap<Instant, List<String>>()
//        
//    okapiTenantAdminService.allConfiguredTenantSchemaNames().each { tenant_schema_id ->
//      
//      try {
//        queue_order.putAll( initializeTenantJobs( tenant_schema_id ) )
//      } catch (Exception e) {
//        // TODO: We should rework how "enabled" tenants are tracked.
//        // We should not fail just because of an exception at startup as this could run before
//        // migrations have completed.
//        log.warn "Unable to fetch jobs for schema ${tenant_schema_id}."
//        // add to a deferred loading list.
//        deferredTenants << tenant_schema_id
//      }
//    }
//    enqueueJobMap(queue_order)
//  }
  
//  public void enqueueJob(final String jobId, final String tenantId) {
//    
//    final long WAIT_MAX = 3000 // 3 seconds should be enough.
//    final long WAIT_INCREMENT = 200
//    
//    log.info "Enqueueing job ${jobId} for ${tenantId}"
//    // Use me within nested closures to ensure we are talking about this service.
//    final def me = this
//    
//    Tenants.withId(tenantId) {
//      /**
//       * While this is UGLY. We can should retry here as this method should only be called once the event has been committed.
//       * As with everything though limit the amount of wait time and then rasie an error.
//       */
//      long totalTime = 0
//      PersistentJob job = PersistentJob.read(jobId)
//      while (job == null) {
//        Thread.sleep(WAIT_INCREMENT) // Recheck every 200 millis.
//        totalTime += WAIT_INCREMENT
//        job = PersistentJob.read(jobId)
//        if (totalTime > WAIT_MAX) throw new IllegalStateException("Failed to read job ${jobId} for ${tenantId} after ${WAIT_MAX} milliseconds")
//      }
//      Runnable work = job.getWork()
//      if (Closure.isAssignableFrom(work.class)) {
//        // Change the delegate to this class so we can control access to beans.
//        Closure workC = work as Closure
//        workC.setDelegate(me)
//        workC.setResolveStrategy(Closure.DELEGATE_FIRST)
//        
//        // Also pass in the current tenant id.
//        work = workC.curry(tenantId)
//      }
//      
//      // We should wrap the work in a closure so we can ensure tenant id is set
//      // as well as setting the job status on execution
//      final Runnable currentWork = work
//      work = { final String tid, final String jid, final Runnable wrk ->
//          final String tenantName = OkapiTenantResolver.schemaNameToTenantId(tid)
//          Tenants.withId(tid) {
//            try {
//              log.debug("Starting job execution");
//              org.slf4j.MDC.clear()
//              org.slf4j.MDC.setContextMap( jobId: '' + jid, tenantId: '' + tid, 'tenant': '' + tenantName)
//              JobContext.current.set(new JobContext( jobId: jid, tenantId: tid ))
//              beginJob(jid)
//              wrk()
//              log.debug("Cleanly terminating job execution");
//              endJob(jid)
//            } catch (Exception e) {
//              failJob(jid)
//              log.error (e.message)
//              log.error ("Job execution failed", e)
//              notify ('jobs:log_info', JobContext.current.get().tenantId, JobContext.current.get().jobId,  "Job execution failed: ${e.message}")
//            } finally {
//              JobContext.current.remove()
//              org.slf4j.MDC.clear()
//              jobEnded(tid, jid)
//            }
//          }
//      }.curry(tenantId, jobId, currentWork)
//      
//      // Execute the work asynchronously.
//      enqueueTenantJob(tenantId, work)
//    }
//  }
  
//  private void enqueueTenantJob (final String tid, final Runnable work) {
//    // Always add to the holding area.
//    holdingArea.add([ tid, work ])
//    
//    // And immediately executeNext()
//    executeNext()
//  }
  
//  private CopyOnWriteArrayList<List> holdingArea = new CopyOnWriteArrayList<List>()
//  private synchronized void executeNext() {
//    for (int i=0; i<holdingArea.size(); i++) {
//      List tuple = holdingArea[i]
//      // Tenant id in index 0
//      final String tenantId = tuple[0]
//      if (canAddTenantJob(tenantId)) {
//        
//        log.debug "Can add job to global queue."
//        
//        try {
//          // We can queue it. Index 1 is the work.
//          executorSvc.execute(tuple[1] as Runnable)
//        
//          // Increment the count
//          tenantCounts.get(tenantId).incrementAndGet()
//          
//          // Remove this element, and reset the counter, so as to always add in order.
//          holdingArea.remove(i)
//          
//          // Not zero as we are inside the loop and this value will be incremented.
//          i = -1
//          
//        } catch (RejectedExecutionException e) {
//          // The global queue is full.
//          log.warn("Executor couldn't accept the work.", e)
//        }
//      } else {
//        log.debug "Max jobs for tenant ${tenantId} queued, keep in holding area."
//      }
//    }
//  }
  
//  private boolean canAddTenantJob( final String tenantId ) {
//    // Counts.
//    AtomicInteger val = tenantCounts[tenantId]
//    if (val == null) {
//      val = new AtomicInteger(0)
//      tenantCounts[tenantId] = val
//    }
//    int count = val.get()
//    log.debug "Currently ${count} tasks queued for tenant ${tenantId}"
//    
//    count < CONCURRENT_JOBS_TENANT
//  }
  
//  private ConcurrentHashMap<String,AtomicInteger> tenantCounts = new ConcurrentHashMap<String,AtomicInteger>()
  private void jobEnded(final String tid, final String jid) {
    
    log.debug "Finished task with jobId ${jid} and tenantId ${tid}"
    
    // Counts.
//    int count = tenantCounts.get(tid).decrementAndGet()
//    log.debug "Tasks queued for tenant ${tid} adjusted to ${count}"
    
    // Execute next
//    executeNext()
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void beginJob(final String jid = null) {
    PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
    final String statusCat = pj.getStatusCategory()
    
    pj.started = Instant.now()
    pj.status = RefdataValue.lookupOrCreate(statusCat, 'In progress')
    pj.save(failOnError: true, flush:true)
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void endJob(final String jid = null) {
    PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
    final String statusCat = pj.getStatusCategory()
    
    pj.ended = Instant.now()
    pj.status = RefdataValue.lookupOrCreate(statusCat, 'Ended')
    
    if (pj.result == null) {
      final String resultCat = pj.getResultCategory()
      
      // If errors then set to partial.
      pj.result = RefdataValue.lookupOrCreate(resultCat, (pj.getErrorLog() ? 'Partial success' : 'Success'))
//      if (getErrorLog()) {
//        this.resultFromString = 'Partial success'
//      } else {
//        this.resultFromString = 'Success'
//      }
    }
    pj.save( failOnError: true, flush:true )
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void failJob(final String jid = null) {
    PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
    final String resultCat = pj.getResultCategory()
    
    // If errors then set to partial.
    pj.result = RefdataValue.lookupOrCreate(resultCat, 'Failure')
    final String statusCat = pj.getStatusCategory()
    
    pj.ended = Instant.now()
    pj.status = RefdataValue.lookupOrCreate(statusCat, 'Ended')
    pj.save( failOnError: true, flush:true )
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void interruptJob(final String tenantId, final String jid) {
    
    Tenants.withId(tenantId) {
    
      PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
      final String resultCat = pj.getResultCategory()
      
      // If errors then set to partial.
      pj.result = RefdataValue.lookupOrCreate(resultCat, 'Interrupted')
      final String statusCat = pj.getStatusCategory()
      
      pj.ended = Instant.now()
      pj.status = RefdataValue.lookupOrCreate(statusCat, 'Ended')
      pj.save( failOnError: true, flush:true )
    }
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void allocateJob( final String tenantId, final String jid) {
    
    Tenants.withId(tenantId) {
      PersistentJob pj = PersistentJob.get( jid )
      final String _myId = appFederationService.getInstanceId()
      pj.runnerId = _myId
      
      // Set the runner id.
      pj.save(flush: true, failOnError:true)
    }
  }

  public void shutdown() {
    log.info("JobRunnerService::shutdown()");
  }
  
  @Transactional
  protected Collection<String> getViableRunners() {
    appFederationService.allHealthyInstanceIds()
  }

  protected void cleanupAfterDeadRunners() {
    Collection<String> viableRunnerIds = getViableRunners()
    
    // Find all jobs in all registered tenants where the runner was the
    // instance being cleaned up and the status was in progress or queued
    // set progress to interrupted and reschedule job
    log.debug("JobRunnerService::cleanupAfterDeadRunner")
    
    okapiTenantAdminService.allConfiguredTenantSchemaNames().each { final String tenant_schema_id ->
      Tenants.withId(tenant_schema_id) {
        // Find all none edned jobs with the dead runner assigned
        final RefdataValue ended = PersistentJob.lookupStatus('Ended')

        // Find every job that was allocated to the runner.
        for (PersistentJob job : PersistentJob.findAllByStatusNotAndRunnerIdNotInList( ended, viableRunnerIds, [ sort: 'dateCreated' ])) {
          log.debug "Found job ${job.id} that was allocated to a runner that has died"
          if (job.status.value == 'queued') {
          
            log.debug "Job ${job.id} was only queued, clear the allocation"
            
            // If this job was queued, then we can just clear the runner ID
            job.runnerId = null
            job.save(flush:true, failOnError: true)
            
          } else {
            // This job was interrupted.
            log.debug "Setting job status to interrupted for ${job.id}"
            interruptJob( tenant_schema_id, job.id )
          }
        }
        
      }
    }
  }
  
  
  
  @Subscriber('federation:tick:leader')
  void leaderTick(final String instanceId) {
    log.debug("JobRunnerService::leaderTick")
    
    cleanupAfterDeadRunners()
    
    findAndRunNextJob()
  }
  
  @Subscriber('federation:tick:drone')
  void droneTick(final String instanceId) {
    log.debug("JobRunnerService::droneTick")
    findAndRunNextJob()
  }
  
  FolioLockService folioLockService
  AppFederationService appFederationService
  
  private final Map<Instant, String[]> potentialJobs = new TreeMap<>()
  
  private synchronized void findAndRunNextJob() {
    log.debug("JobRunnerService::findAndRunNextJob")
    
    // Check if the executor has space.
    final int activeCount = executorSvc.getActiveCount()
    if ((CONCURRENT_JOBS_GLOBAL - activeCount) < 1) {
      log.debug "No free task runners, active workers currently at ${activeCount}. Skipping"
      return
    }
    
    folioLockService.federatedLockAndDo("agreements:job:queue") {
      final int queueSize = potentialJobs.size()
      if (queueSize < 1) {
        log.debug "No jobs in potential jobs queue, find all queued jobs"
          
        okapiTenantAdminService.allConfiguredTenantSchemaNames().each { final String tenant_schema_id ->
          
          log.debug "Finding next jobs for tenant ${tenant_schema_id}"
          Tenants.withId(tenant_schema_id) {
            // Find a queued job with no runner assigned.
            final RefdataValue queued = PersistentJob.lookupStatus('Queued')
            PersistentJob.findAllByStatusAndRunnerIdIsNull(queued, [ sort: 'dateCreated', order: 'asc', max: 3] ).each { PersistentJob j ->
              potentialJobs.put(j.dateCreated, [j.id, tenant_schema_id] as String[])
            }
          }
        }
      }
      
      log.debug "Potential jobs queue size currently ${queueSize}"
      
      // Go through each one and check if each job is runnable.
      // Runnnable is no other tenant job with a RUNNING status
      final List<Instant> jobStamps = potentialJobs.keySet() as List
      
      final Set<String> busyTenants = []
      boolean added = false
      for (int i=0; !added && i<jobStamps.size(); i++) {
        final Instant key = jobStamps.get(i)
        final String[] jobAndTenant = potentialJobs.get( key )
        final String tenantId = jobAndTenant[1]
        if (busyTenants.contains(tenantId)) {
          log.debug "Already determined tenant ${tenantId} was busy. Try next job entry"
        } else {
        
          Tenants.withId(tenantId) {
            // Find a queued job with no runner assigned.
            final RefdataValue inProgress = PersistentJob.lookupStatus('In progress')
            
            final boolean tenantRunningJob = PersistentJob.findByStatus(inProgress)
            
            if (tenantRunningJob) {
              log.debug "Tenant ${tenantId} has at least one job in progess. Next job"
              busyTenants << tenantId
              
            } else {
              // Tenant can have Job run
              final String jobId = jobAndTenant[0]
              PersistentJob job = PersistentJob.read(jobId)
              
              final RefdataValue queued = PersistentJob.lookupStatus('Queued')
              if (job.status.id != queued.id || job.runnerId != null) {
                log.debug "Job ${jobId} is either in no longer queued or already allocated to a different runner"
                
                // Remove from the queue.
                potentialJobs.remove( key )
                
              } else {
                
                allocateJob(tenantId, job.id)
                executeJob(tenantId, job.id, key)
              }
            }
          }
        }
      }
    }
    log.debug("exiting JobRunnerService::findAndRunNextJob")
  }
  
  @Transactional(propagation=REQUIRES_NEW, readOnly=true)
  private Runnable getJobWork(final String tenantId, final String jobId) {
    Tenants.withId(tenantId) { PersistentJob.read( jobId )?.getWork() }
  }
  
  @Transactional(propagation=REQUIRES_NEW, readOnly=true)
  private boolean executeJob ( final String tenantId, final String jobId, final Instant key) {
    
    boolean added = false
    
    // Read the work.
    Runnable work = getJobWork(tenantId, jobId)
    
    if (work == null) {
      log.error("Couldn't fetch workload for job ${jobId}")
    }
    
    if (Closure.isAssignableFrom(work.class)) {
      // Change the delegate to this class so we can control access to beans.
      Closure workC = work as Closure
//      final JobRunnerService me = this
      workC.setDelegate(this)
      workC.setResolveStrategy(Closure.DELEGATE_FIRST)

      // Also pass in the current tenant id.
      work = workC.curry(tenantId)
    }

    // We should wrap the work in a closure so we can ensure tenant id is set
    // as well as setting the job status on execution
    final Runnable currentWork = work
    work = { final String tid, final String jid, final Runnable wrk ->
      final String tenantName = OkapiTenantResolver.schemaNameToTenantId(tid)
      try {
        log.debug("Starting job execution");
        org.slf4j.MDC.clear()
        org.slf4j.MDC.setContextMap( jobId: '' + jid, tenantId: '' + tid, 'tenant': '' + tenantName)
        JobContext.current.set(new JobContext( jobId: jid, tenantId: tid ))

        Tenants.withId(tid) {
          beginJob(jid)
        }
        Tenants.withId(tid) {
          wrk.run()
          log.debug("Cleanly terminating job execution")
        }
        Tenants.withId(tid) {
          endJob(jid)
        }
      } catch (Exception e) {

        Tenants.withId(tid) {
          failJob(jid)
        }
        log.error (e.message)
        log.error ("Job execution failed", e)
        notify ('jobs:log_info', JobContext.current.get().tenantId, JobContext.current.get().jobId,  "Job execution failed: ${e.message}")
      } finally {
        JobContext.current.remove()
        org.slf4j.MDC.clear()
        jobEnded(tid, jid)
      }
    }.curry(tenantId, jobId, currentWork)

    try {
      // Execute.
      executorSvc.execute(work)

      // Remove from the queue.
      potentialJobs.remove( key )
      added = true
    } catch (RejectedExecutionException e) {
      // The global queue is full.
      log.warn("Executor couldn't accept the work.", e)
    }
    
    added
  }
  
//  @Subscriber('jobs:job_created')
//  void handleNewJob(final String jobId, final String tenantId) {
//    // Attempt to append to queue.
//    log.info "onJobCreated(${jobId}, ${tenantId})"
//    enqueueJob(jobId, tenantId)
//  }
  
//  @Subscriber('okapi:tenant_enabled')
//  public void onTenantEnabled(final String tenantId) {
//    log.debug "New tenant (re)registered"
//    
//    // initialize the tenants jobs if these were in the deferred list
//    final String tenantSchema = OkapiTenantResolver.getTenantSchemaName(tenantId)
//    if (deferredTenants.contains(tenantSchema)) {
//      // load...
//      enqueueJobMap (initializeTenantJobs(tenantSchema))
//    }
//  }
}
