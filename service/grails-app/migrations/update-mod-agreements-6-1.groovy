databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "20240219-1445-001") {
    dropTable(tableName: "match_key")
  }

	changeSet(author: "efreestone (manual)", id: "20240219-1445-002") {
    dropTable(tableName: "naive_match_key_assignment_job")
  }
}