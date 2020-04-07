databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "202003231555-1") {
    modifyDataType(
        tableName: "custom_property_definition",
        columnName: "pd_description", type: "text",
        newDataType: "text",
        confirm: "Successfully updated the pd_description column."
        )
  }
  changeSet(author: "claudia (manual)", id: "202004061829-01") {
    createTable(tableName: "subscription_agreement_alternate_names") {
      column(name: "subscription_agreement_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "alternate_names_string", type: "VARCHAR(255)")
    }
  }

  changeSet(author: "claudia (manual)", id: "202004061829-02") {
    addForeignKeyConstraint(baseColumnNames: "subscription_agreement_id", baseTableName: "subscription_agreement_alternate_names", constraintName: "FKbwixs452hfe48k069eip5xgx0", referencedColumnNames: "sa_id", referencedTableName: "subscription_agreement")
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-1") {
    createTable(tableName: "embargo") {
      column(name: "emb_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "emb_end_fk", type: "VARCHAR(36)")

      column(name: "emb_start_fk", type: "VARCHAR(36)")
    }
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-2") {
    createTable(tableName: "embargo_statement") {
      column(name: "est_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "est_type", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }

      column(name: "est_unit", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }

      column(name: "est_length", type: "INT") {
        constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-3") {
    addColumn(tableName: "package_content_item") {
      column(name: "pci_embargo_fk", type: "varchar(36)") {
        constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-4") {
    addPrimaryKey(columnNames: "emb_id", constraintName: "embargoPK", tableName: "embargo")
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-5") {
    addPrimaryKey(columnNames: "est_id", constraintName: "embargo_statementPK", tableName: "embargo_statement")
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-6") {
    addForeignKeyConstraint(baseColumnNames: "emb_start_fk", baseTableName: "embargo", constraintName: "FKaqsox5q361gjhl1dx9ulb8ra5", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "est_id", referencedTableName: "embargo_statement")
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-7") {
    addForeignKeyConstraint(baseColumnNames: "emb_end_fk", baseTableName: "embargo", constraintName: "FKd8ml5pj554n8b90km5sa0k07m", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "est_id", referencedTableName: "embargo_statement")
  }

  changeSet(author: "sosguthorpe (generated)", id: "1586289817497-8") {
    addForeignKeyConstraint(baseColumnNames: "pci_embargo_fk", baseTableName: "package_content_item", constraintName: "FKm8g6i6blt58ctbfcf8p6faidu", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "emb_id", referencedTableName: "embargo")
  }
}
