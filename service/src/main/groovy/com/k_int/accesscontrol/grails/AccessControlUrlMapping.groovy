package com.k_int.accesscontrol.grails

class AccessControlUrlMapping {
  static Closure buildRoutes(List<Map<String, String>> mappings) {
    return {
      mappings.each { mapping ->
        String path = mapping.path
        String controller = mapping.controller

        "${path}/$id/canRead"(controller: controller, action: "canRead", method: 'GET')
        "${path}/$id/canUpdate"(controller: controller, action: "canUpdate", method: 'GET')
        "${path}/$id/canDelete"(controller: controller, action: "canDelete", method: 'GET')

        // FIXME this won't end up in the final work, just here as a test to compare/contract with normal read.
        "${path}/testReadRestrictedList" (controller: controller, action: "readRestrictedList", method: 'GET')
      }
    }
  }
}
