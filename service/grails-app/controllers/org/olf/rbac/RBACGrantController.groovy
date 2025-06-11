package org.olf.rbac

import com.k_int.okapi.OkapiTenantAwareController
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import org.olf.erm.SubscriptionAgreement

@Slf4j
@CurrentTenant
class RBACGrantController extends OkapiTenantAwareController<RBACGrant>  {
  RBACGrantController() {
    super(RBACGrant)
  }

  // FIXME DO NOT MERGE THIS IN FINAL PR
  // WHEN pattern is on data (IE DATA CAN HAVE %) then needs to be on LHS
  // WHEN pattern is in search then needs to be on RHS

  // WHEN pattern on both????
  public def testingrbac() {


    // FIXME for now we're testing in the desc of an agreement... this is like the "on what" part of a grant
    // Test that we can apply this dynamically in the sqlRestriction
    String rbacOwnerColumn = 'sa_description'

    // THIS is equivalent to the "find grants for ...", which is inside the EXISTS in the white paper
    respond doTheLookup ({
      def res = SubscriptionAgreement;
      and{
        //sqlRestriction("? LIKE ${rbacOwnerColumn}", ["GBV/UBV"]) // Should find agreement with DESC GBV/% AND GBV/UBV AND GBV%
        sqlRestriction("${rbacOwnerColumn} LIKE ?", ["GBV/%"]) // Finds agreement with Desc GBV/% and GBV/UBV but NOT GBV%
        //sqlRestriction("${rbacOwnerColumn} LIKE ?", ["%"]) // Finds all agreements
        //sqlRestriction("? LIKE ${rbacOwnerColumn}", ["%"]) // Finds ONLY Agreement with desc %
      }
    })
  }
}

