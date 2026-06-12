databaseChangeLog = {
  // ExternalEntitlementEholdingsSyncJob -- ERM-4009
  changeSet(author: "snosko (manual)", id: "20260610-1200-001") {
    createTable(tableName: "external_entitlement_eholdings_sync_job") {
      column(name: "id", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
    }

    addPrimaryKey(
      columnNames: "id",
      constraintName: "external_entitlement_eholdings_sync_jobPK",
      tableName: "external_entitlement_eholdings_sync_job"
    )
  }
}