package org.olf

import com.k_int.okapi.OkapiTenantAdminService
import org.olf.general.jobs.JobRunnerService

import com.k_int.web.toolkit.files.S3FileObject;
import com.k_int.web.toolkit.files.LOBFileObject;

class BootStrap {

  def grailsApplication
  OkapiTenantAdminService okapiTenantAdminService
  JobRunnerService jobRunnerService

  def init = { servletContext ->

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        log.info("mod-agreements shutdown hook - process terminating");
      }
    });

    log.info("${grailsApplication.getMetadata().getApplicationName()}  (${grailsApplication.config?.info?.app?.version}) initialising");
    log.info("          build number -> ${grailsApplication.metadata['build.number']}");
    log.info("        build revision -> ${grailsApplication.metadata['build.git.revision']}");
    log.info("          build branch -> ${grailsApplication.metadata['build.git.branch']}");
    log.info("          build commit -> ${grailsApplication.metadata['build.git.commit']}");
    log.info("            build time -> ${grailsApplication.metadata['build.time']}");
    log.info("            build host -> ${grailsApplication.metadata['build.host']}");
    log.info("         Base JDBC URL -> ${grailsApplication.config.dataSource.url} / ${grailsApplication.config.dataSource.username}");

    jobRunnerService.populateJobQueue()
  }

  def destroy = {
  }
}
