package org.olf.erm

import javax.persistence.Transient

import org.hibernate.Hibernate
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance

import grails.databinding.BindInitializer
import grails.gorm.MultiTenant


/**
 * Entitlement (A description of a right to access a specific digital resource, which can be an 
 * title on a platform (But not listed in a package), a title named in a package, a full package of resources
 *
 * OFTEN attached to an agreement, but it's possible we know we have the right to access a resource
 * without perhaps knowing which agreement controls that right.
 *
 */
public class Entitlement implements MultiTenant<Entitlement> {
  public static final Class<? extends ErmResource>[] ALLOWED_RESOURCES = [Pkg, PackageContentItem, PlatformTitleInstance] as Class[]

  String id

  ErmResource resource

  // The date ranges on which this line item is active. These date ranges allow the system to determine
  // what content is "Live" in an agreement. Content can be "Live" without being switched on, and 
  // vice versa. The dates indicate that we believe the agreement is in force for the items specified.
  // For Trials, these dates will indicate the dates of the trial, for live agreements the agreement item dates
  Date activeFrom
  Date activeTo

  Date contentUpdated

  // Type - must be set to external for externally defined packages, null or local for things defined in the local DB
  String type

  // These three properties allow us to create an entitlement which is externally defined. An externally defined
  // entitlement does not link to a resource in the tenant database, but instead will use API calls to define
  // the contents of a package. An example would be Authority:'eHoldings', reference: '301-3707', label 'Bentham science complete
  // as defined in EKB'
  String authority
  String reference
  String label

  static belongsTo = [
    owner:SubscriptionAgreement
  ]

  static hasMany = [
    coverage: HoldingsCoverage,
    poLines: POLineProxy
  ]

  static mappedBy = [
    coverage: 'entitlement',
    poLines: 'owner'
  ]

  // Allow users to individually switch on or off this content item. If null, should default to the agreement
  // enabled setting. The activeFrom and activeTo dates determine if a content item is "live" or not. This flag
  // determines if we wish live content to be visible to patrons or not. Content can be "Live" but not enabled,
  // although that would be unusual.
  @BindInitializer({
    Boolean.TRUE // Default this value to true when binding.
  })
  Boolean enabled
  

  static mapping = {
                   id column: 'ent_id', generator: 'uuid', length:36
              version column: 'ent_version'
                owner column: 'ent_owner_fk'
             resource column: 'ent_resource_fk'
                 type column: 'ent_type'
              enabled column: 'ent_enabled'
       contentUpdated column: 'ent_content_updated'
           activeFrom column: 'ent_active_from'
             activeTo column: 'ent_active_to'
            authority column: 'ent_authority'
            reference column: 'ent_reference'
                label column: 'ent_label'
             coverage cascade: 'all-delete-orphan'

  }

  static constraints = {
            owner(nullable:true,  blank:false)

          // Now that resources can be internally or externally defined, the internal resource link CAN be null,
          // but if it is, there should be authorty, reference and label properties.
          resource (nullable:true, validator: { val, inst ->
            if ( val ) {
              Class c = Hibernate.getClass(val)
              if (!Entitlement.ALLOWED_RESOURCES.contains(c)) {
                ['allowedTypes', "${c.name}", "entitlement", "resource"]
              }
            }
          })
          
          coverage (validator: HoldingsCoverage.STATEMENT_COLLECTION_VALIDATOR)

              type(nullable:true,  blank:false)
           enabled(nullable:true,  blank:false)
    contentUpdated(nullable:true,  blank:false)
        activeFrom(nullable:true,  blank:false)
          activeTo(nullable:true,  blank:false)
         authority(nullable:true,  blank:false)
         reference(nullable:true,  blank:false)
             label(nullable:true,  blank:false)
  }
  
  @Transient
  public String getExplanation() {
    
    String result = null
    
    if (resource) {
      // Get the class using the hibernate helper so we can
      // be sure we have the target class and not a proxy wrapper.
      Class c = Hibernate.getClass(resource)
      switch (c) {
        case Pkg:
          result = 'Agreement includes a package containing this item'
          break
        case PlatformTitleInstance:
          result = 'Agremment includes this title directly'
          break
        case PackageContentItem:
          result = 'Agreement includes this item from a package specifically'
          break
      }
    }
    result
  }

  @Transient
  public boolean getHaveAccess() {
    return haveAccessAsAt(new Date());
  }

  /**
   * If activeFrom <= date <= activeTo 
   */
  public boolean haveAccessAsAt(Date point_in_time) {
    boolean result = false;
    if ( ( activeFrom != null ) && ( activeTo != null ) ) {
      result = ( ( activeFrom.getTime() <= point_in_time.getTime() ) && ( point_in_time.getTime() <= activeTo.getTime() ) )
    }
    else if ( activeFrom != null ) {
      result = ( activeFrom.getTime() <= point_in_time.getTime() )
    }
    else if ( activeTo != null ) {
      result = ( point_in_time.getTime() <= activeTo.getTime() )
    }
    else {
      // activeFrom and activeTo are both null - we assume this is perpetual then, so true
      return true;
    }
    return result;
  }
 
}
