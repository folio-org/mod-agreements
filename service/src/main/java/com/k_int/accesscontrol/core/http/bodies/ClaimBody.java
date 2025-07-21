package com.k_int.accesscontrol.core.http.bodies;

import com.k_int.accesscontrol.core.AccessPolicyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Represents the body of a claim request containing policy claims.
 * <p>
 * This class encapsulates the structure for a claim request, including a list of policy claims,
 * each with an associated policy ID, type, and optional description.
 * The resource id and resource class are not included in this body, as they might need to be obtained
 * from the {@link com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager}
 * </p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimBody {
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  protected static class PolicyClaim {
    @Nullable
    String id;

    @NotNull
    String policyId;
    @NotNull
    AccessPolicyType type;

    @Nullable
    String description;
  }

  @NotNull
  List<PolicyClaim> claims;
}
