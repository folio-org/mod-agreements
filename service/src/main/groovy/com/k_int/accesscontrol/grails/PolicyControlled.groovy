package com.k_int.accesscontrol.grails

/**
 * Marker trait to identify domain classes that are subject to policy-based access control.
 *
 * This can be used for automatic cleanup of policies when a resource is deleted
 * or for generic policy queries across resource types.
 */
trait PolicyControlled {

}