databaseChangeLog = {
  // Change contentUpdated column over to type "TIMESTAMP"
  changeSet(author: "efreestone (manual)", id: "20211102-0939-001") {

    modifyDataType(
      tableName: "entitlement",
      columnName: "ent_content_updated",
      newDataType: "timestamp",
      confirm: "Successfully updated the ent_content_updated column."
    )
  }

  changeSet(author: "ianibbo (manual)", id: "202109161336-002") {
    dropNotNullConstraint(columnName: "file_contents", tableName: "file_object")

    addColumn (tableName: "file_object" ) {
      column(name: "class", type: "VARCHAR(255)")
      column(name: "fo_s3ref", type: "VARCHAR(255)")
    }
  }

  changeSet(author: "ianibbo (manual)", id: "202109161336-003") {
    grailsChange {
      change {
        sql.execute("UPDATE ${database.defaultSchemaName}.file_object SET class = 'DB' where class is null".toString());
      }
    }
  }

  changeSet(author: "efreestone (manual)", id: "20220118-1432-001") {
    createTable(tableName: "match_key") {
      column(name: "mk_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "mk_version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "mk_key", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }

      column(name: "mk_value", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }

      column(name: "mk_resource_fk", type: "VARCHAR(36)")
    }
  }

  changeSet(author: "efreestone (manual)", id: "20220119-1108-001") {
    addForeignKeyConstraint(
      baseColumnNames: "mk_resource_fk",
      baseTableName: "match_key",
      constraintName: "match_key_erm_resourceFK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "id",
      referencedTableName: "erm_resource"
    )
  }
}
