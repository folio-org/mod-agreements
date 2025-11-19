package com.k_int.accesscontrol.core.sql;

import com.k_int.accesscontrol.core.PolicyRestriction;

import java.util.HashMap;
import java.util.List;

/**
 * A cache that maps {@link PolicyRestriction} keys to lists of {@link PolicySubquery} values.
 * <p>
 * This cache is used to store and retrieve precomputed subqueries associated with specific
 * policy restrictions (up to headers, queryType and filters), optimizing query generation and execution.
 * </p>
 */
public class RestrictionSubqueryCache extends HashMap<PolicyRestriction, List<PolicySubquery>> {
}
