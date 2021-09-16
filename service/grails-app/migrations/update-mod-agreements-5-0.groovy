databaseChangeLog = {
  // add dateCreated and lastUpdated timestamp to SubscriptionAgreement and Entitlement
  changeSet(author: "claudia (manual)", id: "202107201437-001") {
    addColumn (tableName: "subscription_agreement" ) {
      column(name: "sa_date_created", type: "timestamp")
      column(name: "sa_last_updated", type: "timestamp")
    }   
  }

  changeSet(author: "claudia (manual)", id: "202107201437-002") {
    addColumn (tableName: "entitlement" ) {
      column(name: "ent_date_created", type: "timestamp")
      column(name: "ent_last_updated", type: "timestamp")
    }
  }

  changeSet(author: "efreestone (manual)", id: "20210818-1423-001") {
    createTable(tableName: "title_ingest_job") {
      column(name: "id", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "efreestone (manual)", id: "20210818-1423-002") {
    addPrimaryKey(columnNames: "id", constraintName: "title_ingest_jobPK", tableName: "title_ingest_job")
  }

}
