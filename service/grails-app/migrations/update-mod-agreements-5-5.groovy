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

  // Update refdataCategory descriptions for contentTypes (agreement and package)
  // Pkg: ContentType.ContentType -> Pkg.ContentType
  changeSet(author: "claudia (manual)", id:"20230201-1503-001") {
    grailsChange {
      change {
          sql.execute("UPDATE ${database.defaultSchemaName}.refdata_category SET rdc_description='Pkg.ContentType' WHERE rdc_description='ContentType.ContentType'".toString())
      }
    }
  }
  // Agreement: AgreementContentType -> SubscriptionAgreement.ContentType
  changeSet(author: "claudia (manual)", id:"20230201-1509-001") {
    grailsChange {
      change {
          sql.execute("UPDATE ${database.defaultSchemaName}.refdata_category SET rdc_description='SubscriptionAgreement.ContentType' WHERE rdc_description='AgreementContentType'".toString())
      }
    }
  }

  changeSet(author: "efreestone (manual)", id: "20230202-1013-001") {
    addForeignKeyConstraint(baseColumnNames: "sact_content_type_fk", baseTableName: "subscription_agreement_content_type", constraintName: "sact_content_type_fk_rdvFK", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
  }

  changeSet(author: "efreestone (manual)", id: "20230202-1013-002") {
    addForeignKeyConstraint(baseColumnNames: "ct_content_type_fk", baseTableName: "content_type", constraintName: "content_type_fk_rdvFK", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
  }

  changeSet(author: "efreestone (manual)", id: "20230313-1356-001") {
    grailsChange {
      change {
          sql.execute("UPDATE ${database.defaultSchemaName}.package SET pkg_remote_kb=NULL".toString())
      }
    }
  } 
  
  changeSet(author: "efreestone (manual)", id: "20230313-1356-002") {
    dropForeignKeyConstraint(baseTableName: "package", constraintName: "FKoedx99aeb9ll9v1p7w29htqtl")
  }
  
  changeSet(author: "efreestone (manual)", id: "20230313-1356-003") {
    dropColumn(columnName: "pkg_remote_kb", tableName: "package")
  }

  changeSet(author: "efreestone (manual)", id: "20230324-1048-001") {
    createTable(tableName: "push_kb_session") {
      column(name: "pkbs_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "pkbs_version", type: "BIGINT")

      column(name: "pkbs_session_id", type: "VARCHAR(75)")

      column(name: "pkbs_date_created", type: "timestamp")
      column(name: "pkbs_last_updated", type: "timestamp")
    }
  }

  changeSet(author: "efreestone (manual)", id: "20230324-1048-002") {
    createTable(tableName: "push_kb_chunk") {
      column(name: "pkbc_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "pkbc_version", type: "BIGINT")

      column(name: "pkbc_chunk_id", type: "VARCHAR(75)")

      column(name: "pkbc_date_created", type: "timestamp")
      column(name: "pkbc_last_updated", type: "timestamp")

      column(name: "pkbc_session_fk", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      addForeignKeyConstraint(
        baseColumnNames: "pkbc_session_fk",
        baseTableName: "push_kb_chunk",
        constraintName: "pkbc_to_pkbs_fk",
        deferrable: "false",
        initiallyDeferred: "false",
        referencedColumnNames: "pkbs_id",
        referencedTableName: "push_kb_session"
      )
    }
  }

}
