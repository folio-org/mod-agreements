package org.olf.kb

import grails.gorm.MultiTenant

/**
 * KB or Knowledgebase is a loaded term in ERM contexts. Originally derived from the more generic computing
 * use of a repository of facts used to drive an inference engine, and the problem space being
 * "Can I access Volume 1 Issue 5 of X on platform Y". The idea was that instead of storring individual issue records (Or article records)
 * a KB would infer from coverage if a user had access to a particular desired content item.
 *
 * In this context a "RemoteKB" is any repository that makes assertions about titles, platforms, packages and coverage. We also extend
 * this model to include the concept of "Activation" - recording in a KB that we have "Switched on" a particular content item. Recording
 * activation is separate to "Why" we activated something - We may have several agreements that entitle us to access Vol 1 Issue 1 - present of
 * Nature on the Platform Nature.com - But we only activate that title once in each resolver or KB.
 *
 */
public class RemoteKB implements MultiTenant<RemoteKB> {

  String id
  String name
  String type // this is the name of a spring bean which will act
  String cursor // This may be a datetimestring, transaction or other service specific means of determining where we are up to
  String uri
  String listPrefix
  String fullPrefix
  String principal
  String credentials
  String syncStatus
  Long lastCheck
  Long rectype  // 1-PACKAGE, 2-TITLE

  public static final Long RECTYPE_PACKAGE = 1L;
  public static final Long RECTYPE_TITLE = 2L;

  // Mark KB as protected/readonly, e.g. the LOCAL KB
  boolean readonly = false
  // Mark KB as a trusted source of title instance metadata
  boolean trustedSourceTI = false
  // Harvesting role
  /** Does this remote KB support harvesting */
  Boolean supportsHarvesting
  /** If supported, is harvesting from this remote KB activated */
  Boolean active

  // Content activation
  /** Does this remote KB support content activation */
  Boolean activationSupported
  /** If supported, is recording actication in this remote KB enabled */
  Boolean activationEnabled

  static mapping = {
                     id column:'rkb_id', generator: 'uuid2', length:36
                version column:'rkb_version'
                   name column:'rkb_name'
                 cursor column:'rkb_cursor'
                    uri column:'rkb_uri'
             fullPrefix column:'rkb_full_prefix'
             listPrefix column:'rkb_list_prefix'
                   type column:'rkb_type'
              principal column:'rkb_principal'
            credentials column:'rkb_creds'
                rectype column:'rkb_rectype'
                 active column:'rkb_active'
     supportsHarvesting column:'rkb_supports_harvesting'
      activationEnabled column:'rkb_activation_enabled'
    activationSupported column:'rkb_activation_supported'
             syncStatus column:'rkb_sync_status'
              lastCheck column:'rkb_last_check'
               readonly column:'rkb_readonly'
        trustedSourceTI column:'rkb_trusted_source_ti'
  }

  static constraints = {
                   name( nullable:false, blank:false, unique:true)
                 cursor( nullable:true, blank:false)
                    uri( nullable:true, blank:false)
                   type( nullable:true, blank:false)
             fullPrefix( nullable:true, blank:false)
             listPrefix( nullable:true, blank:false)
              principal( nullable:true, blank:false)
            credentials( nullable:true, blank:false)
                 active( nullable:true, blank:false)
     supportsHarvesting( nullable:true, blank:false)
      activationEnabled( nullable:true, blank:false)
    activationSupported( nullable:true, blank:false)
             syncStatus( nullable:true, blank:false)
              lastCheck( nullable:true, blank:false)
               readonly( nullable:true, blank:false)
        trustedSourceTI( nullable:false, blank: false)
  }


  public String toString() {
    return "RemoteKB ${name} - ${type}/${uri}${cursor ? '/' + cursor : ''}".toString()
  }

  // When RemoteKB is readonly we want SOME properties to be editable, some not to be
  private static final Set<String> updateableWhenReadOnly = ['trustedSourceTI']
  def beforeUpdate() {
    def readonlyInDB = getPersistentValue('readonly')
    def dissallowedProperties = (updateableWhenReadOnly + this.dirtyPropertyNames) - updateableWhenReadOnly.intersect( this.dirtyPropertyNames )
    if (readonlyInDB == true && dissallowedProperties) {
      if (this.readonly == false) {
        log.debug("Denying update to KB ${this.id} / ${this.name} because 'readonly' is set to 'true' and cannot be changed to 'false'")
        return false
      }
      log.debug("Denying update to KB ${this.id} / ${this.name} because 'readonly' is set to 'true' and updates were attempted to ${dissallowedProperties}")
      return false
    }
  }

  def beforeDelete() {
    if (this.readonly == true) {
      log.debug("Denying to delete KB ${this.id} / ${this.name} because 'readonly' is set to 'true'")
      return false
    }
  }

}
