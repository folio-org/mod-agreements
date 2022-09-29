package org.olf.dataimport.erm

import org.olf.dataimport.internal.PackageSchema.PackageDescriptionUrlSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

@GrailsCompileStatic
class PackageDescriptionUrl implements PackageDescriptionUrlSchema, Validateable {
  
  String url
  
  String getUrl() {
    this.url?.trim()
  }
  
  static constraints = {
    url      nullable: false, blank: false
  }
  
  String toString() {
    "${url}"
  }
}
