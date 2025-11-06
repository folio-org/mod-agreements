package com.k_int.accesscontrol.core.policycontrolled;

import com.k_int.accesscontrol.core.PolicyRestriction;

import java.util.HashMap;

/**
 * A map that associates {@link PolicyRestriction} keys with {@link RestrictionMapEntry} values.
 * <p>
 * This map is used to control how policy restrictions are inherited or mapped from parent objects.
 * Ownership can either pass through directly from the parent (default) or be mapped to different
 * restrictions. For example, a child object's CREATE/EDIT/DELETE actions might be restricted by
 * the EDIT action on the parent, or a child may have its own standalone policies.
 * </p>
 */
public class PolicyControlledRestrictionMap extends HashMap<PolicyRestriction, RestrictionMapEntry> {
}
