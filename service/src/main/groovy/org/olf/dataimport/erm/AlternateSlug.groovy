package org.olf.dataimport.erm

import org.olf.dataimport.internal.PackageSchema.AlternateSlugSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

@GrailsCompileStatic
class AlternateSlug implements AlternateSlugSchema, Validateable {

  String slug

  String getAlternateSlug() {
    this.slug?.trim()
  }

  static constraints = {
    slug      nullable: false, blank: false
  }

  String toString() {
    "${slug}"
  }
}
