package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.http.bodies.ClaimBody
import grails.validation.Validateable

/**
 * GrailsClaimBody is a Grails-specific implementation of ClaimBody.
 * It extends ClaimBody and implements Validateable to provide validation
 * capabilities for grails endpoints.
 */
class GrailsClaimBody extends ClaimBody implements Validateable {
  // Override the claims property to use a Grails-specific implementation which is ALSO validated
  class GrailsPolicyClaim extends ClaimBody.PolicyClaim implements Validateable {
    boolean _delete = false; // Flag to indicate if the claim should be deleted

    static constraints = {
      id nullable: true, blank: false
      policyId nullable: false, blank: false
      type nullable: false, blank: false
      description nullable: true, blank: false
    }
  }
  List<GrailsPolicyClaim> claims;

  static constraints = {
    claims validator: { val, _obj, errors ->
      if (!val) {
        return false; // Don't allow null claims
      }

      val.eachWithIndex { claim, i ->
        // Manually trigger validation on the nested object
        if (!claim.validate()) {
          // Copy errors from the nested object to the parent's error collection
          claim.errors.allErrors.each { nestedError ->
            def fieldName = "claims[${i}].${nestedError.field}"
            errors.rejectValue(fieldName, nestedError.code, nestedError.arguments, nestedError.defaultMessage)
          }
        }
      }

      // Return true at the end so that the rejectValues are found on the parent object
      return true
    }
  }
}
