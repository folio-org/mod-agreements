package org.olf.general

import org.olf.kb.ErmResource

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class TemplatedUrl implements MultiTenant<TemplatedUrl> {
  String id
  String name
  String url

  ErmResource resource

  static mapping = {
          id column:'tu_id', generator: 'uuid2', length:36
        name column:'tu_name'
         url column:'tu_url'
    resource column: 'tu_resource_fk'
  }
}