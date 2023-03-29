package org.olf.erm
import com.k_int.web.toolkit.domain.traits.Clonable
import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant


@GrailsCompileStatic
public class InternalContact implements MultiTenant<InternalContact>, Clonable<InternalContact> {

  String id
  String user
  @Defaults(['Subject specialist']) // Defaults to create for this property.
  RefdataValue role

  static belongsTo = [
    owner: SubscriptionAgreement
  ]

    static mapping = {
//    table 'internal_contact'
                   id column: 'ic_id', generator: 'uuid2', length:36
              version column: 'ic_version'
                owner column: 'ic_owner_fk'
                 user column: 'ic_user_fk'
                 role column: 'ic_role'
  }

  static constraints = {
       owner(nullable:false, blank:false);
        user(nullable:true, blank:false);
        role(nullable:true, blank:false);
  }

  /**
   * Need to resolve the conflict manually and add the call to the clonable method here.
   */
  @Override
  public InternalContact clone () {
    Clonable.super.clone()
  }
}
