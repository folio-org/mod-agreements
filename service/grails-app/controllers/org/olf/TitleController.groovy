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

  DetachedCriteria pciSubQuery = PackageContentItem.where({
    eqProperty('pti.id', 'platformTitleInstance.id')
    setAlias 'packageContentItem'

    projections {
      property 'id'
    }
  })

  DetachedCriteria ptiSubQuery = PlatformTitleInstance.where({
    eqProperty('titleInstance.id', "${DEFAULT_ROOT_ALIAS}.id") //here "this" refers to the root alias of criteria
    setAlias 'platformTitleInstance'

    exists(pciSubQuery)

    projections {
      property 'id'
    }
  })

  // LEFT JOIN query
  def electronic () {
    respond doTheLookup {
      and {
        eq 'subType', TitleInstance.lookupOrCreateSubType('electronic')

        exists(ptiSubQuery)
      }
    }
  }
  
  def entitled() {
    respond doTheLookup (TitleInstance.entitled)
  }
}

