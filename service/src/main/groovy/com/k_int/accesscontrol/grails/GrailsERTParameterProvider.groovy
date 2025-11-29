package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadata
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.ERTParameterProvider
import com.k_int.accesscontrol.core.sql.AccessControlSql
import com.k_int.accesscontrol.core.sql.AccessControlSqlType
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters
import com.k_int.accesscontrol.grails.criteria.AccessControlHibernateTypeMapper
import org.hibernate.Session
import org.hibernate.query.NativeQuery
import org.hibernate.type.Type

/**
 * A grails implementation of the {@link ERTParameterProvider} from the AccessControl library.
 * This allows a grails controller to construct an object with all the context necessary to then act as a provider
 * within the PolicyEngine for enriching a RestrictionTree.
 */
class GrailsERTParameterProvider implements ERTParameterProvider {

  GrailsERTParameterProvider(
    AccessControlHibernateTypeMapper typeMapper,
    PolicyControlledManager policyControlledManager,
    String resourceId,
    int startLevel
  ) {
    this.typeMapper = typeMapper
    this.policyControlledManager = policyControlledManager
    this.resourceId = resourceId
    this.startLevel = startLevel
  }
  /**
   * The {@link PolicyControlledManager} containing the ownership chain to calculate parameters on for a given ownerLevel
   * @param policyControlledManager
   */
  PolicyControlledManager policyControlledManager

  /**
   * The resource id corresponding to the "start" rung in the ownership chain
   */
  String resourceId
  int startLevel

  AccessControlHibernateTypeMapper typeMapper // FIXME THIS IS DEFINITELY NOT A GOOD PATTERN

  // FIXME when resourceId is wibble and startLevel is 1 then we have OWNER in hand, and so need to
  String ownerIdProvider(int ownerLevel) {
    // Hmm... for now shortcut out if we hand null in, since we don't actually need to resolve the id for READ LIST for example... not certain about this though
    if (resourceId == null || ownerLevel < startLevel) { // FIXME I'm not sure about the ownerLevel < startLevel part here -- necessary to avoid issues in getOwnerIdSql
      return null
    }

    // First we get the SQL to resolve the owner id
    AccessControlSql sql = policyControlledManager.getOwnerIdSql(resourceId, ownerLevel, startLevel)

    // Then we build the SQL and perform the query
    AccessPolicyEntity.withSession { Session sess ->
      NativeQuery identifierQuery = sess.createNativeQuery(sql.getSqlString())
      sql.getParameters().eachWithIndex { Object param, int paramIndex  ->
        // Hibernate params start at 1 :(
        identifierQuery.setParameter(paramIndex + 1, param, (Type) typeMapper.getHibernateType((AccessControlSqlType) sql.getTypes()[paramIndex]))
      }
      String id = identifierQuery.list()[0]
      return id
    }
  }

  PolicySubqueryParameters provideParameters(int ownerLevel) {
    String resourceId = ownerIdProvider(ownerLevel)

    String resourceAlias = '{alias}'
    PolicyControlledMetadata ownerLevelMetadata = policyControlledManager.getOwnerLevelMetadata(ownerLevel)
    if (ownerLevelMetadata.getAliasName()) {
      resourceAlias = ownerLevelMetadata.getAliasName()
    } // This should always be calculated, except for "root" entry which uses the base alias

    String resourceClass = policyControlledManager.resolveOwnerClass(ownerLevel)
    String resourceIdColumn = policyControlledManager.resolveOwnerResourceIdColumn(ownerLevel)

    return PolicySubqueryParameters
      .builder()
      .accessPolicyTableName(AccessPolicyEntity.TABLE_NAME)
      .accessPolicyTypeColumnName(AccessPolicyEntity.TYPE_COLUMN)
      .accessPolicyIdColumnName(AccessPolicyEntity.POLICY_ID_COLUMN)
      .accessPolicyResourceIdColumnName(AccessPolicyEntity.RESOURCE_ID_COLUMN)
      .accessPolicyResourceClassColumnName(AccessPolicyEntity.RESOURCE_CLASS_COLUMN)
      .resourceAlias(resourceAlias) // This alias can be deeply nested from owner or '{alias}' for hibernate top level queries
      .resourceIdColumnName(resourceIdColumn)
      .resourceId(resourceId) // This might be null (For LIST type queries)
      .resourceClass(resourceClass)
      .build()
  }
}
