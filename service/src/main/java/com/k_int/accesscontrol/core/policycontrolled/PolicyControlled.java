package com.k_int.accesscontrol.core.policycontrolled;

import com.k_int.accesscontrol.core.PolicyRestriction;

import java.lang.annotation.*;

/**
 * Annotation to mark classes that are controlled by access policies.
 * <p>
 * This annotation is used to specify the resource and owner identifiers for policy evaluation.
 * It allows the policy engine to determine the resource and ownership hierarchy for access control.
 * </p>
 * <p>
 * At runtime, classes such as {@code PolicyControlledManager} use this annotation to
 * introspect resource and ownership metadata. This enables generic policy enforcement
 * by dynamically extracting resource identifiers and ownership relationships from annotated classes.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PolicyControlled {
  /**
   * The name of the database column that identifies the resource.
   * <p>
   * This is used to determine the resource for policy evaluation.
   * </p>
   * @return the resource column name
   */
  String resourceIdColumn() default "";
  /**
   * The name of the field in the class that holds the resource's unique identifier.
   * <p>
   * This is used to determine the resource for policy evaluation.
   * </p>
   * @return the resource ID field name
   */
  String resourceIdField() default "";


  // Allow us to roam up an ownership tree
  /**
   * The name of the database column that identifies the owner of the resource.
   * <p>
   * This is used to determine the ownership hierarchy for policy evaluation.
   * </p>
   * @return the owner column name
   */
  String ownerColumn() default "";
  /**
   * The name of the field in the class that holds the owner's unique identifier.
   * <p>
   * This is used to determine the ownership hierarchy for policy evaluation.
   * </p>
   * @return the owner field name
   */
  String ownerField() default "";
  /**
   * The class that owns the resource, used to determine the ownership hierarchy.
   * <p>
   * Defaults to {@code Object.class} if not specified, indicating no specific owner.
   * </p>
   * @return the owner class
   */
  Class<?> ownerClass() default Object.class;

  /**
   * Controls how the READ restriction for this class maps to the parent's restriction.
   *
   * <ul>
   *   <li>Default: {@code PolicyRestriction.READ} — the child READ is mapped to the parent's READ restriction.</li>
   *   <li>{@code PolicyRestriction.NONE}: indicates this class does NOT rely on the parent for READ mapping.</li>
   * </ul>
   *
   * <p>Note: When this is set to {@code NONE} and {@link #hasStandaloneReadPolicies()} is {@code false},
   * the calling engine must handle that case explicitly (for example by emitting a dummy always-true SQL
   * fragment so that the restriction does not accidentally deny access).</p>
   *
   * @return the mapped parent restriction to use for READ
   */
  PolicyRestriction readRestrictionMapping() default PolicyRestriction.READ;

  /**
   * Indicates whether this class defines standalone READ policies (independent of its parent).
   *
   * <p>When {@link #readRestrictionMapping()} is {@code NONE} and this flag is {@code true},
   * the engine should consult the policies defined for this class directly. If the flag is {@code false}
   * and {@link #readRestrictionMapping()} is {@code NONE}, the engine must treat the level as a pass-through
   * (i.e. behave as if the restriction is always satisfied) unless other mappings or filters are provided.</p>
   *
   * @return {@code true} if this class has its own READ policies; {@code false} otherwise
   */
  boolean hasStandaloneReadPolicies() default false;

  /**
   * Controls how the CREATE restriction for this class maps to the parent's restriction.
   *
   * <ul>
   *   <li>Default: {@code PolicyRestriction.CREATE} — the child CREATE is mapped to the parent's CREATE restriction.</li>
   *   <li>{@code PolicyRestriction.NONE}: indicates this class does NOT rely on the parent for CREATE mapping.</li>
   * </ul>
   *
   * <p>Note: Some engines may treat CREATE specially (for example, acquisition-units often never restrict CREATE).
   * When this is set to {@code NONE} and {@link #hasStandaloneCreatePolicies()} is {@code false},
   * the calling engine must handle that case explicitly (for example by emitting a dummy always-true SQL
   * fragment so that the restriction does not accidentally deny access).</p>
   *
   * @return the mapped parent restriction to use for CREATE
   */
  PolicyRestriction createRestrictionMapping() default PolicyRestriction.CREATE;

  /**
   * Indicates whether this class defines standalone CREATE policies (independent of its parent).
   *
   * @return {@code true} if this class has its own CREATE policies; {@code false} otherwise
   */
  boolean hasStandaloneCreatePolicies() default false;

  /**
   * Controls how the APPLY_POLICIES restriction for this class maps to the parent's restriction.
   *
   * <ul>
   *   <li>Default: {@code PolicyRestriction.APPLY_POLICIES} — the child APPLY_POLICIES is mapped to the parent's APPLY_POLICIES restriction.</li>
   *   <li>{@code PolicyRestriction.NONE}: indicates this class does NOT rely on the parent for APPLY_POLICIES mapping.</li>
   * </ul>
   *
   * <p>Note: APPLY_POLICIES is not relevant for an entity if there are NO standalone policies for any restriction on it
   * The {@link com.k_int.accesscontrol.main.PolicyEngine} should reject any attempt to assign policies to such a resource.
   * When this is set to {@code NONE} and {@link #hasStandaloneApplyPolicies()} is {@code false},
   * the calling engine must handle that case explicitly.</p>
   *
   * @return the mapped parent restriction to use for APPLY_POLICIES
   */
  PolicyRestriction applyPoliciesRestrictionMapping() default PolicyRestriction.APPLY_POLICIES;

  /**
   * Indicates whether this class defines standalone APPLY_POLICIES policies (independent of its parent).
   *
   * @return {@code true} if this class has its own APPLY_POLICIES policies; {@code false} otherwise
   */
  boolean hasStandaloneApplyPolicies() default false;

  /**
   * Controls how the UPDATE restriction for this class maps to the parent's restriction.
   *
   * <ul>
   *   <li>Default: {@code PolicyRestriction.UPDATE} — the child UPDATE is mapped to the parent's UPDATE restriction.</li>
   *   <li>{@code PolicyRestriction.NONE}: indicates this class does NOT rely on the parent for UPDATE mapping.</li>
   * </ul>
   *
   * <p>When this is set to {@code NONE} and {@link #hasStandaloneUpdatePolicies()} is {@code false},
   * the calling engine must handle that case explicitly.</p>
   *
   * @return the mapped parent restriction to use for UPDATE
   */
  PolicyRestriction updateRestrictionMapping() default PolicyRestriction.UPDATE;

  /**
   * Indicates whether this class defines standalone UPDATE policies (independent of its parent).
   *
   * @return {@code true} if this class has its own UPDATE policies; {@code false} otherwise
   */
  boolean hasStandaloneUpdatePolicies() default false;

  /**
   * Controls how the DELETE restriction for this class maps to the parent's restriction.
   *
   * <ul>
   *   <li>Default: {@code PolicyRestriction.DELETE} — the child DELETE is mapped to the parent's DELETE restriction.</li>
   *   <li>{@code PolicyRestriction.NONE}: indicates this class does NOT rely on the parent for DELETE mapping.</li>
   * </ul>
   *
   * <p>When this is set to {@code NONE} and {@link #hasStandaloneDeletePolicies()} is {@code false},
   * the calling engine must handle that case explicitly.</p>
   *
   * @return the mapped parent restriction to use for DELETE
   */
  PolicyRestriction deleteRestrictionMapping() default PolicyRestriction.DELETE;

  /**
   * Indicates whether this class defines standalone DELETE policies (independent of its parent).
   *
   * @return {@code true} if this class has its own DELETE policies; {@code false} otherwise
   */
  boolean hasStandaloneDeletePolicies() default false;
}
