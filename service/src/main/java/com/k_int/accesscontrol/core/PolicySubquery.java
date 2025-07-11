package com.k_int.accesscontrol.core;

/**
 * Interface for generating SQL subqueries based on policy restrictions.
 * <p>
 * This interface defines a method to generate SQL subqueries that can be used
 * to filter records based on access policy restrictions.
 * </p>
 */
public interface PolicySubquery {
  String getSql(PolicySubqueryParameters parameters);
}
