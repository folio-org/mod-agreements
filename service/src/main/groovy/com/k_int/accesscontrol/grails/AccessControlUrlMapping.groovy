package com.k_int.accesscontrol.grails

/**
 * AccessControlUrlMapping is a utility class that builds URL mappings for access control operations.
 * It defines routes for checking permissions on creating, reading, updating, and deleting resources.
 */
class AccessControlUrlMapping {

  /**
   * Builds URL mappings for access control operations based on the provided list of mappings.
   * Each mapping contains a path and a controller name.
   *
   * @param mappings A list of maps, each containing 'path' and 'controller' keys.
   * @return A Closure that defines the URL mappings.
   */
  static Closure buildRoutes(List<Map<String, String>> mappings) {
    return {
      mappings.each { mapping ->
        String path = mapping.path
        String controller = mapping.controller

        "${path}/canCreate"(controller: controller, action: "canCreate", method: 'GET')

        "${path}/$id/canRead"(controller: controller, action: "canRead", method: 'GET')
        "${path}/$id/canUpdate"(controller: controller, action: "canUpdate", method: 'GET')
        "${path}/$id/canDelete"(controller: controller, action: "canDelete", method: 'GET')
        "${path}/$id/canApplyPolicies"(controller: controller, action: "canApplyPolicies", method: 'GET')

        // FIXME we should probably also include a "${path}/$id/policies" to return the policies for a given resource, with the same shape as claimPolicies etc?
      }

      // Should these be in this grails urlMappings?
      // FIXME should we remove the ability to directly CRUD accessPolicies, and instead manage them through the resource controllers?
      "/erm/accessControl"(resources: 'accessPolicy') {
        collection {
          "/readPolicies"(controller: 'accessPolicy', action: 'getReadPolicyIds', method: 'GET')
          "/deletePolicies"(controller: 'accessPolicy', action: 'getDeletePolicyIds', method: 'GET')
          "/updatePolicies"(controller: 'accessPolicy', action: 'getUpdatePolicyIds', method: 'GET')
          "/createPolicies"(controller: 'accessPolicy', action: 'getCreatePolicyIds', method: 'GET')
          "/claimPolicies"(controller: 'accessPolicy', action: 'getClaimPolicyIds', method: 'GET')
          "/applyPolicies"(controller: 'accessPolicy', action: 'getApplyPolicyIds', method: 'GET')
        }
      }
    }
  }
}
