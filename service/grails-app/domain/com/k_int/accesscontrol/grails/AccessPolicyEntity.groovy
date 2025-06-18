package com.k_int.accesscontrol.grails

// FIXME we could move this grails stuff out to a grails plugin
//  specifically using the central java code
// Or actually just into web-toolkit?

import com.k_int.accesscontrol.core.AccessPolicy

class AccessPolicyEntity extends AccessPolicy {
  String id;

  static mapping = {
    table         'access_policy'
    id            column: 'id', generator: 'uuid2', length:36
    type          column: 'acc_pol_type'
    description   column: 'acc_pol_description'
    dateCreated   column: 'acc_pol_date_created'
    policyUUID    column: 'acc_pol_policy_uuid'
  }

  static constraints = {
    type nullable: false
    description nullable: true
    dateCreated nullable: false
    policyUUID blank: false
  }
}
