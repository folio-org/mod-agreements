package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicy
import grails.gorm.MultiTenant

/**
 * GORM entity representing persisted access policies within the Grails application.
 * Extends the base AccessPolicy structure from AccessControl libraries.
 *
 * This class maps to the `access_policy` table and connects access control
 * data with internal ERM resources (e.g., SubscriptionAgreements).
 */
class AccessPolicyEntity extends AccessPolicy implements MultiTenant<AccessPolicyEntity> {

  static mapping = {
            table 'access_policy'
               id column: 'id', generator: 'uuid2', length:36
          version column: 'version'

             type column: 'acc_pol_type'
      description column: 'acc_pol_description'
      dateCreated column: 'acc_pol_date_created'
         policyId column: 'acc_pol_policy_id'

    // Map the foreign key to the AccessPolicyContainer
    resourceClass column: 'acc_pol_resource_class'
       resourceId column: 'acc_pol_resource_id'
  }

  static constraints = {
             type nullable: false
      description nullable: true
         policyId blank: false
    resourceClass nullable: false
       resourceId nullable: false
  }
}
