package org.olf.dataimport.erm

import org.olf.dataimport.internal.PackageSchema.AlternateResourceNameSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

@GrailsCompileStatic
class AlternateResourceName implements AlternateResourceNameSchema, Validateable {
  
  String name
  
  String getAlternateResourceName() {
    this.name?.trim()
  }
  
  static constraints = {
    name      nullable: false, blank: false
  }
  
  String toString() {
    "${name}"
  }
}
