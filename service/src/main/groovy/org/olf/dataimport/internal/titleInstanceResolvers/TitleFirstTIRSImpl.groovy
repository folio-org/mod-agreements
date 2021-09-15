package org.olf.dataimport.internal.titleInstanceResolvers

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


import groovy.json.*

import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
class TitleFirstTIRSImpl extends BaseTIRS implements TitleInstanceResolverService {
  private static final String TEXT_MATCH_TITLE_HQL = '''
      SELECT ti from TitleInstance as ti
        WHERE 
          ti.name = :queryTitle
          AND ti.subType.value like :subtype
      '''

  /* This method lowercases, strips all leading and trailing whitespace,
   * and replaces all internal duplicated whitespaces with a single space
   */
  private String titleNormaliser(String s) {
    return s.toLowerCase().trim().replaceAll("\\s+", " ")
  }

  public TitleInstance resolve (ContentItemSchema citation, boolean trustedSourceTI) {

    throw new RuntimeException("This isn't implemented yet");
  }
}
