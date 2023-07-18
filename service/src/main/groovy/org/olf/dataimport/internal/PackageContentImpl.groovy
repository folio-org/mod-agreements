package org.olf.dataimport.internal

import java.time.LocalDate

import org.olf.dataimport.erm.CoverageStatement
import org.olf.dataimport.erm.Identifier
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString

import org.springframework.validation.Errors

@ToString(includePackage=false)
@GrailsCompileStatic
class PackageContentImpl implements ContentItemSchema, Validateable {
  
  List<Identifier> instanceIdentifiers
  List<Identifier> siblingInstanceIdentifiers
  List<CoverageStatement> coverage
  String title
  String instanceMedium
  String instanceMedia
  String instancePublicationMedia
  String sourceIdentifier
  String embargo
  String coverageDepth
  String coverageNote
  String platformUrl

  String dateMonographPublished
  String dateMonographPublishedPrint
  String firstAuthor
  String firstEditor
  String monographEdition
  String monographVolume
  
  String url
  String platformName

  String _platformId // This doesn't appear to be used anywhere

  LocalDate accessStart
  LocalDate accessEnd

  Long removedTimestamp

  // Must NOT have items of its own
  InternalPackageImpl contentItemPackage

  /* As far as I can tell, this validation isn't actually ever fired for the harvest process.
   * This is fine, since in general we validate the actual domain objects we
   * save to the db, but it does potentially leave space for optimisation if we can catch some
   * failures earlier.
   *
   * The newer "pushKB" process will call a validate, so extra validators have been added to allow
   * fields that _ARE_ nullable, but were never previously validated so were presumed non-nullable.
   */
  static constraints = {
    
    platformName nullable:true, blank:false
    platformUrl  blank:false, validator: { String platformUrl, PackageContentImpl instance ->
      if (!platformUrl && !instance.platformName) {
        // If platform is blank then this can't be.
        return ['null.message']
      }
    }
    
    accessStart nullable:true, validator: { LocalDate startDate, ContentItemSchema item ->
      if (!startDate && item.accessEnd) {
        return ['null.message']
      }
    }
    
    accessEnd nullable:true, validator: { LocalDate endDate, ContentItemSchema item ->
      
      if (item.accessStart &&
        endDate &&
        ( item.accessStart > endDate) ) {
          return [ 'start.after.end', 'accessStart', item.class.name, item.accessStart, endDate]
      }
    }

    contentItemPackage validator: {InternalPackageImpl pkg, ContentItemSchema item, Errors errors ->
      if ((pkg?.packageContents ?: []).size() > 0) {
        errors.rejectValue('contentItemPackage', 'contentItemPackage.has.items')
      }
    }

    instanceMedia nullable: false, blank: false
    instanceMedium nullable: true, blank: false
    coverageNote nullable: true, blank: false
    coverageDepth nullable: true, blank: false
    dateMonographPublishedPrint nullable: true, blank: false
    dateMonographPublished nullable: true, blank: false
    monographEdition nullable: true, blank: false
    instancePublicationMedia nullable: true, blank: false
    firstAuthor nullable: true, blank: false
    firstEditor nullable: true, blank: false
    _platformId nullable: true, blank: false
    monographVolume nullable: true, blank: false
    embargo nullable: true, blank: false
    removedTimestamp nullable: true, blank: false
  }
}