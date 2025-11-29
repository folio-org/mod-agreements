package com.k_int.accesscontrol.core.policycontrolled.restrictiontree;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.sql.*;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * An object representing a tree structure for a SINGLE restriction at some PolicyControlled level.
 * This object should be created by choosing a restriction and walking up the ownership chain from a PolicyControlledManager
 * from the leaf resource, building the tree of relevant subqueries and restrictions.
 *
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class EnrichedRestrictionTree implements IRestrictionTree {
  /**
   * The parent restriction tree node. Null if this is the root.
   * @param parent the parent restriction tree node
   * @return the parent restriction tree node
   */
  EnrichedRestrictionTree parent;

  /**
   * Tracks how "far up" the hierarchy this {@code RestrictionTree} instance is. <br/>
   * 0: Represents the "base class" or leaf resource. <br/>
   * 1: Represents the direct owner of the base class. <br/>
   * 2: Represents the owner of the owner of the base class, and so on.
   * @param ownerLevel the level in the hierarchy for this policy controlled object
   * @return the level in the hierarchy for this policy controlled object
   */
  int ownerLevel;

  /**
   * The policy restriction represented by this tree node.
   * @param restriction the policy restriction
   * @return the policy restriction
   */
  PolicyRestriction restriction;

  /**
   * The list of subqueries associated with this restriction.
   * @param subqueries the list of policy subqueries
   * @return the list of policy subqueries
   */
  List<PolicySubquery> subqueries;

  /**
   * The parameters used to fill out the subqueries for this level
   * @param parameters the policy subquery parameters
   * @return the policy subquery parameters
   */
  PolicySubqueryParameters parameters;

  /**
   * Indicates whether this level has standalone policies, independent of the parent.
   *
   * @param hasStandalonePolicies true if standalone policies exist
   * @return this builder instance
   */
  @Builder.Default
  @Accessors(fluent = true)
  boolean hasStandalonePolicies = false;

  /**
   * Generates a list of SQL subqueries for this restriction tree and its ancestors.
   * <p>
   * Traverses up the tree, collecting SQL subqueries from each node with standalone policies.
   * Throws an exception if a node claims standalone policies but has no subqueries or parameters.
   * </p>
   *
   * @return a list of {@link AccessControlSql} objects representing the SQL subqueries
   * @throws IllegalStateException if a node with standalone policies has no subqueries or parameters
   */
  public List<AccessControlSql> getSql() {
    List<AccessControlSql> sqlList = new ArrayList<>();

    EnrichedRestrictionTree node = this;
    while (node != null) {
      if (node.hasStandalonePolicies()) {
        if (node.getSubqueries() == null || node.getSubqueries().isEmpty()) {
          throw new IllegalStateException("RestrictionTree node at level " + node.getOwnerLevel() +
              " with restriction " + node.getRestriction() +
              " indicates it has standalone policies but has no subqueries defined.");
        }
        for (PolicySubquery subquery : node.getSubqueries()) {
          if (node.getParameters() == null) {
            throw new IllegalStateException("RestrictionTree node at level " + node.getOwnerLevel() +
                " with restriction " + node.getRestriction() +
                " has subqueries defined but no parameters to fill them out.");
          }
          sqlList.add(subquery.getSql(node.getParameters()));
        }
      }
      node = node.getParent();
    }

    return sqlList;
  }

  /**
   * Builds an EnrichedRestrictionTree from an existing IRestrictionTree,
   * applying the provided subqueries and parameter providers.
   *
   * @param restrictionTree the source IRestrictionTree to build from
   * @param subqueriesProvider function to provide subqueries based on owner level and restriction
   * @param parameterProvider function to provide parameters based on owner level and restriction
   * @return the constructed EnrichedRestrictionTree
   */
  public static EnrichedRestrictionTree buildFromRestrictionTree(
    IRestrictionTree restrictionTree,
    RTSubqueriesProvider subqueriesProvider,
    RTParameterProvider parameterProvider
    ) {
    EnrichedRestrictionTree enrichedTree = EnrichedRestrictionTree.builder()
      .ownerLevel(restrictionTree.getOwnerLevel())
      .restriction(restrictionTree.getRestriction())
      .hasStandalonePolicies(restrictionTree.hasStandalonePolicies())
      .parameters(parameterProvider.apply(restrictionTree.getOwnerLevel(), restrictionTree.getRestriction()))
      .subqueries(subqueriesProvider.apply(restrictionTree.getOwnerLevel(), restrictionTree.getRestriction()))
      .build();

    if (restrictionTree.getParent() != null) {
      enrichedTree.setParent(buildFromRestrictionTree(restrictionTree.getParent(), subqueriesProvider, parameterProvider));
    }

    return enrichedTree;
  }
}
