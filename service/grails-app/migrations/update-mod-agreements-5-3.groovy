databaseChangeLog = {

  changeSet(author: "sosguthorpe", id: "202207-0001-001") {
    addColumn(tableName: "persistent_job") {
      column(name: "job_runner_id", type: "VARCHAR(36)")
    }
    
    createIndex(indexName: "job_runner_idx", tableName: "persistent_job") {
      column(name: "job_runner_id")
    }
  }

  changeSet(author: "efreestone (manual)", id: "20220824-1030-001") {
    createTable(tableName: "availability_constraint") {
      column(name: "avc_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "avc_version", type: "BIGINT") 

      column(name: "avc_owner_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "avc_body_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "efreestone (manual)", id: "20220824-1030-002") {
    addForeignKeyConstraint(
      baseColumnNames: "avc_owner_fk",
      baseTableName: "availability_constraint",
      constraintName: "avc_to_pkg_fk",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "id",
      referencedTableName: "package"
    )
  }

  changeSet(author: "efreestone (manual)", id: "20220824-1030-003") {
    addForeignKeyConstraint(
      baseColumnNames: "avc_body_fk",
      baseTableName: "availability_constraint",
      constraintName: "avc_to_rdv_fk",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "rdv_id",
      referencedTableName: "refdata_value"
    )
  }
}
