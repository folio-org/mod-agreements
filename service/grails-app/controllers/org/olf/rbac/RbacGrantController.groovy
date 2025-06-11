package org.olf.rbac

import com.k_int.okapi.OkapiTenantAwareController
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import org.olf.erm.SubscriptionAgreement

@Slf4j
@CurrentTenant
class RbacGrantController extends OkapiTenantAwareController<RBACGrant>  {
  RbacGrantController() {
    super(RBACGrant)
  }

  // FIXME DO NOT MERGE THIS IN FINAL PR
  // WHEN pattern is on data (IE DATA CAN HAVE %) then needs to be on LHS
  // WHEN pattern is in search then needs to be on RHS

  // WHEN pattern on both????
  public def test() {
    log.debug("TESTING RBAC METHOD")
    // FIXME for now we're testing in the desc of an agreement... this is like the "on what" part of a grant
    // Test that we can apply this dynamically in the sqlRestriction
    // String rbacOwnerColumn = 'sa_description'

    // THIS is equivalent to the "find grants for ...", which is inside the EXISTS in the white paper
    /*respond doTheLookup ({
      def res = SubscriptionAgreement;
      and{
        //sqlRestriction("? LIKE ${rbacOwnerColumn}", ["GBV/UBV"]) // Should find agreement with DESC GBV/% AND GBV/UBV AND GBV%
        sqlRestriction("${rbacOwnerColumn} LIKE ?", ["GBV/%"]) // Finds agreement with Desc GBV/% and GBV/UBV but NOT GBV%
        //sqlRestriction("${rbacOwnerColumn} LIKE ?", ["%"]) // Finds all agreements
        //sqlRestriction("? LIKE ${rbacOwnerColumn}", ["%"]) // Finds ONLY Agreement with desc %
      }
    })*/


    // Let's find all the grants covering pretend object for user
    def pretendUser = 'ubv-user'

    def userRolesForContext = ["USER"] // FIXME this needs to be selected...?
    // FIXME What is "context" at search time?

    def pretendObj = [
      rbacOwner: 'GBV/UBV',
      resourceClass: 'org.olf.erm.SubscriptionAgreement', // In reality we'd probs get this from the controller implementation or something?
      id: "made-up-id-1",
    ]

    // FIXME This is ONLY looking up the grants pertaining to the object in hand...
    //  we need to look up all objects where a grant exists for that object
    respond doTheLookup ({
      def res = RBACGrant;
      and{
        sqlRestriction("? LIKE rgra_resource_owner", [pretendObj.rbacOwner]) // GRANT IS PATTERN
        sqlRestriction("? LIKE rgra_resource_id", [pretendObj.id]) // GRANT IS PATTERN
        sqlRestriction("? LIKE rgra_resource_type", [pretendObj.resourceClass]) // GRANT IS PATTERN

        or {
          and {
            eq 'granteeType', GranteeType.ROLE
            'in' 'granteeId', userRolesForContext // FIXME SELECT ROLES USER HAS IN CONTEXT --- not sure yet what that means
            // FIXME Does it pertain to affiliations???
          }

          // FIXME we need the other grantee types here
        }
      }
    })
  }
}

