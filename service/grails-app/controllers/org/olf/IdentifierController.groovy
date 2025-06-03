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

  @Override // Overwritten so we can insert the "resource" stuff
  @Transactional
  def show() {
    Identifier identifier = queryForResource(params.id);

    log.debug("LOGDEBUG WHAT IS RESOURCE: ${identifier.occurrences}")

    def model = [
      identifier: identifier,
    ]
    render(view: '/identifier', model: model)
  }

  def namespaces() {
    respond doTheLookup(IdentifierNamespace) {}
  }
}
