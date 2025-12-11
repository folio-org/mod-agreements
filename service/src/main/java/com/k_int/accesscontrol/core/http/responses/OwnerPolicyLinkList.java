package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.http.bodies.PolicyLink;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;


/**
 * Represents the collection of policy links that apply to a resource at a
 * specific ownership level.
 *
 * <p>This DTO is used in API responses to expose the resolved access-control
 * policies associated with a resource and its ownership hierarchy. Each instance
 * corresponds to a single ownership depth (e.g., the resource itself, its parent,
 * its grandparent, etc.) and contains the set of {@link PolicyLink} entries
 * relevant at that level.</p>
 */
@Builder
@Data
@SuppressWarnings("javadoc")
public class OwnerPolicyLinkList {
  /**
   * The hierarchical depth at which the policies apply.
   * <p>Level {@code 0} represents the resource itself; higher numbers indicate
   * parent or ancestor ownership layers.</p>
   *
   * @param ownerLevel the ownership level
   * @return the ownership level
   */
  int ownerLevel;

  /**
   * The fully-qualified class name or type identifier of the resource for which
   * policies are being reported.
   *
   * @param resourceClass the resource class name
   * @return the resource class name
   */
  String resourceClass;

  /**
   * The identifier of the owner entity at this level in the ownership chain.
   *
   * @param ownerId the owner ID
   * @return the owner ID
   */
  String ownerId;

  /**
   * Flag indicating whether the owner at this level has any standalone policies
   * defined directly on it, independent of inherited or aggregated policies.
   * Defaults to {@code false}.
   *
   * @param hasStandalonePolicies whether this level of ownership has standalone policies on it
   * @return {@code true} if there are standalone policies; {@code false} otherwise
   */
  @Builder.Default
  @Accessors(fluent = true)
  boolean hasStandalonePolicies = false;

  /**
   * List of {@link PolicyLink} objects representing the individual policy
   * associations for this ownership level.
   *
   * @param policies the list of policies
   * @return the list of policies
   */
  List<PolicyLink> policies;
}
