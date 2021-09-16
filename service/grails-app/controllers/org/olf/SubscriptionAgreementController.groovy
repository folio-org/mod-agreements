package org.olf

import java.time.LocalDate
import org.grails.web.json.JSONObject
import org.hibernate.sql.JoinType
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance

import com.k_int.okapi.OkapiTenantAwareController

import grails.gorm.DetachedCriteria
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

import static org.springframework.http.HttpStatus.*


/**
 * Control access to subscription agreements.
 * A subscription agreement (SA) is the connection between a set of resources (Which could be packages or individual titles) and a license. 
 * SAs have start dates, end dates and renewal dates. This controller exposes functions for interacting with the list of SAs
 */
@Slf4j
@CurrentTenant
class SubscriptionAgreementController extends OkapiTenantAwareController<SubscriptionAgreement>  {
  
  CoverageService coverageService
  ExportService exportService
  
  SubscriptionAgreementController() {
    super(SubscriptionAgreement)
  }
  
  
  def publicLookup () {
    final List<String> referenceIds = params.list('referenceId')
    final List<String> resourceIds = params.list('resourceId')
    final List<String> disjunctiveReferences = [] + referenceIds.collect { String id ->
      String[] parts = id.split(/\-/)
      if (parts.length == 3) {
        // assuming progressive ID and we should search for multiples
        disjunctiveReferences << "${parts[0]}-${parts[1]}"
      }
      id
    }
    
    final LocalDate today = LocalDate.now()
    
    respond (doTheLookup {
      // Selectively join here to be more performant if we don't need to join.
      if (resourceIds || disjunctiveReferences) {
        createAlias 'items', 'direct_ent', (resourceIds && disjunctiveReferences ? JoinType.LEFT_OUTER_JOIN : JoinType.INNER_JOIN)
        
        createAlias 'periods', 'per'
        createAlias 'agreementStatus', 'status', JoinType.LEFT_OUTER_JOIN
        createAlias 'reasonForClosure', 'reason', JoinType.LEFT_OUTER_JOIN
        createAlias 'isPerpetual', 'perpetual', JoinType.LEFT_OUTER_JOIN
        // Makes sure we use the dates on the period to only show relevant
        or {
          // Agreement Status == (Active OR Cancelled) AND Perpetual Access == Yes
          and {
            eq 'status.value', 'closed'
            eq 'reason.value', 'cancelled'
            eq 'perpetual.value', 'yes'
          }
          
          // Agreement Status == Active AND agreement start date in the past AND (an end date in the future OR a blank end date)
          and {
            eq 'status.value', 'active'
            le 'per.startDate', today
            or {
              isNull 'per.endDate'
              ge 'per.endDate', today
            }
          }
        }
      
        or {
          if (disjunctiveReferences) {
            'in' 'direct_ent.reference', disjunctiveReferences
          }
          
          if (resourceIds) {          
            or {
              
               // Direct PTIs
              'in' 'direct_ent.resource.id', new DetachedCriteria(PlatformTitleInstance, 'direct_ptis').build {
                
                or {
                  'in' 'id', resourceIds
                  'in' 'titleInstance.id', resourceIds
                }
                
                entitlements {
                  or {
                    isNull 'activeFrom'
                    le 'activeFrom', today
                  }
                  or {
                    isNull 'activeTo'
                    ge 'activeTo', today
                  }
                }
                  
                projections {
                  property ('id')
                }
              }
              
              // Direct PCIs
              'in' 'direct_ent.resource.id', new DetachedCriteria(PackageContentItem, 'direct_pcis').build {
                or {
                  'in' 'id', resourceIds
                  'in' 'pti.id', resourceIds
                  'in' 'pti.id', new DetachedCriteria(PlatformTitleInstance, 'direct_pci_tis').build {
                    'in' 'titleInstance.id', resourceIds
                    projections {
                      property ('id')
                    }
                  }
                }
                entitlements {
                  or {
                    isNull 'activeFrom'
                    le 'activeFrom', today
                  }
                  or {
                    isNull 'activeTo'
                    ge 'activeTo', today
                  }
                }
                or {
                  isNull 'accessStart'
                  le 'accessStart', today
                }
                or {
                  isNull 'accessEnd'
                  ge 'accessEnd', today
                }
                  
                projections {
                  property ('id')
                }
              }
              
              // Pci linked via package.
              'in' 'direct_ent.resource.id', new DetachedCriteria(Pkg, 'pkgs').build {
                
                or {
                  'in' 'id', resourceIds
                  
                  contentItems {
                    'in' 'id', new DetachedCriteria(PackageContentItem, 'pkg_pcis').build {
                  
                      or {
                        'in' 'id', resourceIds
                        'in' 'pti.id', resourceIds
                        'in' 'pti.id', new DetachedCriteria(PlatformTitleInstance, 'pkg_pci_ptis').build {
                          'in' 'titleInstance.id', resourceIds
                          projections {
                            property ('id')
                          }
                        }
                      }
                      
                      entitlements {
                        or {
                          isNull 'activeFrom'
                          le 'activeFrom', today
                        }
                        or {
                          isNull 'activeTo'
                          ge 'activeTo', today
                        }
                      }
                      
                      or {
                        isNull 'accessStart'
                        le 'accessStart', today
                      }
                      or {
                        isNull 'accessEnd'
                        ge 'accessEnd', today
                      }
                        
                      projections {
                        property ('id')
                      }
                    }
                  }
                }
                
                entitlements {
                  or {
                    isNull 'activeFrom'
                    le 'activeFrom', today
                  }
                  or {
                    isNull 'activeTo'
                    ge 'activeTo', today
                  }
                }
                
                projections {
                  property ('id')
                }
              }
            }
          }
        }
      }
    })
  } 
  
  def resources () {
    
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {
      final def results = doTheLookup (ErmResource) {
        readOnly (true)
        or {
          
          // Direct PTIs
          'in' 'id', new DetachedCriteria(PlatformTitleInstance, 'direct_ptis').build {
            readOnly (true)
            
            entitlements {
              eq 'owner.id', subscriptionAgreementId
            }
              
            projections {
              property ('id')
            }
          }
          
          // Direct PCIs
          'in' 'id', new DetachedCriteria(PackageContentItem, 'direct_pcis').build {
            readOnly (true)
            
            entitlements {
              eq 'owner.id', subscriptionAgreementId
            }
              
            projections {
              property ('id')
            }
          }
          
          // Pci linked via package.
          'in' 'id', new DetachedCriteria(PackageContentItem, 'pkg_pcis').build {
            isNull 'removedTimestamp'
            
            pkg {
              entitlements {
                eq 'owner.id', subscriptionAgreementId
              }
            }
            
//            'in' 'pkg.id', new DetachedCriteria(Pkg, 'pkg_pci_pkgs').build {
//              entitlements {
//                eq 'owner.id', subscriptionAgreementId
//              }
//              
//              projections {
//                property ('id')
//              }
//            }            
            projections {
              property ('id')
            }
          }
        }
      }
      
      // This method writes to the web request if there is one (which of course there should be as we are in a controller method)
      coverageService.lookupCoverageOverrides(results, "${subscriptionAgreementId}")
      
      respond results
      return
    }
  }
  
  def currentResources () {
    
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {

      // Now
      final LocalDate today = LocalDate.now()
      final def results = doTheLookup (ErmResource) {
        or {
          
          // Direct PTIs
          'in' 'id', new DetachedCriteria(PlatformTitleInstance, 'pti_sub').build {
            
            entitlements {
              eq 'owner.id', subscriptionAgreementId
              or {
                isNull 'activeFrom'
                le 'activeFrom', today
              }
              or {
                isNull 'activeTo'
                ge 'activeTo', today
              }
            }
              
            projections {
              property ('id')
            }
          }
          
          // Direct PCIs
          'in' 'id', new DetachedCriteria(PackageContentItem, 'pci_direct_sub').build {
            
            entitlements {
              eq 'owner.id', subscriptionAgreementId
              or {
                isNull 'activeFrom'
                le 'activeFrom', today
              }
              or {
                isNull 'activeTo'
                ge 'activeTo', today
              }
            }
            or {
              isNull 'accessStart'
              le 'accessStart', today
            }
            or {
              isNull 'accessEnd'
              ge 'accessEnd', today
            }
              
            projections {
              property ('id')
            }
          }
          
          // Pci linked via package.
          'in' 'id', new DetachedCriteria(PackageContentItem,'pci_pkg_sub').build {
            
            isNull 'removedTimestamp'
            
            'in' 'pkg.id', new DetachedCriteria(Pkg, 'pci_pkg_pkg_sub').build {
              entitlements {
                eq 'owner.id', subscriptionAgreementId
                
                or {
                  isNull 'activeFrom'
                  le 'activeFrom', today
                }
                or {
                  isNull 'activeTo'
                  ge 'activeTo', today
                }
              }
              projections {
                property ('id')
              }
            }
            
            or {
              isNull 'accessStart'
              le 'accessStart', today
            }
            or {
              isNull 'accessEnd'
              ge 'accessEnd', today
            }
            
            projections {
              property ('id')
            }
          }
        }
        
        readOnly (true)
      }
      
      // This method writes to the web request if there is one (which of course there should be as we are in a controller method)
      coverageService.lookupCoverageOverrides(results, "${subscriptionAgreementId}")
      
      respond results
      return
    }
  }
  
  def droppedResources () {
    
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {

      // Now
      final LocalDate today = LocalDate.now()
        
      final def results = doTheLookup (ErmResource) {
        createAlias 'entitlements', 'direct_ent', JoinType.LEFT_OUTER_JOIN
        createAlias 'pkg', 'ind_pci_pkg', JoinType.LEFT_OUTER_JOIN
        createAlias 'ind_pci_pkg.entitlements', 'pkg_ent', JoinType.LEFT_OUTER_JOIN
          
        or {
          and {
            eq 'class', PlatformTitleInstance
            eq 'direct_ent.owner.id', subscriptionAgreementId
            lt 'direct_ent.activeTo', today
          }
          
          and {
            eq 'class', PackageContentItem
            eq 'direct_ent.owner.id', subscriptionAgreementId
            
            // Valid access start
            or {
              isNull 'accessStart'
              isNull 'direct_ent.activeTo'
              ltProperty 'accessStart', 'direct_ent.activeTo'
            }
            
            // Valid access end
            or {
              isNull 'accessEnd'
              isNull 'direct_ent.activeFrom'
              gtProperty 'accessEnd', 'direct_ent.activeFrom'
            }
            
            // Line or Resource in the past
            or {
              lt 'direct_ent.activeTo', today
              lt 'accessEnd', today
            }
          }
          
          and {
            eq 'class', PackageContentItem
            isNull 'removedTimestamp'
            eq 'pkg_ent.owner.id', subscriptionAgreementId
            
            // Valid access start
            or {
              isNull 'accessStart'
              isNull 'pkg_ent.activeTo'
              ltProperty 'accessStart', 'pkg_ent.activeTo'
            }
            // Valid access end
            or {
              isNull 'accessEnd'
              isNull 'pkg_ent.activeFrom'
              gtProperty 'accessEnd', 'pkg_ent.activeFrom'
            }
            
            // Line or Resource in the past
            or {
              lt 'pkg_ent.activeTo', today
              lt 'accessEnd', today
            }
          }
        }
        
        readOnly (true)
      }
      
      // This method writes to the web request if there is one (which of course there should be as we are in a controller method)
      coverageService.lookupCoverageOverrides(results, "${subscriptionAgreementId}")
      
      respond results
      return
    }
  }
  
  def futureResources () {
    
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {

      // Now
      final LocalDate today = LocalDate.now()
        
      final def results = doTheLookup (ErmResource) {
        
        createAlias 'entitlements', 'direct_ent', JoinType.LEFT_OUTER_JOIN
        createAlias 'pkg', 'ind_pci_pkg', JoinType.LEFT_OUTER_JOIN
        createAlias 'ind_pci_pkg.entitlements', 'pkg_ent', JoinType.LEFT_OUTER_JOIN
        
        or {
          and {
            eq 'class', PlatformTitleInstance
            eq 'direct_ent.owner.id', subscriptionAgreementId
            gt 'direct_ent.activeFrom', today
          }
          
          and {
            eq 'class', PackageContentItem
            eq 'direct_ent.owner.id', subscriptionAgreementId
            
            // Valid access start
            or {
              isNull 'accessStart'
              isNull 'direct_ent.activeTo'
              ltProperty 'accessStart', 'direct_ent.activeTo'
            }
            
            // Valid access end
            or {
              isNull 'accessEnd'
              isNull 'direct_ent.activeFrom'
              gtProperty 'accessEnd', 'direct_ent.activeFrom'
            }
            
            // Line or Resource in the future
            or {
              gt 'direct_ent.activeFrom', today
              gt 'accessStart', today
            }
          }
          
          and {
            eq 'class', PackageContentItem
            isNull 'removedTimestamp'
            eq 'pkg_ent.owner.id', subscriptionAgreementId
            
            // Valid access start
            or {
              isNull 'accessStart'
              isNull 'pkg_ent.activeTo'
              ltProperty 'accessStart', 'pkg_ent.activeTo'
            }
            
            // Valid access end
            or {
              isNull 'accessEnd'
              isNull 'pkg_ent.activeFrom'
              gtProperty 'accessEnd', 'pkg_ent.activeFrom'
            }
            
            // Line or Resource in the future
            or {
              gt 'pkg_ent.activeFrom', today
              gt 'accessStart', today
            }
          }
        }
        
        readOnly (true)
      }
      
      // This method writes to the web request if there is one (which of course there should be as we are in a controller method)
      coverageService.lookupCoverageOverrides(results, "${subscriptionAgreementId}")
      
      respond results
      return
    }
  }
  
  
  private static final Map<String, List<String>> CLONE_GROUPING = [
    'agreementInfo': ['name', 'description', 'renewalPriority' , 'isPerpetual'],
    'internalContacts': ['contacts'],
    'agreementLines': ['items'], // Do not copy poLine. Need to also duplicate coverage
    'linkedLicenses': ['linkedLicenses', 'licenseNote'],
    'externalLicenses': ['externalLicenseDocs'],
    'organizations': ['orgs'],
    'supplementaryProperties': ['customProperties'],
    'usageData': ['usageDataProviders'],
//    'tags': ['tags']
  ]
  
  @Transactional
  def doClone () {
    final Set<String> props = []
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {
      
      // Grab the JSON body.
      JSONObject body = request.JSON
      
      // Create a set of propertyNames to clone.
      
      // Build up a list of properties from the incoming json object.
      for (Map.Entry<String, Boolean> entry : body.entrySet()) {
        
        if (entry.value == true) {
        
          final String fieldOrGroup = entry.key
          if (CLONE_GROUPING.containsKey(fieldOrGroup)) {
            // Add the group instead.
            props.addAll( CLONE_GROUPING[fieldOrGroup] )
          } else {
            // Assume single field.
            props << fieldOrGroup
          }
        }
      }
      
      log.debug "Attempting to clone agreement ${subscriptionAgreementId} using props ${props}"
      SubscriptionAgreement instance = queryForResource(subscriptionAgreementId).clone(props)
      
      instance.save()
      if (instance.hasErrors()) {
        transactionStatus.setRollbackOnly()
        respond instance.errors, view:'edit' // STATUS CODE 422 automatically when errors rendered.
        return
      }
      respond instance, [status: OK]
      return
    }
    
    respond ([statusCode: 404])
  }
  
  def export () {
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {
      respond exportService.agreement(subscriptionAgreementId, params.boolean("currentOnly") ?: false)
      return
    }
    respond ([statusCode: 404])
  }
  
  @Transactional
  def delete() {
    SubscriptionAgreement sa = queryForResource(params.id)
    
    // Not found.
    if (sa == null) {
      transactionStatus.setRollbackOnly()
      notFound()
      return
    }
    
    // Return the relevant status if not allowed to delete.
    if ((sa.items?.size() ?: 0) > 0) {
      transactionStatus.setRollbackOnly()
      render status: METHOD_NOT_ALLOWED, text: "Agreement has agreement lines"
      return
    }
    
    // Return the relevant status if not allowed to delete.
    if ((sa.linkedLicenses?.size() ?: 0) > 0) {
      transactionStatus.setRollbackOnly()
      render status: METHOD_NOT_ALLOWED, text: "Agreement has license lines"
      return
    }
    
    // Return the relevant status if not allowed to delete.
    if ((sa.inwardRelationships?.size() ?: 0) > 0 || (sa.outwardRelationships?.size() ?: 0) > 0) {
      transactionStatus.setRollbackOnly()
      render status: METHOD_NOT_ALLOWED, text: "Agreement has related agreements"
      return
    }
    
    // Finally delete the license if we get this far and respond.
    deleteResource sa
    render status: NO_CONTENT
  }
}
