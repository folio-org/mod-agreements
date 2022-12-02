package org.olf.kb

import grails.gorm.MultiTenant
import org.olf.general.StringUtils


/**
 * Recording the availability of a titleInstance on a platform
 */
public class PlatformTitleInstance extends ErmResource implements MultiTenant<PlatformTitleInstance> {

  TitleInstance titleInstance
  Platform platform
  String url
  
  String getName() {
    "'${StringUtils.truncate( titleInstance?.name, 110 )}' on Platform '${StringUtils.truncate( platform?.name, 110 )}'"
  }

  String getLongName() {
    "'${titleInstance.name}' on Platform '${platform.name}'"
  }

  static transients = ['longName']
  
  static hasMany = [
    packageOccurences: PackageContentItem,
  ]

  static mappedBy = [
    packageOccurences: 'pti'
  ]

  static mapping = {
        titleInstance column:'pti_ti_fk'
             platform column:'pti_pt_fk'
                  url column:'pti_url'
  }

  static constraints = {
          titleInstance(nullable:false, blank:false)
               platform(nullable:false, blank:false)
                    url(nullable:true, blank:false)
  }

}
