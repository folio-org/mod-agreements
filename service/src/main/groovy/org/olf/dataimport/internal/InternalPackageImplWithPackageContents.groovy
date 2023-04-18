package org.olf.dataimport.internal

import grails.compiler.GrailsCompileStatic
import org.olf.dataimport.erm.Identifier

import grails.validation.Validateable

// Make distinction between InternalPackageImpl which requires packageContents and that which does not.
@GrailsCompileStatic
class InternalPackageImplWithPackageContents extends InternalPackageImpl implements Validateable {
  static constraints = {
    packageContents   minSize: 1
  }
}
