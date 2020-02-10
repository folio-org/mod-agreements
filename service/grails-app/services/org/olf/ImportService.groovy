package org.olf

import grails.gorm.transactions.Transactional
import grails.web.databinding.DataBinder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.olf.dataimport.erm.ErmPackageImpl
import org.olf.dataimport.internal.InternalPackageImpl
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.erm.Identifier
import org.slf4j.MDC
import org.springframework.context.MessageSource
import org.springframework.validation.ObjectError
import org.springframework.context.i18n.LocaleContextHolder

import com.opencsv.CSVReader
import org.olf.dataimport.erm.CoverageStatement
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@CompileStatic
@Slf4j
class ImportService implements DataBinder {
  
  PackageIngestService packageIngestService
  
  MessageSource messageSource
  
  void importFromFile (final Map envelope) {
    
    final def header = envelope.header
    final def dataSchemaName = header?.getAt('dataSchema')?.getAt('name')
    if (dataSchemaName) {
      
      log.info "dataSchema specified"
      
      // we can use the dataSchema object to lookup the type.
      switch (dataSchemaName) {
        case 'mod-agreements-package':
          log.debug "ERM schema"          
          log.info "${importPackageUsingErmSchema (envelope)} package(s) imported successfully"
          break
          
          // Successfully
        default: 
          log.error "Unknown dataSchema ${dataSchemaName}, ignoring import."
      }
    } else {
      // No dataSchemaName. Examine the rest of the root properties
      if (header && envelope.packageContents) {
        // Looks like it might be the internal schema.
        
        log.debug "Possibly internal schema"
        importPackageUsingInternalSchema (envelope)
      }
    }
  }
  
  int importPackageUsingErmSchema (final Map envelope) {
    
    log.debug "Called importPackageUsingErmSchema with data ${envelope}"
    int packageCount = 0
    // Erm schema supports multiple packages per document. We should lazily parse 1 by 1.
    envelope.records?.each { Map record ->
      // Ingest 1 package at a time.
      
      MDC.put('rowNumber', "${packageCount + 1}")
      MDC.put('discriminator', "Package #${packageCount + 1}")
      if (importPackage (record, ErmPackageImpl)) {
        packageCount ++
      }
    }
    
    packageCount
  }
  
  int importPackageUsingInternalSchema (final Map envelope) {
    // The whole envelope is a single package in this format.
    
    MDC.put('rowNumber', "1")
    MDC.put('discriminator', "Package #1")
    importPackage (envelope, InternalPackageImpl) ? 1 : 0
  }
  
  private boolean importPackage (final Map record, final Class<? extends PackageSchema> schemaClass) {
    boolean packageImported = false
    final PackageSchema pkg = schemaClass.newInstance()
    bindData(pkg, record)
    // Check for binding errors.
    if (!pkg.errors.hasErrors()) {
      // Validate the actual values now. And check for constraint violations
      pkg.validate()
      if (!pkg.errors.hasErrors()) {
        // Ingest the package.
        packageIngestService.upsertPackage(pkg)
        
        packageImported = true
      } else {
        // Log the errors.
        pkg.errors.allErrors.each { ObjectError error ->
          log.error "${ messageSource.getMessage(error, LocaleContextHolder.locale) }"
        }
      }
    } else {
      // Log the errors.
      pkg.errors.allErrors.each { ObjectError error ->
        log.error "${ messageSource.getMessage(error, LocaleContextHolder.locale) }"
      }
    }
    
    packageImported
  }

  boolean importPackageFromKbart (CSVReader file) {
    boolean packageImported = false
    log.debug("Attempting to import package from KBART file")

    // peek gets line without removing from iterator
    // readNext gets line and removes it from the csvReader object
    String headerValue = file.readNext()[0]
    def header = (headerValue).split("\t")

    // Create an object containing fields we can accept and their mappings in our domain structure, as well as indices in the imported file, with -1 if not found
    Map acceptedFields = [
      publication_title: [field: 'title', index: -1],
      print_identifier: [field: 'siblingInstanceIdentifiers', index: -1],
      online_identifier: [field: 'instanceIdentifiers', index: -1],
      date_first_issue_online: [field: 'CoverageStatement.startDate', index: -1],
      num_first_vol_online: [field: 'CoverageStatement.startVolume', index: -1],
      num_first_issue_online: [field: 'CoverageStatement.startIssue', index: -1],
      date_last_issue_online: [field: 'CoverageStatement.endDate', index: -1],
      num_last_vol_online: [field: 'CoverageStatement.endVolume', index: -1],
      num_last_issue_online: [field: 'CoverageStatement.endIssue', index: -1],
      title_url: [field: 'url', index: -1],
      first_author: [field: 'firstAuthor', index: -1],
      title_id: [field: null, index: -1],
      embargo_info: [field: 'embargo', index: -1],
      coverage_depth: [field: 'coverageDepth', index: -1],
      notes: [field: 'coverageNote', index: -1],
      publisher_name: [field: null, index: -1],
      publication_type: [field: 'instanceMedia', index: -1],
      date_monograph_published_print: [field: 'dateMonographPublishedPrint', index: -1],
      date_monograph_published_online: [field: 'dateMonographPublished', index: -1],
      monograph_volume: [field: 'monographVolume', index: -1],
      monograph_edition: [field: 'monographEdition', index: -1],
      first_editor: [field: 'firstEditor', index: -1],
      parent_publication_title_id: [field: null, index: -1],
      preceding_publication_title_id: [field: null, index: -1],
      access_type : [field: null, index: -1]
    ]

    // Map each key to its location in the header
    for (int i=0; i<header.length; i++) {
      final String key = header[i]
      if (acceptedFields.containsKey(key)) {
        acceptedFields[key]['index'] = i
      }
    }

    // At this point we have a mapping of internal fields to KBART fields and their indexes in the imported file
    // Mandatory fields' existence should be checked
    List mandatoryFields = ['title', 'instanceIdentifiers', 'url', 'instanceMedia']

    def missingFields = mandatoryFields.findAll {field ->
      !shouldExist(acceptedFields, field)[0]
    }.collect { f ->
      shouldExist(acceptedFields, f)[1]
    }
    if (missingFields.size() != 0) {
      log.error("The import file is missing the mandatory fields: ${missingFields}")
      return (false);
    }
    
    final InternalPackageImpl pkg = new InternalPackageImpl()
    pkg.header = [
      packageSource: '123.456',
      packageSlug: '123456',
      packageName: 'myPackage2'
    ]

    String[] record;
    while ((record = file.readNext()) != null) {
      for (String value : record) {
        def lineAsArray = value.split("\t")

        Identifier siblingInstanceIdentifier = new Identifier()
        Identifier instanceIdentifier = new Identifier()

        if (
          getFieldFromLine(lineAsArray, acceptedFields, 'instanceMedia').toLowerCase() == 'monograph' ||
          getFieldFromLine(lineAsArray, acceptedFields, 'instanceMedia').toLowerCase() == 'book'
        ) {
            siblingInstanceIdentifier.namespace = 'ISBN'
            instanceIdentifier.namespace = 'ISBN'
        } else {          
            siblingInstanceIdentifier.namespace = 'ISSN'
            instanceIdentifier.namespace = 'ISSN'
        }

        siblingInstanceIdentifier.value = getFieldFromLine(lineAsArray, acceptedFields, 'siblingInstanceIdentifiers')
        instanceIdentifier.value = getFieldFromLine(lineAsArray, acceptedFields, 'instanceIdentifiers')
        
        PackageContentImpl pkgLine = new PackageContentImpl(
          title: getFieldFromLine(lineAsArray, acceptedFields, 'title'),
          siblingInstanceIdentifiers: [
            siblingInstanceIdentifier
          ],
          instanceIdentifiers: [
            instanceIdentifier
          ],
          coverage: buildCoverage(lineAsArray, acceptedFields),
          url: getFieldFromLine(lineAsArray, acceptedFields, 'url'),
          firstAuthor: getFieldFromLine(lineAsArray, acceptedFields, 'firstAuthor'),
          embargo: getFieldFromLine(lineAsArray, acceptedFields, 'embargo'),
          coverageDepth: getFieldFromLine(lineAsArray, acceptedFields, 'coverageDepth'),
          coverageNote: getFieldFromLine(lineAsArray, acceptedFields, 'coverageNote'),
          instanceMedia: getFieldFromLine(lineAsArray, acceptedFields, 'instanceMedia'),
          instanceMedium: "electronic",

          dateMonographPublished: getFieldFromLine(lineAsArray, acceptedFields, 'dateMonographPublished'),
          dateMonographPublishedPrint: getFieldFromLine(lineAsArray, acceptedFields, 'dateMonographPublishedPrint'),

          monographVolume: getFieldFromLine(lineAsArray, acceptedFields, 'monographVolume'),
          monographEdition: getFieldFromLine(lineAsArray, acceptedFields, 'monographEdition'),
          firstEditor: getFieldFromLine(lineAsArray, acceptedFields, 'firstEditor')
        )

        // We add this information to our package
        pkg.packageContents << pkgLine
      }
    }

    def result = packageIngestService.upsertPackage(pkg)
    //TODO Use this information to return true if the package imported successfully or false otherwise
    packageImported = true
    
    return (packageImported)
  }

  private String getFieldFromLine(String[] lineAsArray, Map acceptedFields, String fieldName) {
    //ToDo potentially work out how to make this slightly less icky, it worked a lot nicer without @CompileStatic
    String index = getIndexFromFieldName(acceptedFields, fieldName)
    if (lineAsArray[index.toInteger()] == '') {
      return null;
    }
  return lineAsArray[index.toInteger()];
  }

  private String getIndexFromFieldName(Map acceptedFields, String fieldName) {
    String index = (acceptedFields.values().find { it['field']?.equals(fieldName) })['index']
    return index;
  }

  private List shouldExist(Map acceptedFields, String fieldName) {
    boolean result = false
    String importField = acceptedFields.find { it.value['field']?.equals(fieldName) }?.key

    if (getIndexFromFieldName(acceptedFields, fieldName) != '-1') {
      result = true
    }
    return [result, importField];
  }

  private LocalDate parseDate(String date) {
    // We know that data coming in here matches yyyy, yyyy-mm or yyyy-mm-dd
    LocalDate outputDate

    DateTimeFormatter yearFormat = new DateTimeFormatterBuilder()
    .appendPattern("yyyy")
    .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
    .toFormatter();

    DateTimeFormatter monthYearFormat = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM")
    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
    .toFormatter();

    switch(date) {
      case ~ '^\\d{4}\$':
        outputDate = LocalDate.parse(date, yearFormat);
        break;
      case ~ '^\\d{4}(-(\\d{2}))\$':
        outputDate = LocalDate.parse(date, monthYearFormat);
        break;
      default:
        outputDate = LocalDate.parse(date);
        break;
    }
    return outputDate;
  }

  private List buildCoverage(String[] lineAsArray, Map acceptedFields) {
    //TODO StartDate can't be null, currently this parsing isn't working as expected
    String startDate = getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.startDate')
    String endDate = getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.endDate')

    LocalDate endDateLocalDate
    if (endDate != null) {
      endDateLocalDate = parseDate(endDate)
    } else {
      endDateLocalDate = null
    }

    LocalDate startDateLocalDate
    if (startDate != null) {
      startDateLocalDate = parseDate(startDate)
    } else {
      startDateLocalDate = null
    }

    if (
      (getFieldFromLine(lineAsArray, acceptedFields, 'instanceMedia').toLowerCase() != 'monograph' ||
      getFieldFromLine(lineAsArray, acceptedFields, 'instanceMedia').toLowerCase() != 'book') &&
      startDateLocalDate != null
    ) {
      return ([
        new CoverageStatement(
          startDate: startDateLocalDate,
          startVolume: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.startVolume'),
          startIssue: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.startIssue'),
          endDate: endDateLocalDate,
          endVolume: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.endVolume'),
          endIssue: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.endIssue')
        )
      ]);
    } else {
      if (getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.startDate') != '') {
        log.error("Unexpected coverage information for for title: ${getFieldFromLine(lineAsArray, acceptedFields, 'title')} of type: monograph")
      }
      return [];
    } 
  }
}