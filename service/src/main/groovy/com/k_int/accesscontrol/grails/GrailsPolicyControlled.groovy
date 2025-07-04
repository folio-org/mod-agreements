package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.PolicyControlled

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to identify domain classes that are subject to policy-based access control,
 * and provide the crucial information for subquery parameters automatically
 *
 * This can be used for automatic cleanup of policies when a resource is deleted
 * or for generic policy queries across resource types.
 *
 * Implements com.k_int.accesscontrol.core.PolicyControlled, keeping the boundary for
 * grails modules to this SINGLE grails implementation
 */
@PolicyControlled(
  resourceClass = "",      // These are required by Java annotations, but will be overridden below
  resourceIdColumn = ""
)
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@interface GrailsPolicyControlled {
  String resourceClass()
  String resourceIdColumn()
}