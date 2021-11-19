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

  /* Adding some indexes with sights set on performance */

  // Entitlement active to/from dates
  changeSet(author: "efreestone (manual)", id: "20211119-1037-001") {
    createIndex(indexName: "ent_active_to", tableName: "entitlement") {
      column(name: "ent_active_to")
      column(name: "ent_active_from")
    }

    createIndex(indexName: "ent_active_from", tableName: "entitlement") {
      column(name: "ent_active_from")
      column(name: "ent_active_to")
    }
  }

  // PCI access start/end dates
  changeSet(author: "efreestone (manual)", id: "20211119-1037-002") {
    createIndex(indexName: "pci_access_start", tableName: "package_content_item") {
      column(name: "pci_access_start")
      column(name: "pci_access_end")
    }

    createIndex(indexName: "pci_access_end", tableName: "package_content_item") {
      column(name: "pci_access_end")
      column(name: "pci_access_start")
    }
  }
}
