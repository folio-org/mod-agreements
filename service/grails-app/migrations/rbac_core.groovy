databaseChangeLog = {
  // ** FIXME ALL party strings are rn set to 255, and might need to be longer
  // Indexing those could become interesting
  changeSet(author: "Ethan Freestone", id: "2025-06-11-1544-001") {
    createTable(tableName: "rbac_affiliation") {
      column(name: "raff_id", type: "VARCHAR(36)")
      column(name: "raff_version", type: "BIGINT")
      column(name: "raff_user", type: "VARCHAR(36)")
      column(name: "raff_role", type: "VARCHAR(36)")
      column(name: "raff_party", type: "VARCHAR(255)") // See ** above
    }
  }

  changeSet(author: "Ethan Freestone", id: "2025-06-11-1544-002") {
    createTable(tableName: "rbac_grant") {
      column(name: "rgra_id", type: "VARCHAR(36)")
      column(name: "rgra_version", type: "BIGINT")
      column(name: "rgra_resource_type", type: "VARCHAR(36)")
      column(name: "rgra_resource_id", type: "VARCHAR(36)")
      column(name: "rgra_resource_owner", type: "VARCHAR(255)") // See ** above
      column(name: "rgra_grantee_type", type: "VARCHAR(36)")
      column(name: "rgra_grantee_id", type: "VARCHAR(36)")
      column(name: "rgra_grantee_context", type: "VARCHAR(255)") // See ** above
    }
  }
}
