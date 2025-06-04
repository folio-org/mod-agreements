package org.olf

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.hibernate.Transaction
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.PackageContentItem

/**
 * Explore package content items - the KB
 */
@Slf4j
@CurrentTenant
class IdentifierController extends OkapiTenantAwareController<Identifier> {
  IdentifierController() {
    super(Identifier)
  }

  def namespaces() {
    respond doTheLookup(IdentifierNamespace) {}
  }
}
