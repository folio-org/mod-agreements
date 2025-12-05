package org.olf.erm

import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.gorm.MultiTenant

//  FIXME This isn't something we actually want long term probably
/*@PolicyControlled(
  ownerColumn = 'pol_owner_fk',
  ownerField = 'owner',
  ownerClass = Entitlement.class,
  resourceTableName = 'order_line',
  resourceIdColumn = "pol_id", // For grails modules we use SQL Column name for resourceId
  resourceIdField = "id",
  createRestrictionMapping = PolicyRestriction.UPDATE, // ONLY ALLOW CREATE ON UPDATE OF OWNER
  hasStandaloneReadPolicies = true, // FIXME we probably don't want to actually do this
  readRestrictionMapping = PolicyRestriction.UPDATE // This is a bit nonsense, but using for non-destructive testing
  //readRestrictionMapping = PolicyRestriction.NONE // Cuts off the tree at this point
)*/
@PolicyControlled(
  ownerColumn = 'pol_owner_fk',
  ownerField = 'owner',
  ownerClass = Entitlement.class,
  resourceTableName = 'order_line',
  resourceIdColumn = "pol_id", // For grails modules we use SQL Column name for resourceId
  resourceIdField = "id",
  hasStandaloneUpdatePolicies = true // FIXME get rid of this
)
public class OrderLine implements MultiTenant<OrderLine>, Clonable<OrderLine> {
	
	String id
	String poLineId
	
	static belongsTo = [ owner: Entitlement ]
  
	  static mapping = {
  //    table 'order_lines'
					 id column: 'pol_id', generator: 'uuid2', length:36
				version column: 'pol_version'
				  owner column: 'pol_owner_fk'  
			   poLineId column: 'pol_orders_fk'
	}
  
	static constraints = {
		 owner(nullable:false, blank:false);
	   poLineId(nullable:true, blank:false);
	}
  
  /**
   * Need to resolve the conflict manually and add the call to the clonable method here.
   */
  @Override
  public OrderLine clone () {
    Clonable.super.clone()
  }
}
