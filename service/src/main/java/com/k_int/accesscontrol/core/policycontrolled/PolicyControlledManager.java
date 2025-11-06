package com.k_int.accesscontrol.core.policycontrolled;

import com.k_int.accesscontrol.core.PolicyRestriction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages and resolves the ownership chain for entities marked with {@link PolicyControlled} annotations.
 * This class is responsible for building a structured representation of how resources relate to their owners
 * for the purpose of access control policy evaluation.
 */
public class PolicyControlledManager {
  // FIXME IDEA
  // At the moment, it is assumed that if a PCM is for a class that has owners, the owner's policies are DIRECTLY responsible for the child class
  // What if we need the child class to have its own CREATE policies, or for child class DELETE to rely on parent's EDIT instead?
  // What do we do in the case of no owner?
  // What if we want the class to obey policies on its level AND on the parent's level?


  /**
   * The resolved ownership chain, ordered from the leaf class up to the root owner.
   * Each element in the list represents the {@link PolicyControlledMetadata} for a level in the chain.
   * @return A list of PolicyControlledMetadata representing the ordered ownership chain
   */
  @Getter
  @SuppressWarnings("javadoc")
  private final List<PolicyControlledMetadata> ownershipChain;

  /**
   * Constructs a {@code PolicyControlledManager} by resolving the complete ownership chain
   * starting from the specified leaf class.
   * @param leafClass The {@link Class} object representing the leaf entity for which the ownership chain is to be resolved.
   * @throws IllegalArgumentException if the {@code @PolicyControlled} annotation is missing on any class in the chain.
   * @throws IllegalStateException if a cycle is detected in the ownership chain.
   * @see #resolveOwnershipChain(Class)
   */
  public PolicyControlledManager(Class<?> leafClass) {
    this.ownershipChain = PolicyControlledManager.resolveOwnershipChain(leafClass);
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
    PolicyRestriction restriction) {

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
    PolicyRestriction restriction) {

    return switch (restriction) {
      case READ -> annotation.readRestrictionMapping();
      case CREATE -> annotation.createRestrictionMapping();
      case UPDATE -> annotation.updateRestrictionMapping();
      case DELETE -> annotation.deleteRestrictionMapping();
      case APPLY_POLICIES -> annotation.applyPoliciesRestrictionMapping();
      // Default or error handling as appropriate
      default -> PolicyRestriction.NONE;
    };
  }

  /**
   * Build a {@link PolicyControlledRestrictionMap} from a {@link PolicyControlled} annotation.
   * <p>
   * The resulting map includes only entries where:
   * <ul>
   *   <li>the annotation declares standalone policies for the restriction, or</li>
   *   <li>the owner-level mapping differs from the child-level restriction.</li>
   * </ul>
   * </p>
   *
   * @param annotation the annotation from which to build the mapping
   * @return a populated {@link PolicyControlledRestrictionMap} describing non-trivial mappings for the level
   */
  public static PolicyControlledRestrictionMap buildRestrictionMapFromAnnotation(PolicyControlled annotation) {
    // Set up Restriction mapping for this level
    PolicyControlledRestrictionMap restrictionMap = new PolicyControlledRestrictionMap();

    List<PolicyRestriction> relevantPolicies = List.of(
      PolicyRestriction.READ,
      PolicyRestriction.CREATE,
      PolicyRestriction.UPDATE,
      PolicyRestriction.DELETE,
      PolicyRestriction.APPLY_POLICIES
    );

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

    int ownerLevel = -1;
    while (current != null && current != Object.class) {
      if (!visited.add(current)) {
        throw new IllegalStateException("Cycle detected in @PolicyControlled ownership chain for " + current.getName());
      }

      PolicyControlled annotation = current.getAnnotation(PolicyControlled.class);
      if (annotation == null) {
        throw new IllegalArgumentException("Missing @PolicyControlled on " + current.getName());
      }

      // Work out ALIAS stuff
      String aliasName = null; // Nullable field
      String aliasOwnerColumn = null; // Nullable field
      String aliasOwnerField = null; // Nullable field

      if (ownerLevel > -1) {
        PolicyControlledMetadata previous = chain.get(chain.size() - 1);
        aliasName = "owner_alias_" + ownerLevel;
        // Special case for FIRST owner, where we don't need the alias at all
        String aliasBase = (ownerLevel > 0 ? "owner_alias_" + (ownerLevel - 1) + "." : "");

        aliasOwnerColumn = aliasBase + previous.getOwnerColumn();
        aliasOwnerField = aliasBase + previous.getOwnerField();
      }

      // Build the restriction mapping for this chain level
      PolicyControlledRestrictionMap restrictionMap = buildRestrictionMapFromAnnotation(annotation);

      chain.add(PolicyControlledMetadata.builder()
        .resourceClassName(current.getCanonicalName())
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
    return ownershipChain.stream().filter(pcm -> pcm.getOwnerLevel() > -1 ).toList();
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
}
