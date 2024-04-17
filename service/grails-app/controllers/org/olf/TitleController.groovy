package org.olf

import org.olf.kb.ErmResource
import org.olf.kb.TitleInstance
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance

import com.k_int.okapi.OkapiTenantAwareController

import grails.gorm.DetachedCriteria

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import static org.olf.general.Constants.Queries.*

@Slf4j
@CurrentTenant
class TitleController extends OkapiTenantAwareController<TitleInstance>  {

  TitleController() {
    super(TitleInstance)
  }

  def electronic () {
    respond doTheLookup {
        eq 'subType', TitleInstance.lookupOrCreateSubType('electronic')
    }
  }

  def entitled() {
    respond doTheLookup (TitleInstance.entitled)
  }
}

