package org.olf.kb

import javax.persistence.Transient

import org.olf.general.Org

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.MultiTenant

/**
 * mod-erm representation of a package
 */
public class Pkg extends ErmResource implements MultiTenant<Pkg> {
  String source
  String reference  // Reference contains the KBs authoritative ID for this package - Reference should be unique within KB
  Platform nominalPlatform
  Org vendor
  Date sourceDataCreated
  Date sourceDataUpdated
  @Defaults(['Current', 'Retired', 'Expected', 'Deleted'])
  RefdataValue lifecycleStatus
  @Defaults(['Global'])
  RefdataValue availabilityScope
  Set<PackageDescriptionUrl> packageDescriptionUrls
  Set<ContentType> contentTypes
  Set<AvailabilityConstraint> availabilityConstraints

  
  // Declaring this here will provide defaults for the type defined in ErmResource but not create
  // a subclass specific column
  @Defaults(['Aggregated Full Text', 'Abstracts and Index', 'Package'])
  RefdataValue type

  static hasMany = [
              contentItems: PackageContentItem,
    packageDescriptionUrls: PackageDescriptionUrl,
              contentTypes: ContentType,
    availabilityConstraints: AvailabilityConstraint
    // tags: KIWTTag
  ]

  static mappedBy = [ 
              contentItems: 'pkg',
    packageDescriptionUrls: 'owner',
              contentTypes: 'owner'
  ]

  static mapping = {
                        table 'package'
                       source column:'pkg_source'
                    reference column:'pkg_reference'
              nominalPlatform column:'pkg_nominal_platform_fk'
                       vendor column:'pkg_vendor_fk'
            sourceDataCreated column:'pkg_source_data_created'
            sourceDataUpdated column:'pkg_source_data_updated'
              lifecycleStatus column:'pkg_lifecycle_status_fk'
            availabilityScope column:'pkg_availability_scope_fk'
       packageDescriptionUrls cascade: 'all-delete-orphan'
                 contentTypes cascade: 'all-delete-orphan'
      availabilityConstraints cascade: 'all-delete-orphan'
  }

  static constraints = {
               name(nullable:false, blank:false)
             source(nullable:false, blank:false)
          reference(nullable:false, blank:false)
    nominalPlatform(nullable:true, blank:false)
             vendor(nullable:true, blank:false)
  sourceDataCreated(nullable:true, blank:false)
  sourceDataUpdated(nullable:true, blank:false)
    lifecycleStatus(nullable:true, blank:false)
  availabilityScope(nullable:true, blank:false)
  }


  @Transient
  public long getResourceCount() {
    long num_items = (PackageContentItem.executeQuery("""
      SELECT count(*) FROM PackageContentItem pci
      WHERE pci.pkg.id = :id
      AND pci.removedTimestamp = NULL
      """,
      [id: id]
    ) ?: [])[0];
    
    return num_items;
  }

}
