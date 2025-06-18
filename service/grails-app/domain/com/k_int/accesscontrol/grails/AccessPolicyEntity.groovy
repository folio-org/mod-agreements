package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicy

// FIXME we could move this grails stuff out to a grails plugin
//  specifically using the central java code
// Or actually just into web-toolkit?

import grails.gorm.MultiTenant

class AccessPolicyEntity extends AccessPolicy implements MultiTenant<AccessPolicyEntity> {
  static mapping = {
    table         'access_policy'
    id            column: 'id', generator: 'uuid2', length:36
    version       column: 'version'

    type          column: 'acc_pol_type'
    description   column: 'acc_pol_description'
    dateCreated   column: 'acc_pol_date_created'
    policyId      column: 'acc_pol_policy_id'
  }

  static constraints = {
    type        nullable: false
    description nullable: true
    policyId       blank: false
  }
}
