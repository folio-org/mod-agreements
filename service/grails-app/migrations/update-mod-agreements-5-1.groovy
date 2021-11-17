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

  changeSet(author: "ianibbo (manual)", id: "202109161336-001") {
    dropNotNullConstraint(columnName: "file_contents", tableName: "file_object")


    addColumn (tableName: "file_object" ) {
      column(name: "class", type: "VARCHAR(255)")
      column(name: "fo_s3ref", type: "VARCHAR(255)")
    }

    grailsChange {
      change {
        sql.execute("UPDATE ${database.defaultSchemaName}.file_object SET class = 'LOB' where class is null".toString());
      }
    }
  }
}
