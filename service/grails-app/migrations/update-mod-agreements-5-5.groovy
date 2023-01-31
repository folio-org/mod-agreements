databaseChangeLog = {
  changeSet(author: "claudia (manual)", id: "20230131-1040-001") {

    createTable(tableName: "subscription_agreement_content_type") {
      column(name: "sact_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "sact_version", type: "BIGINT")

      column(name: "sact_owner_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "sact_content_type_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      addForeignKeyConstraint(baseColumnNames: "sact_owner_fk", baseTableName: "subscription_agreement_content_type", constraintName: "sact_to_sa_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "sa_id", referencedTableName: "subscription_agreement")
    }
  }
}
