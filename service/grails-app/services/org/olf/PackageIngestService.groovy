package org.olf

import java.util.concurrent.TimeUnit

import org.olf.general.StringUtils

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.kb.Embargo
import org.olf.kb.PackageContentItem
import org.olf.kb.AlternateResourceName
import org.olf.kb.ContentType
import org.olf.kb.AvailabilityConstraint
import org.olf.kb.PackageDescriptionUrl
import org.olf.kb.Pkg
import org.olf.kb.Platform
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.RemoteKB
import org.olf.kb.TitleInstance
import org.olf.kb.MatchKey
import org.slf4j.MDC

import grails.util.GrailsNameUtils
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
class PackageIngestService implements DataBinder {

  // This boolean controls the behaviour of the loader when we encounter a title that does not have
  // a platform URL. We can error the row and do nothing, or create a row and point it at a proxy
  // platform to flag the error. Currently trialling the latter case. set to false to error and ignore the
  // row.
  private boolean PROXY_MISSING_PLATFORM = true

  TitleIngestService titleIngestService
  IdentifierService identifierService
  CoverageService coverageService
  MatchKeyService matchKeyService

  // dependentModuleProxyService is a service which hides the fact that we might be dependent upon other
  // services for our reference data. In this class - vendors are erm Org entries, but in folio these are
  // managed by the vendors app. If we are running in folio mode, this service hides the detail of
  // looking up an Org in vendors and stashing the vendor info in the local cache table.
  DependentModuleProxyService dependentModuleProxyService

  public Map upsertPackage(PackageSchema package_data) {
    return upsertPackage(package_data,'LOCAL',true)
  }


  // For now this is repeated in pushKBService
  private static final def countChanges = ['accessStart', 'accessEnd']

  /**
   * Load the paackage data (Given in the agreed canonical json package format) into the KB.
   * This function must be passed VALID package data. At this point, all package contents are
   * assumed to be valid. Any invalid rows should be filtered out at this point.
   * This is to keep the implementation of this function clean, all error checking shoud be
   * performed prior to this step. This function is soley concerned with absorbing a valid
   * package into the KB.
   * @return id of package upserted
   */
  public Map upsertPackage(PackageSchema package_data, String remotekbname, boolean readOnly=false) {
    Pkg pkg = null;
    // Remove MDC title at top of upsert package
    MDC.remove('title')
    def result = [
      startTime: System.currentTimeMillis(),
      titleCount: 0,
      newTitles: 0,
      removedTitles: 0,
      updatedTitles: 0,
      updatedAccessStart: 0,
      updatedAccessEnd: 0,
    ]

    Boolean trustedSourceTI = package_data.header?.trustedSourceTI

    // ERM caches many remote KB sources in it's local package inventory
    // Look up which remote kb via the name
    RemoteKB kb = RemoteKB.findByName(remotekbname)
    Pkg.withNewTransaction { status ->

      if (!kb) {
       kb = new RemoteKB( name:remotekbname,
                          rectype: RemoteKB.RECTYPE_PACKAGE,
                          active: Boolean.TRUE,
                          readonly:readOnly,
                          trustedSourceTI:false).save(flush:true, failOnError:true)
      }

      if (trustedSourceTI == null) {
        // If we're not explicitly handed trusted information, default to whatever the remote KB setting is
        trustedSourceTI = kb.trustedSourceTI

        if (trustedSourceTI == null) {
          // If it somehow remains unset, default to false, but with warning
          log.warn("Could not find trustedSourceTI setting for KB, defaulting to false")
          trustedSourceTI = false
        }
      }

      result.updateTime = System.currentTimeMillis()

      log.info("Package header: ${package_data.header} - update start time is ${result.updateTime}")
      
      // Farm out package lookup and creation to a separate method
      pkg = lookupOrCreatePkg(package_data);
      // Retain logging information
      MDC.put('packageSource', pkg.source.toString())
      MDC.put('packageReference', pkg.reference.toString())

      // Update identifiers from citation
      identifierService.updatePackageIdentifiers(pkg, package_data.identifiers)

      result.packageId = pkg.id
    }

    package_data.packageContents.eachWithIndex { ContentItemSchema pc, int index ->
      // ENSURE MDC title is set as early as possible
      MDC.put('title', StringUtils.truncate(pc.title.toString()))

      // log.debug("Try to resolve ${pc}")

      try {
        PackageContentItem.withNewTransaction { status ->
          // Delegate out to TitleIngestService so that any shared steps can move there.
          Map titleIngestResult = titleIngestService.upsertTitle(pc, kb, trustedSourceTI)

          // titleIngestResult.titleInstanceId will be non-null IFF TitleIngestService managed to find a title with that Id.
          if ( titleIngestResult.titleInstanceId != null ) {
            TitleInstance title = TitleInstance.get(titleIngestResult.titleInstanceId)

            // Pass off to new hierarchy method (?)
            // FIXME some steps of the MDC and stuff still feel like packageUpsert territory, not needed for pushKB?
            Map hierarchyResult = lookupOrCreateTitleHierarchy(
              title,
              pkg,
              trustedSourceTI,
              pc,
              result.updateTime,
              result.titleCount
            )

            PackageContentItem pci = PackageContentItem.get(hierarchyResult.pciId)
    
            // Handle MDC stuffs
            // TODO perhaps break some of this out so pushKB can use the same code but in a different way
            switch (hierarchyResult.pciStatus) {
              case 'updated':
                // This means we have changes to an existing PCI and not a new one.
                result.updatedTitles++

                // Grab the dirty properties
                def modifiedFieldNames = pci.getDirtyPropertyNames()
                for (fieldName in modifiedFieldNames) {
                  if (fieldName == "accessStart") {
                    result.updatedAccessStart++
                  }
                  if (fieldName == "accessEnd") {
                    result.updatedAccessEnd++
                  }
                  if (countChanges.contains(fieldName)) {
                    def currentValue = pci."$fieldName"
                    def originalValue = pci.getPersistentValue(fieldName)
                    if (currentValue != originalValue) {
                      result["${fieldName}"] = (result["${fieldName}"] ?: 0)++
                    }
                  }
                }
                break;
              case 'new':
                // New item.
                result.newTitles++
                break;
              case 'none':
              default:
                break;
            }
          }
          else {
            String message = "Skipping \"${pc.title}\". Unable to resolve title from ${pc.title} with identifiers ${pc.instanceIdentifiers}"
            log.error(message)
          }
        }
      } catch ( Exception e ) {
        String message = "Skipping \"${pc.title}\". System error: ${e.message}"
        log.error(message,e)
      }
      result.titleCount++
      result.averageTimePerTitle=(System.currentTimeMillis()-result.startTime)/result.titleCount
      if ( result.titleCount % 100 == 0 ) {
        log.debug ("Processed ${result.titleCount} titles, average per title: ${result.averageTimePerTitle}")
      }
    }
    def finishedTime = (System.currentTimeMillis()-result.startTime)/1000

    // FIXME this removed logic is WRONG under pushKB because it's chunked
    // At the end - Any PCIs that are currently live (Don't have a removedTimestamp) but whos lastSeenTimestamp is < result.updateTime
    // were not found on this run, and have been removed. We *may* introduce some extra checks here - like 3 times or a time delay, but for now,
    // this is how we detect deletions in the package file.
    log.debug("Remove any content items that have disappeared since the last upload. ${pkg.name}/${pkg.source}/${pkg.reference}/${result.updateTime}")
    int removal_counter = 0

    PackageContentItem.withNewTransaction { status ->
      // FIXME we're querying on pkg itself here
      PackageContentItem.executeQuery('select pci from PackageContentItem as pci where pci.pkg = :pkg and pci.lastSeenTimestamp < :updateTime and pci.removedTimestamp is null',
                                      [pkg:pkg, updateTime:result.updateTime]).each { removal_candidate ->
        try {
          log.debug("Removal candidate: pci.id #${removal_candidate.id} (Last seen ${removal_candidate.lastSeenTimestamp}, thisUpdate ${result.updateTime}) -- Set removed")
          removal_candidate.removedTimestamp = result.updateTime
          removal_candidate.save(flush:true, failOnError:true)
        } catch ( Exception e ) {
          log.error("Problem removing ${removal_candidate} in package load",e)
        }
        result.removedTitles++
      }
    }

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

//    MDC.clear()

    return result
  }

  /* 
   * Separate out the create or lookup pkg code, so that it can 
   * be used both by the ingest service (via upsert pkg), as well
   * as the pushKBService (directly)
   *
   * This method ALSO provides update information for packages.
   */
  public Pkg lookupOrCreatePkg(PackageSchema package_data) {
    Pkg pkg = null

    // header.packageSlug contains the package maintainers authoritative identifier for this package.
    pkg = Pkg.findBySourceAndReference(package_data.header.packageSource, package_data.header.packageSlug)
    if (pkg == null) {
      // at this point we check alternate slugs
      for ( alternateSlug in package_data.header.alternateSlugs ) {
        pkg = Pkg.findBySourceAndReference(package_data.header.packageSource, alternateSlug);
        if ( pkg != null ) {
          pkg.reference = package_data.header.packageSlug;
          break;
        }
      }
    }

    def vendor = null
    if ( ( package_data.header?.packageProvider?.name != null ) && ( package_data.header?.packageProvider?.name.trim().length() > 0 ) ) {
      log.debug("Package contains provider information: ${package_data.header?.packageProvider?.name} -- trying to match to an existing organisation.")
      vendor = dependentModuleProxyService.coordinateOrg(package_data.header?.packageProvider?.name)
      // reference has been removed at the request of the UI team
      // vendor.enrich(['reference':package_data.header?.packageProvider?.reference])
    }
    else {
      log.warn('Package ingest - no provider information present')
    }

    if ( pkg == null ) {
      pkg = new Pkg(
                      name: package_data.header.packageName,
                    source: package_data.header.packageSource,
                reference: package_data.header.packageSlug,
              description: package_data.header.description,
        sourceDataCreated: package_data.header.sourceDataCreated,
        sourceDataUpdated: package_data.header.sourceDataUpdated,
        availabilityScope: ( package_data.header.availabilityScope != null ? Pkg.lookupOrCreateAvailabilityScope(package_data.header.availabilityScope) : null ),
          lifecycleStatus: (package_data.header.lifecycleStatus != null ? Pkg.lookupOrCreateLifecycleStatus(package_data.header.lifecycleStatus) : Pkg.lookupOrCreateLifecycleStatus('Unknown')), // FIXME this is def not concise enough
                    vendor: vendor,
      ).save(flush:true, failOnError:true)

      (package_data?.header?.contentTypes ?: []).each {
        pkg.addToContentTypes(new ContentType([contentType: ContentType.lookupOrCreateContentType(it.contentType)]))
      }

      (package_data?.header?.alternateResourceNames ?: []).each {
        pkg.addToAlternateResourceNames(new AlternateResourceName([name: it.name]))
      }

      (package_data?.header?.availabilityConstraints ?: []).each {
        pkg.addToAvailabilityConstraints(new AvailabilityConstraint([body: AvailabilityConstraint.lookupOrCreateBody(it.body)]))
      }

      (package_data?.header?.packageDescriptionUrls ?: []).each {
        pkg.addToPackageDescriptionUrls(new PackageDescriptionUrl([url: it.url]))
      }

      pkg.save(failOnError: true)
    } else {
      pkg.sourceDataUpdated = package_data.header.sourceDataUpdated

      if (package_data.header.lifecycleStatus) {
        pkg.lifecycleStatusFromString = package_data.header.lifecycleStatus
      }

      if (package_data.header.availabilityScope) {
        pkg.availabilityScopeFromString = package_data.header.availabilityScope
      }

      pkg.vendor = vendor
      pkg.description = package_data.header.description
      pkg.name = package_data.header.packageName
      pkg.save(failOnError:true)

      // Call separate methods for updating collections for code cleanliness
      // These methods are responsible for their own saves
      updateContentTypes(pkg.id, package_data)
      updateAlternateNames(pkg.id, package_data)
      updateAvailabilityConstraints(pkg.id, package_data)
      updatePackageDescriptionUrls(pkg.id, package_data)
    }

    return pkg;
  }

  def updateContentTypes (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);
    def contentTypes = package_data?.header?.contentTypes ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def contentTypesToRemove = [];

    pkg.contentTypes.each {
      if (!contentTypes.contains(it.contentType.label)) {
        contentTypesToRemove << it
      }
    }

    contentTypesToRemove.each {
      pkg.removeFromContentTypes(it)
    }

    contentTypes.each {def ct ->
      if(!pkg.contentTypes?.collect {def pct -> pct.contentType.label }.contains(ct.contentType)) {
        pkg.addToContentTypes(new ContentType([contentType: ContentType.lookupOrCreateContentType(ct.contentType)]))
      }
    }

    pkg.save(failOnError: true)
  }

  /*  ---- Individual update package methods ---- */
  def updateAlternateNames (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);

    def resourceNames = package_data?.header?.alternateResourceNames ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def alternateNamesToRemove = [];

    pkg.alternateResourceNames.each {
      if (!resourceNames.contains(it.name)) {
        def arn_tbd = AlternateResourceName.findByName(it.name)
        alternateNamesToRemove << arn_tbd
      }
    }

    alternateNamesToRemove.each {
      pkg.removeFromAlternateResourceNames(it)
    }

    resourceNames.each {def arn ->
      if(!pkg.alternateResourceNames?.collect {def parn -> parn.name }.contains(arn.name)) {
        pkg.addToAlternateResourceNames(new AlternateResourceName([name: arn.name]))
      }
    }

    pkg.save(failOnError: true)
  }

  def updateAvailabilityConstraints (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);
    def availabilityConstraints = package_data?.header?.availabilityConstraints ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def availabilityConstraintsToRemove = [];

    pkg.availabilityConstraints.each {
      if (!availabilityConstraints.contains(it.body.label)) {
        availabilityConstraintsToRemove << it
      }
    }

    availabilityConstraintsToRemove.each {
      pkg.removeFromAvailabilityConstraints(it)
    }

    availabilityConstraints.each {def ac ->
      if(!pkg.availabilityConstraints?.collect {def pac -> pac.body.label }.contains(ac.body)) {
        pkg.addToAvailabilityConstraints(new AvailabilityConstraint([body: AvailabilityConstraint.lookupOrCreateBody(ac.body)]))
      }
    }

    pkg.save(failOnError: true)
  }

  def updatePackageDescriptionUrls (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);
    def urls = package_data?.header?.packageDescriptionUrls ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def urlsToRemove = [];

    pkg.packageDescriptionUrls.each {
      if (!urls.contains(it.url)) {
        urlsToRemove << it
      }
    }

    urlsToRemove.each {
      pkg.removeFromPackageDescriptionUrls(it)
    }

    urls.each {def url ->
      if(!pkg.packageDescriptionUrls?.collect {def pdu -> pdu.url }.contains(url.url)) {
        pkg.addToPackageDescriptionUrls(new PackageDescriptionUrl([url: url.url]))
      }
    }

    pkg.save(failOnError: true)
  }

  /* Lookup or create a package from contentItemPackage within the passed contentItem */
  public Pkg lookupOrCreatePackageFromTitle(ContentItemSchema pc) {
    Pkg pkg = null;
    if (pc?.contentItemPackage) {
      pkg = lookupOrCreatePkg(pc.contentItemPackage)
    } else {
      /* FIXME WIP this feels like not the right thing to do */
      throw new Exception("Idk what do do in this situation, throw for now")
    }

    return pkg
  }

  public Platform lookupOrCreatePlatform(ContentItemSchema pc) {
    // lets try and work out the platform for the item
    def platform_url_to_use = pc.platformUrl

    if ( ( pc.platformUrl == null ) && ( pc.url != null ) ) {
      // No platform URL, but a URL for the title. Parse the URL and generate a platform URL
      def parsed_url = new java.net.URL(pc.url)
      platform_url_to_use = "${parsed_url.getProtocol()}://${parsed_url.getHost()}"
    }

    Platform platform = Platform.resolve(platform_url_to_use, pc.platformName)
    // log.debug("Platform: ${platform}")

    if ( platform == null && PROXY_MISSING_PLATFORM ) {
      platform = Platform.resolve('http://localhost.localdomain', 'This platform entry is used for error cases')
    }

    platform
  }

  public PlatformTitleInstance lookupOrCreatePTI(TitleInstance title, Platform platform, Boolean trustedSourceTI, ContentItemSchema pc) {
    PlatformTitleInstance pti = PlatformTitleInstance.findByTitleInstanceAndPlatform(title, platform)
    if ( pti == null ) {
      pti = new PlatformTitleInstance(titleInstance:title,
        platform:platform,
        url:pc.url,
      ).save(failOnError: true)
    } else if (trustedSourceTI) {
      // Update any PTI fields directly
      if (pti.url != pc.url) {
        pti.url = pc.url
      }
      pti.save(flush: true, failOnError: true)
    }

    pti
  }


  // Once package/title has been created, setup rest of hierarchy PTI/PCI etc PCI etc
  // Return special map containing data on whether PCI was created or updated,
  public Map lookupOrCreateTitleHierarchy(
    TitleInstance title,
    Pkg pkg,
    Boolean trustedSourceTI,
    ContentItemSchema pc,
    long updateTime,
    long titleCount
  ) {
    Map result = [
      pciStatus: 'none' // This should be 'none', 'updated' or 'new'
    ]

    // ERM-1799 TI has been created, harvest matchKey information at this point to apply to any PTI/PCIs
    List<Map> matchKeys = matchKeyService.collectMatchKeyInformation(pc)

    // log.debug("platform ${pc.platformUrl} ${pc.platformName} (item URL is ${pc.url})")

    // lets try and work out the platform for the item
    Platform platform = lookupOrCreatePlatform(pc);

    if ( platform != null ) {

      // See if we already have a title platform record for the presence of this title on this platform
      PlatformTitleInstance pti = lookupOrCreatePTI(title, platform, trustedSourceTI, pc)
      matchKeyService.updateMatchKeys(pti, matchKeys)


      // ADD PTI AND PCI ID TO RESULT
      result.ptiId = pti.id;

      // Lookup or create a package content item record for this title on this platform in this package
      // We only check for currently live pci records, as titles can come and go from the package.
      // N.B. addedTimestamp removedTimestamp lastSeenTimestamp
      def pci_qr = PackageContentItem.executeQuery('select pci from PackageContentItem as pci where pci.pti = :pti and pci.pkg.id = :pkg and pci.removedTimestamp is null',
          [pti:pti, pkg:pkg.id])
      PackageContentItem pci = pci_qr.size() == 1 ? pci_qr.get(0) : null;

      boolean isUpdate = false
      boolean isNew = false
      if ( pci == null ) {
        log.debug("Record ${titleCount} - Create new package content item")
        MDC.put('recordNumber', (titleCount+1).toString())
        pci = new PackageContentItem(
          pti:pti,
          pkg:pkg,
          addedTimestamp:updateTime,
        )

        // ERM-1799, match keys need adding to PCI
        matchKeyService.updateMatchKeys(pci, matchKeys, false)
        isNew = true
      }
      else {
        // Note that we have seen the package content item now - so we don't delete it at the end.
        log.debug("Record ${titleCount} - Update package content item (${pci.id})")
        isUpdate = true
        if (trustedSourceTI) {
          /*
          * We may need to update the match key information
          * from the incoming package for existing PCIs
          */
          matchKeyService.updateMatchKeys(pci, matchKeys, false)
        }
      }

      String embStr = pc.embargo?.trim()

      // Pre attempt to parse. And log error.
      Embargo emb = null
      if (embStr) {
        emb = Embargo.parse(embStr)
        if (!emb) {
          log.error "Could not parse ${embStr} as Embargo"
        }
      }

      // Add/Update common properties.
      pci.with {
        note = StringUtils.truncate(pc.coverageNote)
        depth = pc.coverageDepth
        accessStart = pc.accessStart
        accessEnd = pc.accessEnd
        addedTimestamp = updateTime
        lastSeenTimestamp = updateTime

        if (emb) {
          if (embargo) {
            // Edit
            bindData(embargo, emb.properties, [exclude: ['id']])
          } else {
            // New
            emb.save()
            embargo = emb
          }
        }
      }

      // ensure that accessStart is earlier than accessEnd, otherwise stop processing the current item
      if (pci.accessStart != null && pci.accessEnd != null) {
        if (pci.accessStart > pci.accessEnd ) {
          log.error("accessStart date cannot be after accessEnd date for title: ${title} in package: ${pkg.name}")
          return
        }
      }

      // Return a status containing information about whether the PCI was created or updated
      if (isUpdate && pci.isDirty()) {
        result.pciStatus = 'updated'
      } else if (isNew) {
        result.pciStatus = 'new'
      }

      pci.save(flush: true, failOnError: true)

      // ADD PTI AND PCI ID TO RESULT
      result.pciId = pci.id;

      // If the row has a coverage statement, check that the range of coverage we know about for this title on this platform
      // extends to include the supplied information. It is a contract with the KB that we assume this is correct info.
      // We store this generally for the title on the platform, and specifically for this title in this package on this platform.
      if ( pc.coverage ) {

        // We define coverage to be a list in the exchange format, but sometimes it comes just as a JSON map. Convert that
        // to the list of maps that coverageService.extend expects
        Iterable<CoverageStatementSchema> cov = pc.coverage instanceof Iterable ? pc.coverage : [ pc.coverage ]
        coverageService.setCoverageFromSchema (pci, cov)
      }
    }
    else {
      String message = "Skipping \"${pc.title}\". Unable to identify platform from ${platform_url_to_use} and ${pc.platformName}"
      log.error(message)
    }

    result
  }
}
