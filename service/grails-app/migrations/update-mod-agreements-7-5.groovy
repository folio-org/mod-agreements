databaseChangeLog = {
  changeSet(author: "mchaib (manual)", id: "20250820-1532-001") {
    addColumn(tableName: "entitlement") {
      column (name: "ent_resource_name", type: "VARCHAR(255)")
    }
  }
}