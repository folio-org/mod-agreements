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

}
