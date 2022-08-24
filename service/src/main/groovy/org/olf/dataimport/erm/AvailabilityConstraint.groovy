package org.olf.dataimport.erm

import org.olf.dataimport.internal.PackageSchema.AvailabilityConstraintSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

@GrailsCompileStatic
class AvailabilityConstraint implements AvailabilityConstraintSchema, Validateable {
  
  String body
  
  String getBody() {
    this.body?.trim()
  }
  
  static constraints = {
    body      nullable: false, blank: false
  }
  
  String toString() {
    "${body}"
  }
}
