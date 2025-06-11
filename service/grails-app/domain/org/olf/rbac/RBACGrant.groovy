package org.olf.rbac

import grails.gorm.MultiTenant

public class RBACGrant implements MultiTenant<RBACGrant> {
  String id

  /* ------- GRANT ON WHAT ----- */
  // This object grants access to resources matching the below
  /*
    | party (resource.owner) | resourceType          | resourceId || explanation                                                 |
    | ---------------------- | --------------------- | ---------- || ----------------------------------------------------------- |
    | %                      | %                     | 1234-5678  || Matches any resource with the id "1234-5678"                |
    | Org/Dept               | %                     | %          || Matches any resource owned specifically by Org/Dept         |
    | Org/Dept%              | %                     | %          || Matches any resource owned Org/Dept or children             |
    | Org/Dept/%             | %                     | %          || Matches any resource owned by children of Org/Dept only     |
    | %                      | %                     | %          || Matches every resource in the system                        |
    | %                      | SubscriptionAgreement | %          || Matches all SubscriptionAgreements in the system            |
    | Org%                   | SubscriptionAgreement | %          || Matches all SubscriptionAgreements owned by Org or children |
   */

  String resourceType // A class string or %
  // FIXME could this potentially handle packages? org.olf.% for example
  String resourceId // Either an individual id or %
  String resourceOwner // A structured party string, as per Affiliation AND resource owner fields.

  /* ------- GRANT TO WHOM ----- */
  // This object grants access to users matching the below
  // FIXME see Affiliation, there's a strange crossover between resource owner and role-in-party
  // We might want to grant special access to SubscriptionAgreements in /Org/Dept1 to people who are admins in Org/Dept2?
  // We could give them both "Admin" role at Org level, but if they're only allowed to access each other's Agreements (and not, say Org/Dept3's)
  // then we potentially have an issue. granteeContext allows us to do this, but does seem to overcomplicate matters :/

  // That would look like
  // granteeType: ROLE, granteeId: ADMIN, granteeContext: Org/Dept2 pointing at
  // resource.owner Org/Dept1, resourceType: SubscriptionAgreement
  /*
    | granteeType | granteeId | granteeContext || explanation                      |
    | ----------- | --------- | -------------- || -------------------------------- |
    | %           | %         | %              ||  Matches all users in the system |
    | ROLE        | %         | %              ||  ??                              |

   */
  GranteeType granteeType // An enum for role vs group vs user etc
  String granteeId // Depends on "granteeType" above.
  // For USER this will be a UUID
  // For ROLE this would be one of AffiliationRole
  // For GROUP this will be // FIXME What will this be??? -- is this where acquisition groups come in or no?

  // FIXME do we need this???
  String granteeContext // This is ALSO a structured party patten


  // TODO The actual "permission" parts go here.
  // PermissionType = GROUP vs SINGLE?
  // Permission = GroupName vs Single Perm type (READ vs UPDATE vs CREATE vs DELETE)???
  // But maybe we need the perms to be hierarchical as well... ie if you can UPDATE then you can READ.
  // Or maybe we leave this bit out now, and leave that to the existing perms?

  // Are we _replacing_ FOLIO perms or appending to them?


  static mapping = {
    table           'rbac_grant'
    id              column: 'rgra_id', generator: 'uuid2', length:36
    version         column: 'rgra_version'
    resourceType    column: 'rgra_resource_type'
    resourceId      column: 'rgra_resource_id'
    resourceOwner   column: 'rgra_resource_owner'

    granteeType     column: 'rgra_grantee_type'
    granteeId       column: 'rgra_grantee_id'
    granteeContext  column: 'rgra_grantee_context'

  }

  static constraints = {
  }
}
