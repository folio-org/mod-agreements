databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "20220311-1009-001") {
    createTable(tableName: "custom_property_local_date") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value", type: "TIMESTAMP") {
        constraints(nullable: "false")
      }
    }
  }
  
  changeSet(author: "efreestone (manual)", id: "20220311-1009-002") {
    addPrimaryKey(columnNames: "id", constraintName: "custom_property_local_datePK", tableName: "custom_property_local_date")
  }

  changeSet(author: "efreestone (manual)", id: "20220311-1416-001") {
    addColumn(tableName: "custom_property_definition") {
      column(name: "pd_retired", type: "BOOLEAN")
    }
    addNotNullConstraint (tableName: "custom_property_definition", columnName: "pd_retired", defaultNullValue: 'FALSE')
  }

  changeSet(author: "efreestone (manual)", id: "20220311-1416-002") {
    createIndex(indexName: "td_retired_idx", tableName: "custom_property_definition") {
      column(name: "pd_retired")
    }
  }
}
