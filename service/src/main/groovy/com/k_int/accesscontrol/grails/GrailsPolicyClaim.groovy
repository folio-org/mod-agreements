package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyType
import com.k_int.accesscontrol.core.http.bodies.PolicyClaim
import com.k_int.accesscontrol.core.http.responses.Policy
import grails.validation.Validateable

/** * Grails implementation of PolicyClaim.
 * This class represents a single policy claim with its associated properties.
 * It implements the PolicyClaim interface and is marked as Validateable for Grails validation.
 */
class GrailsPolicyClaim implements PolicyClaim, Validateable {
  String id
  GrailsPolicy policy
  AccessPolicyType type
  String description

  void setPolicy(Policy p) {
    this.policy = (GrailsPolicy) p
  }

  static constraints = {
    id nullable: true, blank: false
    policy validator: { val, _obj, errors ->
      if (!val || val == '') {
        return false // Don't allow null or blank claims
      }

      if (!val.validate()) {
        // Copy errors from the nested object to the parent's error collection
        val.errors.allErrors.each { nestedError ->
          def fieldName = "policy.${nestedError.field}"
          errors.rejectValue(fieldName, nestedError.code, nestedError.arguments, nestedError.defaultMessage)
        }
      }

      // Return true at the end so that the rejectValues are found on the parent object
      return true
    }
    type nullable: false, blank: false
    description nullable: true, blank: false
  }
}