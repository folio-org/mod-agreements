package org.olf.rbac

import grails.gorm.MultiTenant

public class RBACAffiliation implements MultiTenant<RBACAffiliation> {
  String id

  String user // A FOLIO UUID for a given user. No foreign key as this user is in a DIFFERENT schema
  AffiliationRole role // An enum for sysops vs admin etc

  // FIXME This is confusing to me at the moment
  // Users can have different roles in different systems.
  // Do we need a grantContext as well? But that will repeat information potentially
  String party // A carefully constructed party string, such as GBVShared1/UB-Rostock/Medical

  // FIXME tbd how does this EXACTLY link to Acq groups?

  static mapping = {
    table   'rbac_affiliation'
    id      column: 'raff_id', generator: 'uuid2', length:36
    version column: 'raff_version'
    user    column: 'raff_user'
    role    column: 'raff_role'
    party   column: 'raff_party'
  }

  static constraints = {
  }
}
