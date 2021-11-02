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
}
