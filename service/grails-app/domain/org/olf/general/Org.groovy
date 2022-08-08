package org.olf.general
import com.k_int.okapi.remote_resources.OkapiLookup
import com.k_int.web.toolkit.databinding.BindUsingWhenRef

import grails.databinding.SimpleMapDataBindingSource
import grails.gorm.MultiTenant
import grails.web.databinding.DataBindingUtils

//@OkapiDistributedDomain(config='Org')
@BindUsingWhenRef({ obj, propName, SimpleMapDataBindingSource source ->
  // If the data is asking for null binding then ensure we return here.
  final def data = source?.getAt(propName)
  if (data == null) {
    return null
  }
  Org org
  if (data.orgsUuid) {
    // Lookup the Org using the remote ID and ignore the local.
    org = Org.findOrCreateByOrgsUuid(data.orgsUuid)
  } else if (data.id) {
    System.out.println "Seen the ID ${data.id}"
    org = Org.findOrCreateById(data.id)
  } else {
    System.out.println "Just create a new one."
    org = new Org()
  }
  
  // bind the other properties
  DataBindingUtils.bindObjectToInstance(org, data)
  org.save(failOnError:true)
  
  org
})
class Org implements MultiTenant<Org> {

  String id
  String name
  
  @OkapiLookup(
    // value = '${ (obj.orgsUuid != null ) ? \'/organizations/organizations/\'+obj.orgsUuid : \'\''
    value = '/organizations/organizations/${obj.orgsUuid?:""}'
  )
  String orgsUuid

  static mapping = {
            id column: 'org_id', generator: 'uuid2', length:36
       version column: 'org_version'
          name column: 'org_name'
      orgsUuid column: 'org_orgs_uuid'
  }

  static constraints = {
           name(nullable:true, blank:false)
       orgsUuid(nullable:true, blank:false, unique:true)
  }

  /**
   * In many places we will look up an org and in the context of that lookup we may know something
   * extra about the org we are looking up. This method checks a known list of properties and if not set
   * will absorb any extra context into the record. This is used to accumulate external identifers and
   * other contextual information.
   */
  public void enrich(Map<String, Object> props) {
    boolean changed = false;
    props.keySet().each { k -> 
      if ( ( props[k] != null ) && ( [ 'reference'].contains(k) ) ) {
        if ( this[k] == null ) {
          this[k] = props[k]
        }
      }
    }

    if ( changed ) {
      this.save(flush:true, failOnError:true);
    }
  }
}
