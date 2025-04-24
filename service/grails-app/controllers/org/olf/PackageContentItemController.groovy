package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.Pkg
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import grails.converters.JSON


/**
 * Explore package content items - the KB
 */
@Slf4j
@CurrentTenant
class PackageContentItemController extends OkapiTenantAwareController<PackageContentItem>  {

  PackageContentItemController() {
    super(PackageContentItem)
  }

  // TODO: Override POST and DELETE
  def heirarchicalDeletePCIs() {

  }

}

