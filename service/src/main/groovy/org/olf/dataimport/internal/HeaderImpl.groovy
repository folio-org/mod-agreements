package org.olf.dataimport.internal
import java.time.LocalDate

import org.olf.dataimport.erm.PackageProvider
import org.olf.dataimport.internal.PackageSchema.PackageHeaderSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString

import com.k_int.web.toolkit.refdata.RefdataValue

@ToString(includePackage=false)
@GrailsCompileStatic
class HeaderImpl implements PackageHeaderSchema, Validateable {
  PackageProvider packageProvider
  String packageSource
  String status
  String packageName
  Boolean trustedSourceTI
  LocalDate startDate
  LocalDate endDate
  String packageSlug
  LocalDate sourceDataCreated
  LocalDate sourceDataUpdated
  String availabilityScope
  String lifecycleStatus 
  String _intenalId
  
  static constraints = {
    packageSource   nullable: false, blank: false
    packageSlug     nullable: false, blank: false
    packageName     nullable: false, blank: false
  }
}