package org.olf.general.pushKB

import org.olf.general.StringUtils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

import org.olf.dataimport.erm.CoverageStatement
import org.olf.dataimport.erm.ErmPackageImpl
import org.olf.dataimport.erm.ContentItem
import org.olf.dataimport.erm.Identifier
import org.olf.dataimport.erm.PackageProvider
import org.olf.dataimport.internal.HeaderImpl
import org.olf.dataimport.internal.InternalPackageImpl
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.KBManagementBean
import org.olf.dataimport.internal.KBManagementBean.KBIngressType

// Have moved to another package to help pull some of this work together, now need to import these beans
import org.olf.UtilityService
import org.olf.PackageIngestService
import org.olf.TitleIngestService

import org.slf4j.MDC

import org.olf.kb.RemoteKB
import org.olf.kb.Pkg

import com.opencsv.CSVReader

import grails.web.databinding.DataBinder
import static groovy.transform.TypeCheckingMode.SKIP
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.MDC


@Slf4j
class PushKBService implements DataBinder {
  UtilityService utilityService
  PackageIngestService packageIngestService
  TitleIngestService titleIngestService

  KBManagementBean kbManagementBean

  public Map pushPackages(final List<Map> packages) {
    Map result = [
      success: false
    ]
    KBIngressType ingressType = kbManagementBean.ingressType

    if (ingressType == KBIngressType.PushKB) {
      try {
        packages.each { Map record ->
          final PackageSchema pkg = InternalPackageImpl.newInstance();
          bindData(pkg, record)
          if (utilityService.checkValidBinding(pkg)) {

            // Start a transaction -- method in packageIngestService needs this
            Pkg.withNewTransaction { status ->
              packageIngestService.upsertPackage(pkg)
            }

          }
        }
        result.success = true
      } catch (Exception e) {
        log.error("Something went wrong", e);
        result.errorMessage = "Something went wrong: ${e}"
      }
    } else {
      result.errorMessage = "pushPackages not valid when kbManagementBean is configured with type (${ingressType})"
    }

    return result
  }

  public Map pushPCIs(final List<Map> pcis) {
    Map result = [
      success: false
    ]
    KBIngressType ingressType = kbManagementBean.ingressType

    if (ingressType == KBIngressType.PushKB) {
      try {
        pcis.each { Map record ->
          // Handle MDC directly? Might not be the right approach
          MDC.put('title', StringUtils.truncate(record.title.toString()))

          log.debug("LOGGING PCI: ${record}")
          final ContentItemSchema pci = PackageContentImpl.newInstance();
          bindData(pci, record)
          if (utilityService.checkValidBinding(pci)) {
            Pkg pkg = null;
            Pkg.withNewTransaction { status ->
              pkg = packageIngestService.lookupOrCreatePackageFromTitle(pci);
            }

            log.debug("LOGGING PACKAGE OBTAINED FROM PCI: ${pkg}")
          } else {
            // We could log an ending error message here, but the error log messages from checkValidBinding may well suffice
          }
        }
        result.success = true
      } catch (Exception e) {
        log.error("Something went wrong", e);
        result.errorMessage = "Something went wrong: ${e}"
      }
    } else {
      result.errorMessage = "pushPCIs not valid when kbManagementBean is configured with type (${ingressType})"
    }

    return result
  }
}
