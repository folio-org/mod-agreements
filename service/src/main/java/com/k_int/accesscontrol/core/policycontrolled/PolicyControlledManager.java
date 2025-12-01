package com.k_int.accesscontrol.core.policycontrolled;

import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.PolicyControlledRestrictionTreeMap;
import com.k_int.accesscontrol.core.policycontrolled.restrictiontree.SkeletonRestrictionTree;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.core.sql.AccessControlSql;
import com.k_int.accesscontrol.core.sql.AccessControlSqlType;
import lombok.Getter;

import java.util.*;

/**
 * Manages and resolves the ownership chain for entities marked with {@link PolicyControlled} annotations.
 * This class is responsible for building a structured representation of how resources relate to their owners
 * for the purpose of access control policy evaluation.
 */
public class PolicyControlledManager {
  /**
   * The resolved ownership chain, ordered from the leaf class up to the root owner.
   * Each element in the list represents the {@link PolicyControlledMetadata} for a level in the chain.
   * @return A list of PolicyControlledMetadata representing the ordered ownership chain
   */
  @Getter
  @SuppressWarnings("javadoc")
  private final List<PolicyControlledMetadata> ownershipChain;

  /**
   * A map of policy restrictions to their corresponding skeleton restriction trees.
   * This map provides a structured representation of how each policy restriction
   * is evaluated across the ownership chain.
   * @return A PolicyControlledRestrictionTreeMap representing the restriction tree map
   */
  @Getter
  @SuppressWarnings("javadoc")
  private final PolicyControlledRestrictionTreeMap restrictionTreeMap;

  /**
   * A list of relevant policy restrictions to consider when building restriction maps.
   */
  private static final List<PolicyRestriction> relevantPolicies = List.of(
    PolicyRestriction.READ,
    PolicyRestriction.CREATE,
    PolicyRestriction.UPDATE,
    PolicyRestriction.DELETE,
    PolicyRestriction.APPLY_POLICIES
  );

  /**
   * Constructs a {@code PolicyControlledManager} with a pre-resolved ownership chain.
   *
   * @param ownershipChain A list of {@link PolicyControlledMetadata} representing the ownership chain,
   *                       ordered from the leaf class to the root owner.
   * @param restrictionTreeMap A {@link PolicyControlledRestrictionTreeMap} representing the restriction tree map.
   */
  public PolicyControlledManager(List<PolicyControlledMetadata> ownershipChain, PolicyControlledRestrictionTreeMap restrictionTreeMap) {
    this.ownershipChain = ownershipChain;
    this.restrictionTreeMap = restrictionTreeMap;
  }

  /**
   * Constructs a {@code PolicyControlledManager} by resolving the ownership chain
   * starting from the specified leaf class.
   *
   * @param leafClass The class representing the most granular resource in the ownership hierarchy.
   */
  public PolicyControlledManager(Class<?> leafClass) {
    this.ownershipChain = resolveOwnershipChain(leafClass);
    this.restrictionTreeMap = this.buildRestrictionTreeMap();
  }

  /**
   * Determine whether a given {@link PolicyRestriction} is declared as having standalone policies
   * on the supplied {@link PolicyControlled} annotation.
   *
   * @param annotation  the {@code PolicyControlled} annotation instance to inspect
   * @param restriction the policy restriction to check
   * @return {@code true} if the annotation declares standalone policies for the restriction, otherwise {@code false}
   */
  public static boolean hasStandalonePolicies(
    PolicyControlled annotation,
    PolicyRestriction restriction
  ) {
    if (annotation.ownerClass() == null || annotation.ownerClass() == Object.class) {
      // At root, we ALWAYS have standalone policies
      return true;
    }

    return switch (restriction) {
      case READ -> annotation.hasStandaloneReadPolicies();
      case CREATE -> annotation.hasStandaloneCreatePolicies();
      case UPDATE -> annotation.hasStandaloneUpdatePolicies();
      case DELETE -> annotation.hasStandaloneDeletePolicies();
      case APPLY_POLICIES -> annotation.hasStandaloneApplyPolicies();
      // Default for PolicyRestriction.NONE or other types
      default -> false;
    };
  }

  /**
   * Map a child-level {@link PolicyRestriction} to the corresponding owner-level restriction
   * as declared on the provided {@link PolicyControlled} annotation.
   *
   * @param annotation  the {@code PolicyControlled} annotation instance to consult
   * @param restriction the child-level restriction
   * @return the {@link PolicyRestriction} that should be consulted on the owner level; {@link PolicyRestriction#NONE}
   *         when no mapping is provided for the supplied restriction
   */
  public static PolicyRestriction getOwnerRestrictionMapping(
    PolicyControlled annotation,
    PolicyRestriction restriction
  ) {

    return switch (restriction) {
      case READ -> annotation.readRestrictionMapping();
      case CREATE -> annotation.createRestrictionMapping();
      case UPDATE -> annotation.updateRestrictionMapping();
      case DELETE -> annotation.deleteRestrictionMapping();
      case APPLY_POLICIES -> annotation.applyPoliciesRestrictionMapping();
      default -> PolicyRestriction.NONE;
    };
  }

  /**
   * Build a {@link PolicyControlledMetadataRestrictionMap} from a {@link PolicyControlled} annotation.
   * The resulting map includes only entries where:
   * <ul>
   *   <li>the annotation declares standalone policies for the restriction, or</li>
   *   <li>the owner-level mapping differs from the child-level restriction.</li>
   * </ul>
   *
   * @param annotation the annotation from which to build the mapping
   * @return a populated {@link PolicyControlledMetadataRestrictionMap} describing non-trivial mappings for the level
   */
  public static PolicyControlledMetadataRestrictionMap buildRestrictionMapFromAnnotation(PolicyControlled annotation) {
    // Set up Restriction mapping for this level
    PolicyControlledMetadataRestrictionMap restrictionMap = new PolicyControlledMetadataRestrictionMap();

    relevantPolicies.forEach(pr -> {
      PolicyRestriction ownerRestriction = getOwnerRestrictionMapping(annotation, pr);
      boolean hasStandalonePolicies = hasStandalonePolicies(annotation, pr);
      if (hasStandalonePolicies || ownerRestriction != pr) {
        restrictionMap.put(
          pr,
          RestrictionMapEntry.builder()
            .ownerRestriction(ownerRestriction)
            .hasStandalonePolicies(hasStandalonePolicies)
            .build()
        );
      }
    });

    return restrictionMap;
  }


  /**
   * Walks the full ownership chain starting from a given leaf class and builds a list of
   * {@link PolicyControlledMetadata} objects. Each object in the list describes the policy control
   * relevant information for that level in the ownership hierarchy.
   * The chain is ordered from the {@code leafClass} (index 0) up to the ultimate root owner.
   * This method also calculates aliases for join operations in SQL/HQL for owner levels.
   *
   * @param leafClass The starting class (the most granular resource) from which to resolve the chain.
   * @return A {@link List} of {@link PolicyControlledMetadata} representing the ownership chain.
   * @throws IllegalArgumentException if any class in the chain is missing the {@code @PolicyControlled} annotation.
   * @throws IllegalStateException if a circular reference (cycle) is detected in the ownership chain.
   */
  public static List<PolicyControlledMetadata> resolveOwnershipChain(Class<?> leafClass) {
    List<PolicyControlledMetadata> chain = new ArrayList<>();
    Class<?> current = leafClass;
    Set<Class<?>> visited = new HashSet<>();

    int ownerLevel = 0;
    while (current != null && current != Object.class) {
      if (!visited.add(current)) {
        throw new IllegalStateException("Cycle detected in @PolicyControlled ownership chain for " + current.getName());
      }

      PolicyControlled annotation = PolicyControlledValidator.validateAndGet(current);

      // Work out ALIAS stuff
      String aliasName = null; // Nullable field
      String aliasOwnerColumn = null; // Nullable field
      String aliasOwnerField = null; // Nullable field

      // If we are an owner (not the leaf), we need to set up aliasing
      if (ownerLevel > 0) {
        PolicyControlledMetadata previous = chain.get(chain.size() - 1);
        aliasName = "owner_alias_" + ownerLevel;
        // Special case for FIRST owner, where we don't need the alias at all
        String aliasBase = (ownerLevel > 1 ? "owner_alias_" + (ownerLevel - 1) + "." : "");

        aliasOwnerColumn = aliasBase + previous.getOwnerColumn();
        aliasOwnerField = aliasBase + previous.getOwnerField();
      }

      // Build the restriction mapping for this chain level
      PolicyControlledMetadataRestrictionMap restrictionMap = buildRestrictionMapFromAnnotation(annotation);

      chain.add(PolicyControlledMetadata.builder()
        .resourceClassName(current.getCanonicalName())
        .resourceTableName(annotation.resourceTableName())
        .resourceIdColumn(annotation.resourceIdColumn())
        .resourceIdField(annotation.resourceIdField())
        .ownerColumn(annotation.ownerColumn())
        .ownerField(annotation.ownerField())
        .ownerClass(annotation.ownerClass())
        .ownerLevel(ownerLevel)
        .aliasName(aliasName)
        .aliasOwnerColumn(aliasOwnerColumn)
        .aliasOwnerField(aliasOwnerField)
        .restrictionMap(restrictionMap)
        .build()
      );

      current = annotation.ownerClass();
      ownerLevel += 1;
    }

    return chain;
  }

  public SkeletonRestrictionTree buildSkeletonRestrictionTree(
    PolicyRestriction restriction
  ) {
    // Set up a list of builders, we will walk backwards down this later to do the parent builds of the final tree
    List<SkeletonRestrictionTree.SkeletonRestrictionTreeBuilder> restrictionTreeBuilders = new ArrayList<>();
    PolicyRestriction levelRestriction = restriction; // We will change this at each level to follow the mapping tree


    for (int i = 0; i < getOwnershipChainSize(); i++) {
      // Set up the builder for the current level
      SkeletonRestrictionTree.SkeletonRestrictionTreeBuilder builder = SkeletonRestrictionTree.builder();
      builder.ownerLevel(i);

      PolicyControlledMetadata levelMetadata = getOwnerLevelMetadata(i);
      RestrictionMapEntry rme = levelMetadata.getRestrictionMap().get(levelRestriction);

      builder.restriction(levelRestriction);
      if (rme != null) {
        // We have a mapping for this restriction at this level
        builder.hasStandalonePolicies(rme.hasStandalonePolicies());

        levelRestriction = rme.getOwnerRestriction();
      }

      restrictionTreeBuilders.add(builder);
    }

    if (restrictionTreeBuilders.isEmpty()) {
      return null;
    }

    // Walk backwards down the builders list to get the tree at the base
    SkeletonRestrictionTree previousChild = null;
    for (int i = restrictionTreeBuilders.size() - 1; i >= 0; i--) {
      SkeletonRestrictionTree.SkeletonRestrictionTreeBuilder builder = restrictionTreeBuilders.get(i);

      // The previously constructed node (`currentChild`) becomes the parent of the node
      // currently being built (`builder`).

      // The "parent" of the current node being built is the previously fully built node (currentChild).
      builder.parent(previousChild);

      // Build the new, immutable node and update currentChild for the next iteration.
      previousChild = builder.build();
    }

    // return the last child we dealt with
    return previousChild;
  }

  public PolicyControlledRestrictionTreeMap buildRestrictionTreeMap() {
    PolicyControlledRestrictionTreeMap treeMap = new PolicyControlledRestrictionTreeMap();
    for (PolicyRestriction pol : relevantPolicies ) {
      treeMap.put(pol, buildSkeletonRestrictionTree(pol));
    }

    return treeMap;
  }


  /**
   * Retrieves the {@link PolicyControlledMetadata} for the leaf (most granular) class
   * in the ownership chain. This is always the first element in the {@link #ownershipChain}.
   * @return The {@link PolicyControlledMetadata} for the leaf class.
   */
  public PolicyControlledMetadata getLeafPolicyControlledMetadata() {
    return ownershipChain.get(0);
  }

  /**
   * Retrieves the {@link PolicyControlledMetadata} for the root (top-most owner) class
   * in the ownership chain. This is always the last element in the {@link #ownershipChain}.
   * @return The {@link PolicyControlledMetadata} for the root owner class.
   */
  public PolicyControlledMetadata getRootPolicyControlledMetadata() {
    return ownershipChain.get(ownershipChain.size() - 1);
  }

  /**
   * Returns a list of {@link PolicyControlledMetadata} for all entities in the ownership chain
   * that are *not* the leaf class. This list is useful for building joins for owner-related policies.
   * @return A {@link List} of {@link PolicyControlledMetadata} excluding the leaf class metadata.
   */
  public List<PolicyControlledMetadata> getNonLeafOwnershipChain() {
    return ownershipChain.stream().filter(pcm -> pcm.getOwnerLevel() > 0 ).toList();
  }

  /**
   * Retrieves the {@link PolicyControlledMetadata} for the specified owner level in the ownership chain.
   *
   * @param ownerLevel the zero-based index of the owner level to retrieve
   * @return the {@link PolicyControlledMetadata} at the specified owner level
   * @throws PolicyEngineException if the owner level is out of range
   */
  public PolicyControlledMetadata getOwnerLevelMetadata(int ownerLevel) {
    if (ownerLevel > ownershipChain.size() - 1 || ownerLevel < 0) {
      throw new PolicyEngineException("Cannot access PolicyControlledManager ownershipChain[${ownerLevel}]. Ownership chain has size: ${ownershipChain.size()}");
    }

    return ownershipChain.get(ownerLevel);
  }

  /**
   * Returns the number of levels in the ownership chain, including the leaf and all owners.
   *
   * @return the size of the ownership chain
   */
  public int getOwnershipChainSize() {
    return ownershipChain.size();
  }

  /**
   * Checks if the managed resource has any configured owners in its ownership chain.
   * This is determined by whether the {@link #ownershipChain} contains more than one entry
   * (the leaf class itself always counts as one entry).
   * @return {@code true} if the resource has owners, {@code false} otherwise.
   */
  public boolean hasOwners() {
    return ownershipChain.size() > 1;
  }

  /**
   * Checks if any restriction at the specified owner level declares standalone policies.
   *
   * @param ownerLevel the zero-based index of the owner level to check
   * @return {@code true} if any restriction at this level has standalone policies, otherwise {@code false}
   */
  public boolean hasStandalonePolicies(int ownerLevel) {
    return getOwnerLevelMetadata(ownerLevel)
      .getRestrictionMap()
      .entrySet()
      .stream()
      .anyMatch(entry -> entry.getValue().hasStandalonePolicies);
  }

  /**
   * Checks if any restriction at the leaf level (level 0) declares standalone policies.
   *
   * @return {@code true} if any restriction at the leaf level has standalone policies, otherwise {@code false}
   */
  public boolean hasStandalonePolicies() {
    return hasStandalonePolicies(0);
  }

  /**
   * Generates an {@link AccessControlSql} statement to retrieve the owner id at the specified level,
   * starting from the leaf id.
   *
   * @param ownerLevel the level in the ownership chain for which to return the id
   * @param leafId the identifier of the leaf resource
   * @return an {@link AccessControlSql} object representing the SQL to retrieve the owner id
   */
  public AccessControlSql getOwnerIdSql(
    String leafId, // The "bottom" identifier, applied to level $startLevel
    int ownerLevel // The level in the ownershipChain we want to return the id of
  ) {
    return getOwnerIdSql(leafId, ownerLevel, 0);
  }

  /**
   * Generates an {@link AccessControlSql} statement to retrieve the owner id at the specified level,
   * starting from a given start level and resource id.
   *
   * @param ownerLevel the level in the ownership chain for which to return the id
   * @param leafId the identifier of the resource at the start level
   * @param startLevel the level at which the given resourceId applies
   * @return an {@link AccessControlSql} object representing the SQL to retrieve the owner id
   * @throws IllegalArgumentException if {@code ownerLevel} is less than {@code startLevel}
   */
  public AccessControlSql getOwnerIdSql(
    String leafId, // The "bottom" identifier, applied to level $startLevel
    int ownerLevel, // The level in the ownershipChain we want to return the id of
    int startLevel // The level at which the given resourceId applies. For CREATE we will want to start at level 1 with id Y, instead of level 0 with id X, since we don't have id in hand for create
  ) {
    if (ownerLevel < startLevel) {
      throw new IllegalArgumentException("PolicyControlledManager::getOwnerIdSql ownerLevel: '" + ownerLevel + "' is less than configured start level: '" + startLevel + "'");
    }

    // If we start on the level we're attempting to find the id for, we have it in hand already
    if (ownerLevel == startLevel) {
      return AccessControlSql.builder()
        .sqlString("SELECT ? as id;")
        .types(new AccessControlSqlType[]{AccessControlSqlType.STRING})
        .parameters(new String[] { leafId })
        .build();
    }

    PolicyControlledMetadata ownerLevelMetadata = getOwnerLevelMetadata(ownerLevel);
    PolicyControlledMetadata leafLevelMetadata = getOwnerLevelMetadata(startLevel);

    // At this stage we have to build some SQL
    // We will JOIN tables from leaf -> ownerLevel as t0, t1 etc etc (if startLevel is 1 we actually start from t1 but the principle is the same)
    StringBuilder sqlString = new StringBuilder("SELECT t" + ownerLevel + "." + ownerLevelMetadata.resourceIdColumn + " as id");
    sqlString
      .append(" FROM ")
      .append(leafLevelMetadata.resourceTableName)
      .append(" as t")
      .append(startLevel); // Start from the startPoint entity

    // Build the JOINs up the chain
    for (int i = startLevel + 1; i <= ownerLevel; i++) {
      PolicyControlledMetadata previousMetadata = getOwnerLevelMetadata(i - 1);
      PolicyControlledMetadata currentMetadata = getOwnerLevelMetadata(i);

      if (previousMetadata.ownerColumn != null && !Objects.equals(previousMetadata.ownerColumn, "")) {
        sqlString.append(" JOIN ")
          .append(currentMetadata.resourceTableName)
          .append(" AS t")
          .append(i)
          .append(" ON t")
          .append(i - 1)
          .append(".")
          .append(previousMetadata.ownerColumn)
          .append(" = t")
          .append(i)
          .append(".")
          .append(currentMetadata.resourceIdColumn);
      }
    }

    sqlString
      .append(" WHERE t")
      .append(startLevel)
      .append(".")
      .append(leafLevelMetadata.resourceIdColumn)
      .append(" = ?;");

    return AccessControlSql.builder()
      .sqlString(sqlString.toString())
      .types(new AccessControlSqlType[]{AccessControlSqlType.STRING})
      .parameters(new String[] { leafId })
      .build();
  }

  /**
   * Resolves the class name of the owner at the specified level in the ownership chain.
   *
   * @param ownerLevel the zero-based index of the owner level to resolve
   * @return the class name of the owner at the specified level as a {@code String}
   * @throws PolicyEngineException if the owner level is out of range or if the required metadata is missing
   */
  public String resolveOwnerClass(int ownerLevel) {
    PolicyControlledMetadata ownerLevelMetadata = getOwnerLevelMetadata(ownerLevel);
    return ownerLevelMetadata.resourceClassName;
  }

  /**
   * Resolves the resourceIdColumn of the owner at the specified level in the ownership chain.
   *
   * @param ownerLevel the zero-based index of the owner level to resolve
   * @return the resource id column name of the owner at the specified level as a {@code String}
   * @throws PolicyEngineException if the owner level is out of range or if the required metadata is missing
   */
  public String resolveOwnerResourceIdColumn(int ownerLevel) {
    PolicyControlledMetadata ownerLevelMetadata = getOwnerLevelMetadata(ownerLevel);
    return ownerLevelMetadata.resourceIdColumn;
  }
}
