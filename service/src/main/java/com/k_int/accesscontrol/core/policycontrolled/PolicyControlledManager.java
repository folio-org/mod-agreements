package com.k_int.accesscontrol.core.policycontrolled;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PolicyControlledManager {
  @Getter
  private final List<PolicyControlledMetadata> ownershipChain;

  public PolicyControlledManager(Class<?> leafClass) {
    this.ownershipChain = PolicyControlledManager.resolveOwnershipChain(leafClass);
  }

  // Walk the full ownership chain and build the PCM ownership chain
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
        .build()
      );

      current = annotation.ownerClass();
      ownerLevel += 1;
    }

    return chain;
  }

  public PolicyControlledMetadata getLeafPolicyControlledMetadata() {
    return ownershipChain.get(0);
  }

  public PolicyControlledMetadata getRootPolicyControlledMetadata() {
    return ownershipChain.get(ownershipChain.size() - 1);
  }

  public List<PolicyControlledMetadata> getNonLeafOwnershipChain() {
    return ownershipChain.stream().filter(pcm -> pcm.getOwnerLevel() > -1 ).toList();
  }

  public boolean hasOwners() {
    return ownershipChain.size() > 1;
  }
}
