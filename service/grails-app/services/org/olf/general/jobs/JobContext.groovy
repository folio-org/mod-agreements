package org.olf.general.jobs

import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/* As of Poppy, this will ALSO be used for setting the pushKB context
 * so that logs reflect on those objects in the same way as on jobs
 * Keeping the naming/package scheme the same in case we want to lift
 * just the job part out later
 */
@CompileStatic
@Slf4j
final class JobContext {
  Serializable jobId
  Serializable tenantId = Tenants.CurrentTenant.get()
  
  public static ThreadLocal<JobContext> current = new InheritableThreadLocal<JobContext>() {
    @Override
    public void set(JobContext value) {
      // log.debug 'Setting threadlocal JobContext.current for thread {} to {}', Thread.currentThread().name, value
      super.set(value)
    }
    
    @Override
    public JobContext get() {
      JobContext value = super.get();
      // log.debug 'Getting threadlocal JobContext.current for thread {} returning {}', Thread.currentThread().name, value
      value
    }
  }
}
