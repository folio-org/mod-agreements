package org.olf.dataimport.erm

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

// Make distinction between ErmPackageImpl which requires contentItems and that which does not.
@GrailsCompileStatic
class ErmPackageImplWithContentItems extends ErmPackageImpl implements Validateable {
  static constraints = {
    contentItems minSize: 1
  }
}
