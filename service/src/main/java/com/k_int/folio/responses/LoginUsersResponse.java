package com.k_int.folio.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** A FOLIO login response.
 * <p>
 * This class represents the structure of a login response from the bl-users endpoint in a FOLIO system,
 * including user details, patron group information, permissions, service points,
 * and token expiration times.
 * </p>
 * <p>
 *   This class attempts to map the JSON response from FOLIO's login endpoint to
 *   the data we might be interested in using from a login response.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginUsersResponse {
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public class User {
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Personal {
      String firstName;
      String lastName;
      String email;
      // Also includes addresses and
      // other informamtion not valuable to us for now
    }
    String id;
    String username;
    String externalSystemId;
    String barcode;
    boolean active;
    String patronGroup;

    Personal personal;
    Instant createdDate;
    Instant updatedDate;

    // We also have metadata, custom fields, preferred email communication and departments
  }
  User user;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public class PatronGroup {
    String id;
    String group;
    String desc;
    // We also have metadata
  }
  PatronGroup patronGroup;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public class Permissions {
    String id;
    String userId;
    List<String> permissions;
    // We also have metadata
  }
  Permissions permissions;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public class ServicePointsUser {
    String id;
    String userId;
    List<String> servicePointsIds;
    // Service points -- not sure what shape these take
    String defaultServicePointId;
    // We also have metadata
  }
  ServicePointsUser servicePointsUser;

  TokenExpirationResponse tokenExpiration;

  String tenant;
}
