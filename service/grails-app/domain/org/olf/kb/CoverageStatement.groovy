package org.olf.kb

import grails.gorm.MultiTenant
import org.hibernate.Hibernate


/**
 * A coverage statement - can apply to a PackageContentItem OR a TitlePlatform OR a title
 * but that should be an exclusive link
 */
public class CoverageStatement extends AbstractCoverageStatement implements MultiTenant<CoverageStatement> {
  public static final Class<? extends ErmResource>[] ALLOWED_RESOURCES = [PackageContentItem, PlatformTitleInstance, TitleInstance] as Class[]

  String id
  
  // pci, pti or ti - See validator below.
  ErmResource resource

  static mapping = {
                   id column:'cs_id', generator: 'uuid', length:36
              version column:'cs_version'
             resource column:'cs_resource_fk'
            startDate column:'cs_start_date'
              endDate column:'cs_end_date'
          startVolume column:'cs_start_volume'
           startIssue column:'cs_start_issue'
            endVolume column:'cs_end_volume'
             endIssue column:'cs_end_issue'
  }

  static constraints = {
    importFrom AbstractCoverageStatement
    resource(nullable:false, validator: { val, inst ->
      if ( val ) {
        Class c = Hibernate.getClass(val)
        if (!CoverageStatement.ALLOWED_RESOURCES.contains(c)) {
          ['allowedTypes', "${c.name}", "entitlement", "resource"]
        }
      }
    })
  }
}
