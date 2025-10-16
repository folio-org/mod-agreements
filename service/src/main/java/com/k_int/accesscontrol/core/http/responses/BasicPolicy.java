package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.IExternalPolicy;
import lombok.Builder;
import lombok.Getter;

/**
 * A basic implementation of the {@link IExternalPolicy} interface.
 * <p>
 * This class allows policies to be represented using only their identifier.
 * It is useful when the full policy object is not needed, such as in lightweight references
 * or lookup scenarios.
 * </p>
 *
 * @see IExternalPolicy
 */
@Getter
@Builder
@SuppressWarnings("javadoc")
public class BasicPolicy implements IExternalPolicy {

  /**
   * The identifier of the policy.
   * <p>
   * This value must be present and uniquely identifies the policy.
   * For acquisition unit-based policies, this is typically a UUID.
   * For grant-based policies (e.g. KIGrant), it may be a wildcard string like {@code "GBV%"}.
   * </p>
   *
   * @param id The policy id string
   * @return The policy ID string.
   */
  String id;
}
