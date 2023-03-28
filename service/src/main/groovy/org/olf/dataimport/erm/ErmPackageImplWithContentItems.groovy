package org.olf.dataimport.erm

import grails.compiler.GrailsCompileStatic

// Make distinction between ErmPackageImpl which requires contentItems and that which does not.
@GrailsCompileStatic
class ErmPackageImplWithContentItems extends ErmPackageImpl {
  static constraints = {
    contentItems minSize: 1
  }
}
