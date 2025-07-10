package com.k_int.accesscontrol.core.policycontrolled;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/**
 * Represents the metadata extracted from a class annotated with {@link PolicyControlled},
 * detailing how a resource is controlled by policies, including its own identifiers and
 * its relationship to an owner in an ownership hierarchy.
 * This class is used internally by the access control system to build and navigate
 * ownership chains for policy evaluation.
 * This object is immutable and all fields are set upon construction
 */
@Data
@Builder
public class PolicyControlledMetadata {
  /**
   * The fully qualified class name of the resource entity (e.g., "org.olf.erm.SubscriptionAgreement").
   */
  final String resourceClassName;
  /**
   * The name of the database column that stores the unique identifier for this resource.
   * This is typically the primary key column name.
   */
  final String resourceIdColumn;
  /**
   * The name of the field (property) in the resource class that represents its unique identifier.
   * (e.g., "id"). This can be used in HQL/Criteria queries.
   */
  final String resourceIdField;

  // Ownership metadata
  /**
   * The name of the database column that stores the ID of this resource's owner.
   * This column exists in the current resource's table.
   */
  final String ownerColumn;
  /**
   * The name of the field (property) in the resource class that represents the association
   * to its owner entity (e.g., "owner" or "subscriptionOwner").
   * This can be used for HQL/Criteria joins.
   */
  final String ownerField;
  /**
   * The {@link Class} object of the owner entity.
   */
  final Class<?> ownerClass;
  /**
   * Tracks how "far up" the hierarchy this {@code PolicyControlledMetadata} instance is. <br/>
   * -1: Represents the "base class" or leaf resource. <br/>
   * 0: Represents the direct owner of the base class. <br/>
   * 1: Represents the owner of the owner of the base class, and so on.
   */
  @Builder.Default
  final int ownerLevel = -1;

  // Owner alias fields
  /**
   * An alias name generated for this owner level when building dynamic queries
   * involving joins (e.g., "owner_alias_0"). This is {@code null} for the leaf class.
   */
  @Nullable
  final String aliasName;
  /**
   * The full path in terms of SQL column names (including previous aliases) to reference
   * this owner from the preceding level in the ownership chain (e.g., "owner_alias_0.owner_id").
   * This is used for constructing join conditions with specific column names.
   * This is {@code null} for the leaf class.
   */
  @Nullable
  final String aliasOwnerColumn;
  /**
   * The full path in terms of entity field names (including previous aliases) to reference
   * this owner from the preceding level in the ownership chain (e.g., "owner_alias_0.owner").
   * This is used for constructing entity-level joins in HQL/Criteria.
   * This is {@code null} for the leaf class.
   */
  @Nullable
  final String aliasOwnerField;
}
