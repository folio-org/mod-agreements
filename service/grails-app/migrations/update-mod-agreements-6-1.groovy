databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "20240219-1445-001") {
    dropTable(tableName: "match_key")
  }

	changeSet(author: "efreestone (manual)", id: "20240219-1445-002") {
    dropTable(tableName: "naive_match_key_assignment_job")
  }

  changeSet(author: "efreestone (manual)", id: "20240221-1120-001") {
    modifyDataType(
      tableName: "platform_title_instance",
      columnName: "pti_url",
      newDataType: "VARCHAR(2083)",
      confirm: "Successfully updated the pti_url column."
    )
  }
}