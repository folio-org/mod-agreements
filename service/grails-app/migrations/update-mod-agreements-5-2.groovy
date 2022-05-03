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
  
  changeSet(author: "pboehm (manual)", id: "20220321-1545-002") {
    createTable(tableName: "alternate_resource_name") {
      column(name: "arn_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "arn_version", type: "BIGINT") 

      column(name: "arn_owner_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "arn_name", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
      addForeignKeyConstraint(baseColumnNames: "arn_owner_fk", baseTableName: "alternate_resource_name", constraintName: "arn_to_pkg_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pkg_id", referencedTableName: "package")
    }
    
    createTable(tableName: "content_type") {
      column(name: "ct_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "ct_version", type: "BIGINT") 

      column(name: "ct_owner_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "ct_content_type_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      addForeignKeyConstraint(baseColumnNames: "ct_owner_fk", baseTableName: "content_type", constraintName: "ct_to_pkg_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pkg_id", referencedTableName: "package")
    }
    
    createTable(tableName: "package_description_url") {
      column(name: "pdu_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "pdu_version", type: "BIGINT") 

      column(name: "pdu_owner_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "pdu_url", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
      addForeignKeyConstraint(baseColumnNames: "pdu_owner_fk", baseTableName: "package_description_url", constraintName: "ct_to_pkg_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pkg_id", referencedTableName: "package")
    }
    
    
    addColumn(tableName: "package") {
      column(name: "pkg_source_data_created", type: "TIMESTAMP")
      column(name: "pkg_source_data_updated", type: "TIMESTAMP")
      column(name: "pkg_lifecycle_status_fk", type: "VARCHAR(36)")
      column(name: "pkg_availability_scope_fk", type: "VARCHAR(36)")
    }
  }

  // Create io_res_fk field on identifierOccurrence
  changeSet(author: "efreestone (manual)", id: "20220420-1631-001") {
    addColumn(tableName: "identifier_occurrence") {
      column(name: "io_res_fk", type: "VARCHAR(36)")
    }
    addForeignKeyConstraint(
      baseColumnNames: "io_res_fk",
      baseTableName: "identifier_occurrence",
      constraintName: "identifier_occurrence_resource_FK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "id",
      referencedTableName: "erm_resource"
    )
  }

  // Update IdentifierOccurrences to move TI foreign key to res foreign key
  changeSet(author: "efreestone (manual)", id: "20220420-1631-002") {
    grailsChange {
      change {
        // First pre-populate all the referenced ids. 
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.identifier_occurrence
          SET io_res_fk = io_ti_fk
        """.toString())
      }
    }
  }

  // Update IdentifierOccurrences to move TI foreign key to res foreign key
  changeSet(author: "efreestone (manual)", id: "20220420-1631-003") {
    dropColumn(columnName: "io_ti_fk", tableName: "identifier_occurrence")
  }
}
