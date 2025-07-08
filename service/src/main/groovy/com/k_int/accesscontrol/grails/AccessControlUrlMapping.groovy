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
      }
    }
  }
}
