databaseChangeLog = {
  // EHoldingsEntitlementSyncJob -- ERM-4009
  changeSet(author: "snosko (manual)", id: "20260610-1200-001") {
    createTable(tableName: "eholdings_entitlement_sync_job") {
      column(name: "id", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
    }

    addPrimaryKey(
      columnNames: "id",
      constraintName: "eholdings_entitlement_sync_jobPK",
      tableName: "eholdings_entitlement_sync_job"
    )
  }
}