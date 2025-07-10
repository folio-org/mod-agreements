databaseChangeLog = {
  changeSet(author: "mchaib (manual)", id: "20250710-1620-001") {
    addForeignKeyConstraint(baseColumnNames: "pkg_lifecycle_status_fk", baseTableName: "package", constraintName: "lifecycle_status_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
    addForeignKeyConstraint(baseColumnNames: "pkg_availability_scope_fk", baseTableName: "package", constraintName: "availability_scope_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
  }
}