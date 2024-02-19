package org.olf.general.pushKB

import org.olf.general.StringUtils

import java.util.concurrent.TimeUnit

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
import org.olf.IdentifierService

import org.slf4j.MDC

import org.olf.kb.RemoteKB
import org.olf.kb.Pkg
import org.olf.kb.PackageContentItem
import org.olf.kb.TitleInstance

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
  IdentifierService identifierService

  KBManagementBean kbManagementBean

  // For now this is repeated in packageIngestService
  private static final def countChanges = ['accessStart', 'accessEnd']

  public Map pushPackages(final List<Map> packages) {
    Map result = [
      success: false
    ]
    KBIngressType ingressType = kbManagementBean.ingressType

    if (ingressType == KBIngressType.PushKB) {
      try {
        packages.each { Map record ->
          final PackageSchema package_data = InternalPackageImpl.newInstance();
          bindData(package_data, record)
          if (utilityService.checkValidBinding(package_data)) {

            // Start a transaction -- method in packageIngestService needs this
            Pkg.withNewTransaction { status ->
              // Farm out package lookup and creation to a separate method

              // These calls mirror what's in upsertPackage but conveniently avoid the
              // logic which handles TIPPS
              Pkg pkg = packageIngestService.lookupOrCreatePkg(package_data);
                // Retain logging information
                MDC.put('packageSource', pkg.source.toString())
                MDC.put('packageReference', pkg.reference.toString())

              // Update identifiers from citation
              identifierService.updatePackageIdentifiers(pkg, package_data.identifiers)
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
      success: false,
      startTime: System.currentTimeMillis(),
      titleCount: 0,
      newTitles: 0,
      removedTitles: 0,
      updatedTitles: 0,
      updatedAccessStart: 0,
      updatedAccessEnd: 0,
    ]
    KBIngressType ingressType = kbManagementBean.ingressType

    if (ingressType == KBIngressType.PushKB) {
      try {
        pcis.each { Map record ->

          // Handle MDC directly? Might not be the right approach
          MDC.put('title', StringUtils.truncate(record.title.toString()))

          // Not entirely sure why we would need this and startTime... left for consistency with upsertPackage
          result.updateTime = System.currentTimeMillis()

          final ContentItemSchema pc = PackageContentImpl.newInstance();

          // Ensure electronic
          if (!pc.instanceMedium) {
            pc.instanceMedium = 'Electronic'
          }

          bindData(pc, record)
          if (utilityService.checkValidBinding(pc)) {
            try {
              Pkg pkg = null;
              Pkg.withNewTransaction { status ->
                // TODO this will allow the PCI data to update the PKG record... do we want this?
                pkg = packageIngestService.lookupOrCreatePackageFromTitle(pc);
                log.debug("LOGGING PACKAGE OBTAINED FROM PCI: ${pkg}")
                
                Map titleIngestResult = titleIngestService.upsertTitleDirect(pc)
                log.debug("LOGGING titleIngestResult: ${titleIngestResult}")

                if ( titleIngestResult.titleInstanceId != null ) {
                  Map hierarchyResult = packageIngestService.lookupOrCreateTitleHierarchy(
                    titleIngestResult.titleInstanceId,
                    pkg.id,
                    true,
                    pc,
                    result.updateTime,
                    result.titleCount // FIXME not sure about this
                  )

                  PackageContentItem pci = PackageContentItem.get(hierarchyResult.pciId)
                  packageIngestService.hierarchyResultMapLogic(hierarchyResult, result, pci)

                  /* TODO figure out if use of removedTimestamp
                   * should be something harvest also needs to do directly
                   * And whether we should be doing it after all the above
                   * or before.
                   */
                  if (pc.removedTimestamp) {
                      try {
                      log.debug("Removal candidate: pci.id #${pci.id} (Last seen ${pci.lastSeenTimestamp}, thisUpdate ${result.updateTime}) -- Set removed")
                      pci.removedTimestamp = pc.removedTimestamp
                      pci.save(failOnError:true)
                    } catch ( Exception e ) {
                      log.error("Problem removing ${pci} in package load", e)
                    }
                    result.removedTitles++
                  }
                } else {
                  String message = "Skipping \"${pc.title}\". Unable to resolve title from ${pc.title} with identifiers ${pc.instanceIdentifiers}"
                  log.error(message)
                }
              }
            }  catch ( Exception e ) {
              String message = "Skipping \"${pc.title}\". System error: ${e.message}"
              log.error(message,e)
            }

            result.titleCount++
            
          } else {
            // We could log an ending error message here, but the error log messages from checkValidBinding may well suffice
          }
        }

        // FIXME logging repeated here again
        result.averageTimePerTitle=(System.currentTimeMillis()-result.startTime)/result.titleCount
        if ( result.titleCount % 100 == 0 ) {
          log.debug ("Processed ${result.titleCount} titles, average per title: ${result.averageTimePerTitle}")
        }

        def finishedTime = (System.currentTimeMillis()-result.startTime)/1000
        result.success = true

        // FIXME same as above, logging may need tweaking between pushKB and harvest
        // Currently this is copied from packageIngestService
        MDC.remove('recordNumber')
        MDC.remove('title')
        // Need to pause long enough so that the timestamps are different
        TimeUnit.MILLISECONDS.sleep(1)
        if (result.titleCount > 0) {
          log.info ("Processed ${result.titleCount} titles in ${finishedTime} seconds (${finishedTime/result.titleCount} average)")
          TimeUnit.MILLISECONDS.sleep(1)
          log.info ("Added ${result.newTitles} titles")
          TimeUnit.MILLISECONDS.sleep(1)
          log.info ("Updated ${result.updatedTitles} titles")
          TimeUnit.MILLISECONDS.sleep(1)
          log.info ("Removed ${result.removedTitles} titles")
          log.info ("Updated accessStart on ${result.updatedAccessStart} title(s)")
          log.info ("Updated accessEnd on ${result.updatedAccessEnd} title(s)")

          // Log the counts too.
          for (final String change : countChanges) {
            if (result[change]) {
              TimeUnit.MILLISECONDS.sleep(1)
              log.info ("Changed ${GrailsNameUtils.getNaturalName(change).toLowerCase()} on ${result[change]} titles")
            }
          }
        } else {
          if (result.titleCount > 0) {
            log.info ("No titles to process")
          }
        }
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
