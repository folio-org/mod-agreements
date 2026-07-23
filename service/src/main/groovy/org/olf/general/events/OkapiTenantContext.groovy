package org.olf.general.events

import com.k_int.okapi.OkapiTenantResolver
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@CompileStatic
@Component
class OkapiTenantContext implements TenantContext {

  @Override
  String currentTenant() {
    String schemaOrTenant = Tenants.currentId() as String
    if (schemaOrTenant == null) {
      return null
    }
    // Tenants.currentId() returns the schema name under OkapiTenantResolver
    // (e.g. "diku_mod_agreements"). Reverse it to the raw tenant id ("diku").
    return OkapiTenantResolver.schemaNameToTenantId(schemaOrTenant)
  }
}