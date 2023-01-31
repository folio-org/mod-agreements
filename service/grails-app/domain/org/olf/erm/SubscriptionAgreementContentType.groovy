package org.olf.erm;

import grails.gorm.MultiTenant
import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.refdata.CategoryId

public class SubscriptionAgreementContentType implements MultiTenant<SubscriptionAgreementContentType> {

	String id

  @CategoryId(value='AgreementContentType', defaultInternal=false)
  @Defaults(['testvalue'])
  RefdataValue subscriptionAgreementContentType

	static belongsTo = [ owner: SubscriptionAgreement ]

	  static mapping = {
      // table 'subscription_agreement_content_type'
                              id column: 'sact_id', generator: 'uuid2', length:36
                         version column: 'sact_version'
subscriptionAgreementContentType column: 'sact_subscription_agreement_content_type_fk'
                           owner column: 'sact_owner_fk'
	}

	static constraints = {
		 owner(nullable:false, blank:false);
	   subscriptionAgreementContentType(nullable:false, blank:false);
	}

}
