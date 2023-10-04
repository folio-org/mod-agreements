databaseChangeLog = {
  // Move work over to extend ErmTitleList
  changeSet(author: "efreestone (manual)", id: "20230830-1728-001") {
    // Copy all work ID/versions into title list table
    grailsChange {
      change {
        sql.execute("""
          INSERT INTO ${database.defaultSchemaName}.erm_title_list(id, version)
          SELECT w_id, w_version FROM ${database.defaultSchemaName}.work;
        """.toString())
      }
    }
    
    // We don't seem to drop the id columns for SubAgreement or ErmResource so haven't dropped here either
    dropColumn(columnName: "w_version", tableName: "work")
  }

  // Move IdentifierOccurrence foreign key constraint up to parent table
  // ErmResource and ErmTitleList should share IDs so no need to repopulate database(?)
  changeSet(author: "efreestone (manual)", id: "20230830-1728-002") {
    dropForeignKeyConstraint(
      baseTableName: "identifier_occurrence",
      constraintName: "identifier_occurrence_resource_FK",
    )

    addForeignKeyConstraint(
      baseColumnNames: "io_res_fk",
      baseTableName: "identifier_occurrence",
      constraintName: "identifier_occurrence_resource_FK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "id",
      referencedTableName: "erm_title_list"
    )
  }
}