databaseChangeLog = {

  // unique indexes for refdata values - particularly to protect against unusual upgrade/clone practices by hosters
  changeSet(author: "ianibbo (manual)", id: "202106021259-001") {
    addUniqueConstraint(columnNames: "rdv_owner,rdv_value", constraintName: "RDV_UNIQUE_VALUE", tableName: "refdata_value")
  }

  changeSet(author: "Ian Ibbotson (manual)", id: "202107051301-001") {
    addColumn (tableName: "entitlement_log_entry" ) {
      column(name: "ele_event_type", type: "VARCHAR(255)")
    }
  }

}
