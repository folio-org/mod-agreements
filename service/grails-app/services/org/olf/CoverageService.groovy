package org.olf

import java.time.Instant
import java.time.LocalDate

import javax.servlet.http.HttpServletRequest

import org.grails.datastore.mapping.validation.ValidationException
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.hibernate.sql.JoinType
import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.erm.Entitlement
import org.olf.general.jobs.CoverageRegenerationJob
import org.olf.kb.AbstractCoverageStatement
import org.olf.kb.CoverageStatement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.validation.ObjectError
import org.springframework.web.context.request.RequestContextHolder

import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.k_int.okapi.OkapiTenantResolver

import grails.events.annotation.Subscriber
import grails.gorm.DetachedCriteria
import grails.gorm.multitenancy.Tenants
import grails.util.Holders
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
public class CoverageService {
  
  private static MessageSource getMessageSource() {
    Holders.grailsApplication.mainContext.getBean('messageSource')
  }
  
  private Map<String, Iterable<AbstractCoverageStatement>> addToRequestIfPresent (final Map<String, Iterable<AbstractCoverageStatement>> statements) {
    
    if (statements) {
      GrailsWebRequest rAtt = (GrailsWebRequest)RequestContextHolder.getRequestAttributes()
      if (rAtt) {
        final String controllerName = rAtt.controllerName
        final String actionName = rAtt.actionName
        final HttpServletRequest request = rAtt.request
        
        final String key = "${controllerName}.${actionName}.customCoverage"
        final Map<String, Set<AbstractCoverageStatement>> current = request.getAttribute(key) ?: [:]
        current.putAll(statements)
        request.setAttribute(key, current)
      }
    }
    
    statements
  }
  
  public Map<String, Iterable<AbstractCoverageStatement>> lookupCoverageOverrides (final Map resultsMap, final String agreementId = null) {
    final List<ErmResource> resources = resultsMap?.get('results')
    
    resources ? lookupCoverageOverrides(resources, agreementId) : [:]
  }
  
  public Map<String, Iterable<AbstractCoverageStatement>> lookupCoverageOverrides (final Iterable<ErmResource> resources, final String agreementId = null) {
    
    if (!resources || resources.size() < 1) return [:]
    
    // Grab the resources
    final List statementQuery = Entitlement.createCriteria().list {
      
      createAlias 'resource', 'ermResource'
      createAlias 'ermResource.contentItems', 'pcis', JoinType.LEFT_OUTER_JOIN
      if (agreementId) {
        eq 'owner.id', agreementId
      } else {
        isNotNull 'owner.id'
      }
     
      or {
        
        final Set<String> ids = resources.collect{ it.id }
        
        // Linked to package.
        'in' 'resource.id', ids
        
        and {
          eq 'ermResource.class', Pkg
          'in' 'pcis.id', ids
        }
      }
      
      projections {
        property ('id')
        property ('resource.id')
        property ('pcis.id')
      }
    }
    
    Entitlement ent
    final Map<String, Set<AbstractCoverageStatement>> statements = statementQuery.collectEntries {
      if (!ent || ent.id != it[0]) {
        // Change the entitlement.
        ent = Entitlement.read (it[0])
      }
      
      // Add the coverage from the entitlement. Call collect to create a copy of the collection.
      [ "${it[2] ?: it[1]}" : ent.coverage.collect() ]
    }

    // Add to the request (if there is one) and return.
    addToRequestIfPresent (statements)
  }

  public String nullIfBlank( final String value ) {
    return (value?.trim()?.length() ?: 0) > 0 ? value : null
  }
  
  /**
   * Set coverage from schema
   */
  public static void setCoverageFromSchema (final ErmResource resource, final Iterable<CoverageStatementSchema> coverage_statements) {
    
    boolean changed = false
    final Set<CoverageStatement> statements = []
    try {
      
      // Clear the existing coverage, or initialize to empty set.
      if (resource.coverage) {
        statements.addAll( resource.coverage.collect() )
        resource.coverage.clear()
        resource.save(failOnError: true) // Necessary to remove the orphans.
      }
      
      for ( CoverageStatementSchema cs : coverage_statements ) {
        if (cs.validate()) {
          CoverageStatement new_cs = new CoverageStatement([
            startDate   : cs.startDate,
            endDate     : cs.endDate,
            startVolume : ("${cs.startVolume}".trim() ? cs.startVolume : null),
            startIssue  : ("${cs.startIssue}".trim() ? cs.startIssue : null),
            endVolume   : ("${cs.endVolume}".trim() ? cs.endVolume : null),
            endIssue    : ("${cs.endIssue}".trim() ? cs.endIssue : null)
          ])

          resource.addToCoverage( new_cs )
          
          // Validate the object at each step.
          if (!resource.validate()) {
            resource.errors.allErrors.each { ObjectError error ->
              log.error (messageSource.getMessage(error, LocaleContextHolder.locale))
            }
            throw new ValidationException('Adding coverage statement invalidates Resource', resource.errors)
          }
          
          resource.save()
        } else {
          // Not valid coverage statement
          cs.errors.allErrors.each { ObjectError error ->
            log.error (messageSource.getMessage(error, LocaleContextHolder.locale))
          }
        }
      }
      
      log.debug("New coverage saved")
      changed = true
    } catch (ValidationException e) {
      log.error("Coverage changes to Resource ${resource.id} not saved")
    }
    
    if (!changed) {
      // Revert the coverage set.
      if (!resource.coverage) resource.coverage = []
      statements.each {
        resource.addToCoverage( it )
      }
    }
    
    resource.save(failOnError: true, flush:true) // Save.
  }
  
  /**
   * Given an PlatformTitleInstance calculate the coverage based on the higher level
   * PackageContentItem coverage values linked to this PTI
   *
   * @param pti The PlatformTitleInstance
   */
  public static void calculateCoverage( final PlatformTitleInstance pti ) {
    
    // Use a sub query to select all the coverage statements linked to PCIs,
    // linked to this pti
    List<org.olf.dataimport.erm.CoverageStatement> allCoverage = CoverageStatement.createCriteria().list {
      'in' 'resource.id', new DetachedCriteria(PackageContentItem).build {
        readOnly (true)
        
        createAlias 'pti', 'pci_pti'
          eq 'pci_pti.id', pti.id

        projections {
          property ('id')
        }
      }
    }.collect{ CoverageStatement cs -> 
      new org.olf.dataimport.erm.CoverageStatement([
        'startDate': cs.startDate,
        'startIssue': cs.startIssue,
        'startVolume': cs.startVolume,
        'endDate': cs.endDate,
        'endIssue': cs.endIssue,
        'endVolume': cs.endVolume
      ])
    }
        
    allCoverage = collateCoverageStatements(allCoverage)
    
    setCoverageFromSchema(pti, allCoverage)
  }
  
  /**
   * Given an TitleInstance calculate the coverage based on the higher level
   * PackageContentItem coverage values linked to this PTI
   *
   * @param ti The TitleInstance
   */
  public static void calculateCoverage( final TitleInstance ti ) {
    
    // Use a sub query to select all the coverage statements linked to PTIs,
    // linked to this TI
    List<org.olf.dataimport.erm.CoverageStatement> allCoverage = CoverageStatement.createCriteria().list {
      'in' 'resource.id', new DetachedCriteria(PlatformTitleInstance).build {
        readOnly (true)
        
        createAlias 'titleInstance', 'pti_ti'
          eq 'pti_ti.id', ti.id

        projections {
          property ('id')
        }
      }
    }.collect{ CoverageStatement cs -> 
      new org.olf.dataimport.erm.CoverageStatement([
        'startDate': cs.startDate,
        'startIssue': cs.startIssue,
        'startVolume': cs.startVolume,
        'endDate': cs.endDate,
        'endIssue': cs.endIssue,
        'endVolume': cs.endVolume
      ])
    }
    
    allCoverage = collateCoverageStatements(allCoverage)
    
    setCoverageFromSchema(ti, allCoverage)
  }
  
  private static int dateWithinCoverage(CoverageStatementSchema cs, LocalDate date, int defaultValue) {
    
    // This is ambiguous. Null date could be open start or open end. Let the calling method sort it out
    // We'll respond with null.
    if (date == null) return defaultValue
    
    if (cs.startDate == null && cs.endDate == null) return 0
    if (date == null) return ( cs.startDate ? -1 : (cs.endDate ? 1 : 0) )
    
    if (date <= cs.endDate) {
      return ( cs.startDate == null || date >= cs.startDate ? 0 : -1 )
    }
    
    if (date >= cs.startDate) {
      return ( cs.endDate == null || date <= cs.endDate ? 0 : 1 )
    }

    // If we get this far we know it outside the start/or end date
    (date > cs.endDate ? 1 : -1)
  }
  
  private static List<CoverageStatementSchema> collateCoverageStatements( final Iterable<CoverageStatementSchema> coverage_statements ) {
    
    // Define our list
    List<CoverageStatementSchema> results = []
    
    // Return early if we can.
    if (coverage_statements.size() < 2) {
      results.addAll(coverage_statements)
      return results
    }
    
    for (CoverageStatementSchema cs : coverage_statements) {      
      // Use an iterator for in-place editing of the collection.      
      boolean absorbed = subsume(results.listIterator(), cs)
    }
    
    results
  }
  
  private static boolean subsume (ListIterator<CoverageStatementSchema> iterator, CoverageStatementSchema statement) {
    boolean absorbed = false
    while (!absorbed && iterator.hasNext()) {
      CoverageStatementSchema current = iterator.next()
      
      int comparison = dateWithinCoverage(current, statement.startDate, -1)
      if (comparison == 0) {
        // Starts within current item, check end.
        comparison = dateWithinCoverage(current, statement.endDate, 1)
        if (comparison == 0) {
          // Also within. This item is already dealt with.
          // No action needed.
          absorbed = true
        } else {
          // End date beyond this statement.
          if (iterator.hasNext()) {
            // There is a next statement. We need to see if the next statement includes the end date
            // and if it does then we should remove this statement. If not, we need to adjust this statement.
            final CoverageStatementSchema next = iterator.next()
            
            // Move back, immediately.
            iterator.previous()
            
            // There is overlap
            if (dateWithinCoverage(next, statement.endDate, 1) >= 0) {
              // Then we should remove this item and deal with it as part of the next item.
              iterator.remove()
              absorbed = subsume(iterator, statement)
            } else {
              // Lengthen this one.
              current.endDate = statement.endDate
              absorbed = true
            }
          } else {
            // Lengthen this one.
            current.endDate = statement.endDate
            absorbed = true
          }
        }
        
      } else if (comparison < 0) {
        // Starts before current item, check end.
        comparison = dateWithinCoverage(current, statement.endDate, 1)
        
        if (comparison < 0) {
          
          // Ends before current statement. Add.
          // Add before this current one. For that we first need to move backwards.
          iterator.previous()
          iterator.add(statement)
          iterator.next() // Sets the pointer internally back to the correct position.
          absorbed = true
          
        } else if (comparison == 0) {
          // Within current. Just increase startdate
          current.startDate = statement.startDate
          absorbed = true
          
        } else {
          if (iterator.hasNext()) {
            // There is a next statement. We need to see if the next statement includes the end date
            // and if it does then we should remove this statement. If not, we need to adjust this statement.
            final CoverageStatementSchema next = iterator.next()
            
            // Move back, immediately.
            iterator.previous()
            
            // There is overlap
            if (dateWithinCoverage(next, statement.endDate, 1) >= 0) {
              // Then we should remove this item and deal with it as part of the next item.
              iterator.remove()
              absorbed = subsume(iterator, statement)
            } else {
              // Lengthen this one.
              current.endDate = statement.endDate
              current.startDate = statement.startDate
              absorbed = true
            }
            
          } else {
            // Lengthen this one.
            current.endDate = statement.endDate
            current.startDate = statement.startDate
            absorbed = true
          }
        }
      } else {
        // Starts after the current statement.
        // We don't need to do anything as the method will
        // just move on and check the next statement.
      }
    }
    
    // If we get this far and absorbed is false then we should just add the item.
    if (!absorbed) {
      iterator.add(statement)
    }
    
    absorbed
  }  
  
  private static PackageContentItem asPCI (ErmResource res) {
    res instanceof PackageContentItem ? res : null
  }
  
  private static PlatformTitleInstance asPTI (ErmResource res) {
    res instanceof PlatformTitleInstance ? res : null
  }
  
  private static TitleInstance asTI (ErmResource res) {
    res instanceof TitleInstance ? res : null
  }
  
  public static void changeListener(ErmResource res) {
    
    final PackageContentItem pci = asPCI(res)
    if ( pci ) {
      log.debug "PCI updated, regenerate PTI's coverage"
      calculateCoverage( pci.pti )
    }

    final PlatformTitleInstance pti = asPTI(res)
    if ( pti ) {
      log.debug "PTI updated regenerate TI's coverage"
      calculateCoverage( pti.titleInstance )
    }

    final TitleInstance ti = asTI(res)
    if ( ti ) {
      log.debug 'TI updated'
    }
  }
  
  private static final Version COVERAGE_IMPROVEMENTS_VERSION = Version.forIntegers(2,3) // Version trigger.
  
  @Subscriber('okapi:tenant_enabled')
  public void onTenantEnabled (final String tenantId, final boolean existing_tenant, final boolean upgrading, final String toVersion, final String fromVersion) {
    if (upgrading && fromVersion) {
      try {
        if (Version.valueOf(fromVersion).compareTo(COVERAGE_IMPROVEMENTS_VERSION) < 0) {
          // We are upgrading from a version prior to when the coverage changes were introduced,
          // lets schedule a job to retrospectively alter the coverage statements
          log.debug "Regenerate coverage based on tenant upgrade prior to improvements being present"
          triggerRegenrationForTenant(tenantId)
        }
      } catch(ParseException pex) {
        // From version couldn't be parsed as semver we should ignore.
        log.debug "${fromVersion} could not be parsed as semver not running Coverage Regeneration."
      }
    }
  }
  
  @Subscriber('okapi:tenant_regen_coverage')
  public void onTenantRegenCoverage(final String tenantId, final String value, final String existing_tenant, final String upgrading, final String toVersion, final String fromVersion) {
    log.debug "Regenerate coverage based on explicit request during tenant activation"
    triggerRegenrationForTenant(tenantId)
  }
  
  private void triggerRegenrationForTenant(final String tenantId) {
    final String tenant_schema_id = OkapiTenantResolver.getTenantSchemaName(tenantId)
    Tenants.withId(tenant_schema_id) {

      CoverageRegenerationJob job = CoverageRegenerationJob.findByStatusInList([
        CoverageRegenerationJob.lookupStatus('Queued'),
        CoverageRegenerationJob.lookupStatus('In progress')
      ])

      if (!job) {
        job = new CoverageRegenerationJob(name: "Coverage Regeneration ${Instant.now()}")
        job.setStatusFromString('Queued')
        job.save(failOnError: true, flush: true)
      } else {
        log.debug('Regeneration job already running or scheduled. Ignore.')
      }
    }
  }
  
  private static final Closure ptiQuery = { 
    order 'lastUpdated', 'asc'
  }
  
  
  public void triggerRegenration () {
    // Select all PTIs and regenerate coverage for them.
    final int batchSize = 100
    
    int count = 0
    List<PlatformTitleInstance> ptis =  PlatformTitleInstance.createCriteria().list ([max: batchSize, offset: batchSize * count]) { 
      order 'id'
    }
    while (ptis && ptis.size() > 0) {
      count ++
      ptis.each { final PlatformTitleInstance pti ->
        
        PlatformTitleInstance.withNewTransaction {
          log.info "Recalculating coverage for PTI ${pti.id}"
          calculateCoverage( pti )
        }
      }
      
      // Next page...
      ptis = PlatformTitleInstance.createCriteria().list ([max: batchSize, offset: batchSize * count]) { 
        order 'id'
      }
    }
  }
  
}
