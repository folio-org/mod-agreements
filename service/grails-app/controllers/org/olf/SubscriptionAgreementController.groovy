package org.olf

import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.Pkg
import org.hibernate.sql.JoinType
import org.olf.erm.Entitlement

import com.k_int.okapi.OkapiTenantAwareController

import grails.gorm.multitenancy.CurrentTenant
import grails.orm.HibernateCriteriaBuilder
import groovy.util.logging.Slf4j
import java.time.LocalDate
import grails.gorm.DetachedCriteria


/**
 * Control access to subscription agreements.
 * A subscription agreement (SA) is the connection between a set of resources (Which could be packages or individual titles) and a license. 
 * SAs have start dates, end dates and renewal dates. This controller exposes functions for interacting with the list of SAs
 */
@Slf4j
@CurrentTenant
class SubscriptionAgreementController extends OkapiTenantAwareController<SubscriptionAgreement>  {
  
  CoverageService coverageService
  
  SubscriptionAgreementController() {
    super(SubscriptionAgreement)
  }
  
  
  def resources () {
    
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {

      // Now
      final LocalDate today = LocalDate.now()
        
      final def results = doTheLookup (ErmResource) {
        readOnly (true)
        or {
          
          // Direct PTIs
          'in' 'id', new DetachedCriteria(PlatformTitleInstance).build {
            readOnly (true)
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              
            projections {
              property ('id')
            }
          }
          
          // Direct PCIs
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            readOnly (true)
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              
            projections {
              property ('id')
            }
          }
          
          // Pci linked via package.
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            readOnly (true)
            
            'in' 'pkg.id', new DetachedCriteria(Pkg).build {
              createAlias 'entitlements', 'pkg_ent'
                eq 'pkg_ent.owner.id', subscriptionAgreementId
                
              projections {
                property ('id')
              }
            }            
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
          'in' 'id', new DetachedCriteria(PlatformTitleInstance).build {
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              or {
                isNull 'direct_ent.activeFrom'
                le 'direct_ent.activeFrom', today
              }
              or {
                isNull 'direct_ent.activeTo'
                ge 'direct_ent.activeTo', today
              }
              
            projections {
              property ('id')
            }
          }
          
          // Direct PCIs
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              or {
                isNull 'direct_ent.activeFrom'
                le 'direct_ent.activeFrom', today
              }
              or {
                isNull 'direct_ent.activeTo'
                ge 'direct_ent.activeTo', today
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
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            
            'in' 'pkg.id', new DetachedCriteria(Pkg).build {
              createAlias 'entitlements', 'pkg_ent'
                eq 'pkg_ent.owner.id', subscriptionAgreementId
                
                or {
                  isNull 'pkg_ent.activeFrom'
                  le 'direct_ent.activeFrom', today
                }
                or {
                  isNull 'pkg_ent.activeTo'
                  ge 'direct_ent.activeTo', today
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
        or {
          
          // Direct PTIs
          'in' 'id', new DetachedCriteria(PlatformTitleInstance).build {
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              lt 'direct_ent.activeTo', today
              
            projections {
              property ('id')
            }
          }
          
          // Direct PCIs
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              
              // Valid resource
              and {
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
              }
              
              // Line or Resource in the past
              or {
                lt 'direct_ent.activeTo', today
                lt 'accessEnd', today
              }
              
            projections {
              property ('id')
            }
          }
          
          // Pci linked via package.
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            
            createAlias 'pkg.entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              
              // Valid resource
              and {
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
              }
              
              // Line or Resource in the past
              or {
                lt 'direct_ent.activeTo', today
                lt 'accessEnd', today
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
  
  def futureResources () {
    
    final String subscriptionAgreementId = params.get("subscriptionAgreementId")
    if (subscriptionAgreementId) {

      // Now
      final LocalDate today = LocalDate.now()
        
      final def results = doTheLookup (ErmResource) {
        or {
          
          // Direct PTIs
          'in' 'id', new DetachedCriteria(PlatformTitleInstance).build {
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              gt 'direct_ent.activeFrom', today
              
            projections {
              property ('id')
            }
          }
          
          // Direct PCIs
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            
            createAlias 'entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              
              // Valid resource
              and {
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
              }
              
              // Line or Resource in the future
              or {
                gt 'direct_ent.activeFrom', today
                gt 'accessStart', today
              }
              
            projections {
              property ('id')
            }
          }
          
          // Pci linked via package.
          'in' 'id', new DetachedCriteria(PackageContentItem).build {
            
            createAlias 'pkg.entitlements', 'direct_ent'
              eq 'direct_ent.owner.id', subscriptionAgreementId
              
              // Valid resource
              and {
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
              }
              
              // Line or Resource in the future
              or {
                gt 'direct_ent.activeFrom', today
                gt 'accessStart', today
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
}
