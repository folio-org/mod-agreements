databaseChangeLog = {
  changeSet(author: "mchaib (manual)", id: "20250820-1532-001") {
    addColumn(tableName: "entitlement") {
      column (name: "ent_resource_name", type: "VARCHAR(255)")
    }
  }

  changeSet(author: "mchaib (manual)", id: "20250915-1458-001") {
    createTable(tableName: "gokb_resource_entitlement_job") {
      column(name: "id", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
      column(name: "package_id", type: "VARCHAR(36)")
    }
  }
}