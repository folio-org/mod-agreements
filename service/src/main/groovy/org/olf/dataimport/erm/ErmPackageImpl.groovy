package org.olf.dataimport.erm

import java.time.LocalDate

import org.olf.dataimport.erm.Identifier

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.PackageHeaderSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

@GrailsCompileStatic
class ErmPackageImpl implements PackageHeaderSchema, PackageSchema, Validateable {
  
  String source
  String reference
  String name
  PackageProvider packageProvider
  Boolean trustedSourceTI
  Date sourceDataCreated
  Date sourceDataUpdated
  String availabilityScope
  String lifecycleStatus 
  List<Identifier> identifiers

  Set<ContentItem> contentItems = []
  
  // Defaults for internal scheam so we can make them optional in the constraints.
  final LocalDate startDate = null
  final LocalDate endDate = null
  final String _intenalId = null
  final String status = null
  
  static hasMany = [
    contentItems: ContentItem
  ]
  
  static constraints = {
    startDate nullable: true
    endDate nullable: true
    _intenalId nullable: true, blank: false
    status nullable: true, blank: false
    trustedSourceTI nullable: true
    
    source    nullable: false, blank: false
    reference nullable: false, blank: false
    name      nullable: false, blank: false
    packageProvider nullable: true
    contentItems minSize: 1
  }

  @Override
  public PackageHeaderSchema getHeader() {
    // This object also implements the header.
    this
  }
  
  @Override
  public String getPackageSource() {
    source
  }
  
  @Override
  public String getPackageName() {
    name
  }  
  
  @Override
  public String getPackageSlug() {
    reference
  }
  
  @Override
  public Collection<ContentItem> getPackageContents() {
    contentItems
  }

  @Override
  public Boolean getTrustedSourceTI() {
    trustedSourceTI
  }
  
  @Override
  public Date getSourceDataCreated() {
    sourceDataCreated
  } 
  
  @Override
  public Date getSourceDataUpdated() {
    sourceDataUpdated
  }  
  
  @Override
  public String getAvailabilityScope() {
    availabilityScope
  }  
  
  @Override
  public String getLifecycleStatus() {
    lifecycleStatus
  }

  @Override
  public List<Identifier> getIdentifiers() {
    identifiers
  }
  
  String toString() {
    "${name} from ${packageProvider}"
  }
}
