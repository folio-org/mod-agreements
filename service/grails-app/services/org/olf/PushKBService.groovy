package org.olf

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

import org.olf.dataimport.erm.CoverageStatement
import org.olf.dataimport.erm.ErmPackageImpl
import org.olf.dataimport.erm.Identifier
import org.olf.dataimport.erm.PackageProvider
import org.olf.dataimport.internal.HeaderImpl
import org.olf.dataimport.internal.InternalPackageImpl
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema
import org.slf4j.MDC

import org.olf.kb.RemoteKB
import org.olf.kb.Pkg

import com.opencsv.CSVReader

import grails.web.databinding.DataBinder
import static groovy.transform.TypeCheckingMode.SKIP
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class PushKBService implements DataBinder {
  UtilityService utilityService
  PackageIngestService packageIngestService
  TitleIngestService titleIngestService

  // Using the "find by name" means static compile fails
  @CompileStatic(SKIP)
  public boolean pushPackages(final List<Map> packages) {
    log.debug("LOGGING PACKAGES: ${packages}")
    
    packages.each { Map record ->
      log.debug("PACKAGE: ${record}")
      final PackageSchema pkg = ErmPackageImpl.newInstance();
      bindData(pkg, record)
      if (utilityService.checkValidBinding(pkg)) {
        log.debug("LOGGING PACKAGE BOUND: ${pkg}")

        // Start a transaction -- method in packageIngestService needs this
        Pkg.withNewTransaction { status ->
          packageIngestService.upsertPackage(pkg)
        }

      }
    }

    return true
  }
}
