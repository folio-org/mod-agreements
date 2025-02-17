package org.olf.kb.metadata

import org.olf.kb.ErmResource

import grails.gorm.MultiTenant

/*
 * A domain class to store the metadata about how the current package
 * data came to be in the system. This includes ingressType (enum)
 */
public class PackageIngressMetadata implements MultiTenant<PackageIngressMetadata> {
  ErmResource resource

  ResourceIngressType ingressType

  /* Will hold:
   *  Package level PushTaskId for Pushkb
   *  RemoteKB.id for harvest,
   *  null for json/kbart import
   */
  String ingressId

  /* Will hold:
   *  PushKB url for Pushkb (should come in along with Pkg data)
   *  null for harvest,
   *  null for json/kbart import
   */
  String ingressUrl

  /* Will hold:
   *  TIPP level PushTaskId for Pushkb
   *  null for harvest,
   *  null for json/kbart import
   */
  String contentIngressId

  /* Will hold:
   *  PushKB url for Pushkb (should come in along with TIPP data)
   *  null for harvest,
   *  null for json/kbart import
   */
  String contentIngressUrl

  static mapping = {
             resource column: 'pim_resource_fk'
          ingressType column: 'pim_ingress_type'
            ingressId column: 'pim_ingress_id'
           ingressUrl column: 'pim_ingress_url'
     contentIngressId column: 'pim_content_ingress_id'
    contentIngressUrl column: 'pim_content_ingress_url'
  }

  static constraints = {
             resource (nullable:false, blank:false)
          ingressType (nullable:false, blank:false)
            ingressId (nullable:true, blank:false)
           ingressUrl (nullable:true, blank:false)
     contentIngressId (nullable:true, blank:false)
    contentIngressUrl (nullable:true, blank:false)
  }
}
