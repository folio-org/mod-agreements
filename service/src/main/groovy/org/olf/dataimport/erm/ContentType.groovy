package org.olf.dataimport.erm

import org.olf.dataimport.internal.PackageSchema.ContentTypeSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

@GrailsCompileStatic
class ContentType implements ContentTypeSchema, Validateable {
  
  String contentType
  
  String getContentType() {
    this.contentType?.trim()
  }
  
  static constraints = {
    contentType      nullable: false, blank: false
  }
  
  String toString() {
    "${contentType}"
  }
}
