package org.olf.dataimport.internal.titleInstanceResolvers

import org.olf.general.StringUtils

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.IdentifierSchema
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.TitleInstance
import org.olf.kb.Work

import grails.gorm.transactions.Transactional
import grails.web.databinding.DataBinder

import org.olf.dataimport.internal.TitleInstanceResolverService
import groovy.util.logging.Slf4j

import groovy.json.*
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil


/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
class WorkSourceIdentifierTIRSImpl extends BaseTIRS implements DataBinder, TitleInstanceResolverService {
  // Inject IdFirstTIRS to fall back to it
  IdFirstTIRSImpl idFirstTIRSImpl;

  public TitleInstance resolve(ContentItemSchema citation, boolean trustedSourceTI) {
    // log.debug("TitleInstanceResolverService::resolve(${citation})");
    TitleInstance result = null;

    /* 
     * ---- APPROACH ----
     * First try to find a Work with the correct sourceIdentifier
     * Case n=1 -> One work. Lookup electronic titles on that work
     *     Case m=1 -> One electronic title, return it
     *     Case m>1 -> Multiple electronic titles matched for that work (?)
     *     Case m=0 -> No electronic titles on that work, create one?
     * Case n>1 -> Multiple works matched. Error out?
     * Case n=0 -> No works matched. Attempt to fallback to IdFirstTIRS resolve.
     * Once resolved: Check work attached to TI
     *     Case sourceIdentifier=null -> attach incoming sourceIdentifier
     *          (This in theory should only happen for pre existing data in the system)
     *     Case sourceIdentifier matches -> Finished (this should happen when creating new Work from scratch)
     *     Case sourceIdentifier does not match -> Treat incoming title as a new one, create new work and title etc
     */

    return result;
  }
}
